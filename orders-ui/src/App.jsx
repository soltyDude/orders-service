import React, { useEffect, useMemo, useState } from "react";
// --- tiny helpers ---
const pretty = (v) => JSON.stringify(v, null, 2);
const uuid = () => (crypto?.randomUUID ? crypto.randomUUID() : Math.random().toString(36).slice(2));

// --- minimal, unobtrusive styling ---
const styles = `
:root{
  --bg:#0b0e14;        /* dark base (so logs pop) */
  --panel:#101521;     /* cards */
  --muted:#8a99b6;     /* secondary text */
  --text:#e7ecf7;      /* primary text */
  --line:#1a2233;      /* borders */
  --brand:#4f7cff;     /* accents */
  --ok:#10b981; --warn:#f59e0b; --err:#ef4444; --chip:#2a3350;
}
*{box-sizing:border-box}
html,body,#root,#app{height:100%}
body{margin:0;background:radial-gradient(1200px 500px at 70% -200px, #152035 0%, var(--bg) 60%);color:var(--text);font:14px/1.45 Inter,system-ui,Segoe UI,Arial}
.container{max-width:1100px;margin:0 auto;padding:20px}
.header{display:flex;align-items:center;gap:10px;margin-bottom:12px}
.h1{font-size:20px;font-weight:800;letter-spacing:.2px}
.env{background:var(--chip);border:1px solid var(--line);padding:4px 8px;border-radius:999px;color:#b6c3e1;font-weight:600}
.grid{display:grid;grid-template-columns:1.1fr 1fr;gap:14px}
.card{background:var(--panel);border:1px solid var(--line);border-radius:12px;padding:14px;box-shadow:0 1px 2px rgba(0,0,0,.25)}
.h2{font-size:13px;font-weight:800;color:#cdd7f0;margin:0 0 8px 0;letter-spacing:.3px;text-transform:uppercase}
.row{display:flex;gap:8px;align-items:center;margin:8px 0;flex-wrap:wrap}
.label{width:140px;color:var(--muted);font-weight:600}
.input{flex:1;min-width:240px;padding:8px 10px;border-radius:9px;background:#0c1220;border:1px solid var(--line);color:var(--text)}
.input.sm{width:140px}
.table{width:100%;border-collapse:collapse}
.th{font-size:12px;color:#9fb0d1;text-align:left;border-bottom:1px solid var(--line);padding:6px}
.td{padding:6px}
.td.center{text-align:center}
.btn{padding:8px 12px;border-radius:10px;border:1px solid var(--line);background:#0f172a;color:#dfe8ff;cursor:pointer}
.btn.primary{background:var(--brand);border-color:#305ef9;color:#fff}
.btn.ghost{background:transparent;color:#c8d7ff}
.btn:disabled{opacity:.55;cursor:not-allowed}
.badge{display:inline-block;padding:4px 8px;border-radius:999px;font-weight:700;}
.badge.ok{background:#0e2b22;color:var(--ok)}
.badge.warn{background:#2b2410;color:var(--warn)}
.badge.err{background:#2b1212;color:var(--err)}
.pre{background:#0a0f1c;color:#cfd7eb;padding:12px;border-radius:10px;font-size:12px;overflow:auto;max-height:260px;border:1px solid var(--line)}
.help{color:var(--muted);font-size:12px}
.hr{height:1px;background:var(--line);border:0;margin:10px 0}
.footer{margin-top:14px;color:#97a6c6;font-size:12px}
.errText{color:#ff9797;font-weight:600}
`;

export default function App(){
    const [baseUrl, setBaseUrl] = useState(localStorage.getItem("orders.baseUrl") || "http://localhost:8081");
    const [customerId, setCustomerId] = useState(localStorage.getItem("orders.customerId") || uuid());
    const [currency, setCurrency] = useState("USD");
    const [items, setItems] = useState([{ sku: "SKU-1", qty: 2, priceCents: 1500 }]);
    const [idemKey, setIdemKey] = useState(localStorage.getItem("orders.idemKey") || ("key-" + Math.random().toString(36).slice(2,8)));

    const [busy, setBusy] = useState(false);
    const [toast, setToast] = useState("");
    const [orderResp, setOrderResp] = useState(null);
    const [orderId, setOrderId] = useState("");
    const [orderView, setOrderView] = useState(null);
    const [errors, setErrors] = useState([]);

    useEffect(()=>{ localStorage.setItem("orders.baseUrl", baseUrl); },[baseUrl]);
    useEffect(()=>{ localStorage.setItem("orders.idemKey", idemKey); },[idemKey]);
    useEffect(()=>{ localStorage.setItem("orders.customerId", customerId); },[customerId]);

    const totalCents = useMemo(()=> items.reduce((a, it) => a + (n(it.qty)*n(it.priceCents)), 0), [items]);

    function n(v){ const x=Number(v); return Number.isFinite(x)?x:0; }
    function updateItem(i, key, val){ setItems(arr => arr.map((it,idx)=> idx===i? {...it,[key]:val}:it)); }
    function addItem(){ setItems(a => [...a, { sku:"", qty:1, priceCents:0 }]); }
    function removeItem(i){ setItems(a => a.filter((_,idx)=>idx!==i)); }

    function validate(){
        const errs = [];
        if(!/^https?:\/\//.test(baseUrl)) errs.push("Backend URL");
        if(!currency) errs.push("Currency");
        if(!idemKey) errs.push("Idempotency-Key");
        items.forEach((it, i)=>{
            if(!it.sku || n(it.qty)<=0 || n(it.priceCents)<0) errs.push(`Item#${i+1}`);
        });
        setErrors(errs);
        return errs.length===0;
    }

    async function createOrder(){
        if(!validate()) { pop("Проверь поля"); return; }
        setBusy(true); setOrderResp(null);
        try{
            const body = { customerId, currency, items: items.map(x=>({ sku:x.sku.trim(), qty:n(x.qty), priceCents:n(x.priceCents) })) };
            const r = await fetch(`${baseUrl}/orders`, { method:"POST", headers:{"Content-Type":"application/json","Idempotency-Key":idemKey}, body: JSON.stringify(body) });
            const text = await r.text();
            let data; try{ data = JSON.parse(text); } catch{ data = { raw:text }; }
            setOrderResp({ status:r.status, data });
            if(r.ok && data?.orderId){ setOrderId(data.orderId); pop("Заказ создан"); await getOrder(data.orderId); }
            else if(!r.ok) pop(`Ошибка ${r.status}`);
        }catch(e){ setOrderResp({ error:String(e) }); pop("Сеть недоступна"); }
        finally{ setBusy(false); }
    }

    async function getOrder(id){
        const target = id || orderId; if(!target) return;
        try{ const r = await fetch(`${baseUrl}/orders/${target}`); const data = await r.json(); setOrderView(data); }
        catch(e){ setOrderView({ error: String(e) }); }
    }

    async function simulatePayment(status){
        if(!orderId) return; setBusy(true);
        try{
            const r = await fetch(`${baseUrl}/_sim/payments`, { method:"POST", headers:{"Content-Type":"application/json"}, body: JSON.stringify({ orderId, status }) });
            if(!r.ok){ pop(`/_sim/payments → ${r.status}`); }
            await getOrder(orderId);
        } finally { setBusy(false); }
    }

    function pop(msg){ setToast(msg); setTimeout(()=>setToast(""), 1600); }

    const invalid = errors.length>0;

    return (
        <div className="container">
            <style>{styles}</style>

            <div className="header">
                <div className="h1">Orders Console</div>
                <span className="env">local</span>
            </div>

            <div className="grid">
                {/* left column */}
                <section className="card">
                    <h2 className="h2">Setup</h2>
                    <div className="row">
                        <label className="label">Backend URL</label>
                        <input className="input" value={baseUrl} onChange={(e)=>setBaseUrl(e.target.value)} />
                    </div>
                    <div className="row">
                        <label className="label">Idempotency‑Key</label>
                        <input className="input" value={idemKey} onChange={(e)=>setIdemKey(e.target.value)} />
                        <button className="btn" onClick={()=>setIdemKey("key-"+Math.random().toString(36).slice(2,10))}>New</button>
                    </div>

                    <div className="hr"/>

                    <h2 className="h2">Create Order</h2>
                    <div className="row">
                        <label className="label">Customer ID</label>
                        <input className="input" value={customerId} onChange={(e)=>setCustomerId(e.target.value)} />
                        <button className="btn" onClick={()=>setCustomerId(uuid())}>New UUID</button>
                    </div>
                    <div className="row">
                        <label className="label">Currency</label>
                        <input className="input sm" value={currency} onChange={(e)=>setCurrency(e.target.value.toUpperCase())} />
                    </div>

                    <table className="table">
                        <thead><tr>
                            <th className="th">SKU</th>
                            <th className="th">Qty</th>
                            <th className="th">Price (cents)</th>
                            <th className="th"></th>
                        </tr></thead>
                        <tbody>
                        {items.map((it,idx)=> (
                            <tr key={idx}>
                                <td className="td"><input className="input" value={it.sku} onChange={(e)=>updateItem(idx,"sku",e.target.value)} /></td>
                                <td className="td"><input className="input sm" type="number" value={it.qty} onChange={(e)=>updateItem(idx,"qty",e.target.value)} /></td>
                                <td className="td"><input className="input sm" type="number" value={it.priceCents} onChange={(e)=>updateItem(idx,"priceCents",e.target.value)} /></td>
                                <td className="td center"><button className="btn ghost" onClick={()=>removeItem(idx)}>✕</button></td>
                            </tr>
                        ))}
                        </tbody>
                    </table>
                    <div className="row" style={{justifyContent:"space-between"}}>
                        <button className="btn ghost" onClick={addItem}>+ Add item</button>
                        <div style={{fontWeight:800}}>Total: {(totalCents/100).toFixed(2)} {currency}</div>
                    </div>

                    {invalid && (
                        <div className="row"><span className="errText">Проверь поля: {errors.join(", ")}</span></div>
                    )}

                    <div className="row">
                        <button className="btn primary" onClick={createOrder} disabled={busy || invalid}>{busy?"Creating…":"Create Order"}</button>
                        <button className="btn" onClick={()=> orderId && getOrder(orderId)} disabled={!orderId || busy}>Refresh</button>
                        <button className="btn ghost" onClick={()=> setOrderResp(null)}>Clear</button>
                    </div>

                    {orderResp && (
                        <div style={{marginTop:8}}>
                            <div style={{fontWeight:700, marginBottom:6}}>POST /orders → HTTP {orderResp.status ?? ""}</div>
                            <pre className="pre">{pretty(orderResp.data ?? orderResp)}</pre>
                        </div>
                    )}
                </section>

                {/* right column */}
                <section className="card">
                    <h2 className="h2">Order</h2>
                    <div className="row">
                        <label className="label">Order ID</label>
                        <input className="input" value={orderId} onChange={(e)=>setOrderId(e.target.value)} />
                        <button className="btn" onClick={()=>getOrder()} disabled={!orderId}>Load</button>
                    </div>

                    {orderView && (
                        <div>
                            <div style={{marginBottom:6}}>
                                <StatusBadge status={orderView.status} />
                            </div>
                            <pre className="pre">{pretty(orderView)}</pre>
                            <div className="row">
                                <button className="btn" onClick={()=>simulatePayment("SUCCESS")} disabled={!orderId || busy}>Pay: SUCCESS</button>
                                <button className="btn" onClick={()=>simulatePayment("FAILED")} disabled={!orderId || busy}>Pay: FAILED</button>
                            </div>
                        </div>
                    )}

                    {!orderView && (
                        <div className="help">Создайте заказ или вставьте существующий <i>Order ID</i>, затем нажмите <b>Load</b>.</div>
                    )}
                </section>
            </div>

            {toast && (
                <div style={{position:"fixed",right:16,bottom:16,background:"#0f1a2d",border:"1px solid var(--line)",color:"#d9e4ff",padding:"10px 12px",borderRadius:12,boxShadow:"0 4px 14px rgba(0,0,0,.35)",fontWeight:700}}>
                    {toast}
                </div>
            )}

            <div className="footer">This is a local console UI for functional checks. No analytics, no trackers.</div>
        </div>
    );
}

function StatusBadge({ status }){
    let cls = "badge"; if(status==="CONFIRMED") cls += " ok"; else if(status==="PENDING") cls += " warn"; else if(status==="CANCELED") cls += " err"; else cls += " warn";
    return <span className={cls}>{status || "UNKNOWN"}</span>;
}
