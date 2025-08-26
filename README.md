# Orders Service (Ktor + PostgreSQL + Outbox + Kafka)

Небольшой демонстрационный сервис оформления заказов с акцентом на надежность вызовов и интеграций: идемпотентность на уровне API, транзакционный **Outbox** для публикации доменных событий в Kafka, имитация платежей, и простой фронтенд на Vite/React для наглядности.

## Что здесь важно

* **Ktor** как легковесный HTTP‑сервер (эндпоинты `/orders`, `/orders/{id}`, `/health`).
* **Идемпотентность** входящих POST‑запросов по заголовку `Idempotency-Key` — защита от повторной отправки форм/ретраев.
* **PostgreSQL + Exposed** для хранения заказов, позиций заказа, таблицы идемпотентности и **outbox**.
* **Flyway** — версионируемые миграции.
* **Outbox Publisher** — воркер, который читает непросланные события из таблицы `outbox` и публикует их в Kafka через абстракцию `EventPublisher`. Это развязывает транзакции БД и доставку событий.
* **Kafka Publisher** (и `NoopEventPublisher` для локала без брокера) — чтобы можно было работать без обязательного подъема Kafka.
* **Имитация платежей** (`/_sim/payments`) — чтобы руками пройти путь *создал заказ → пришел SUCCESS → заказ CONFIRMED*.
* **Простой фронт на Vite/React** (`orders-ui/`) — форма создания заказа + кнопки «симулировать платеж» и «проверить статус».

> 🔎 Примечание: в текущей ревизии статусы записей в outbox у «писателя» и «паблишера» различаются. Это тривиально выравнивается (см. TODO ниже). Сама архитектура outbox реализована: запись события в БД и периодическая публикация в топик вынесены в отдельные компоненты.

---

## Архитектура (коротко)

1. **Создание заказа**

   * `POST /orders` принимает `{customerId, currency, items[]}`.
   * Проверяется `Idempotency-Key`:
     — если ключ уже встречался **с тем же телом**, вернем **прежний ответ**;
     — если ключ повторили **с другим телом**, вернем **409**;
     — если ключ новый, создадим заказ и сохраним «снимок запроса».
2. **Запись события в outbox**

   * После создания заказа пишется событие «OrderCreated» в таблицу `outbox` **в одной транзакции с заказом**.
3. **Публикация событий**

   * Фоновый воркер выбирает непросланные записи из `outbox` и публикует их в Kafka (`topic: orders.v1`) через `EventPublisher`. При успехе помечает запись как отправленную, при ошибке — как ошибочную.
4. **Имитация платежей**

   * `POST /_sim/payments` с `{orderId, status}` публикует событие «PaymentResult». Консьюмер переводит заказ в `CONFIRMED|FAILED`.

Почему так:

* **Идемпотентность** повышает надежность API и UX.
* **Outbox‑паттерн** делает интеграцию **атомарной**: данные в БД и событие о них не расходятся.
* **Kafka** — стандартная шина; через интерфейс `EventPublisher` легко подменить транспорт.

---

## Быстрый старт (локально)

### 0) Предпосылки

* Docker Desktop (для Postgres / Kafka).
* JDK 17, Gradle wrapper — уже в репо.
* Node 18+ для фронта.

### 1) Поднять инфраструктуру

```powershell
# из корня
docker compose up -d
# проверяем Postgres: должен слушать 127.0.0.1:55432
docker compose ps
```

### 2) Запустить backend

```powershell
# важные переменные окружения для локала (Windows PowerShell):
$env:PORT           = "8081"
$env:DB_URL         = "jdbc:postgresql://127.0.0.1:55432/orders?sslmode=disable"
$env:DB_USER        = "postgres"
$env:DB_PASSWORD    = "postgres"
$env:SIM_ROUTES_ENABLED = "true"     # чтобы работал /_sim/payments
# при необходимости: отключить фоновые воркеры/брокер
# $env:APP_KAFKA_DISABLED  = "true"
# $env:APP_WORKERS_DISABLED = "true"

./gradlew run
# проверка
curl http://localhost:8081/health   # OK
```

### 3) Запустить фронт

```powershell
cd orders-ui
npm i
npm run dev
# открыть http://localhost:5173
```

### 4) E2E через curl (без фронта)

```bash
BASE=http://localhost:8081
CID=$(uuidgen 2>/dev/null || echo 11111111-1111-1111-1111-111111111111)

RESP=$(curl -sS -X POST "$BASE/orders" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: key-123" \
  --data '{"customerId":"'"$CID"'","currency":"USD","items":[{"sku":"SKU-1","qty":2,"priceCents":1500}]}' )
echo "$RESP"

OID=$(echo "$RESP" | sed -n 's/.*"orderId":"\([^"\*]*\)".*/\1/p')

curl -sS "$BASE/orders/$OID"

curl -sS -X POST "$BASE/_sim/payments" \
  -H "Content-Type: application/json" \
  --data '{"orderId":"'"$OID"'","status":"SUCCESS"}'

curl -sS "$BASE/orders/$OID"  # должен стать CONFIRMED
```

---

## Структура репозитория

```
orders-ui/                           # Vite + React фронт
src/main/kotlin/com/example/orders/
  adapters/
    db/                              # Exposed DAO/репозитории + OutboxWriter
    kafka/                           # EventPublisher, KafkaEventPublisher, NoopEventPublisher, PaymentResultConsumer
    scheduler/                       # OutboxPublisher — читает outbox и шлет в Kafka
  app/
    Application.kt                   # Ktor bootstrap, плагины, маршруты, фоновые джобы
    OrdersRoutes.kt                  # эндпоинты /orders, /orders/{id}
    SimulatePaymentsRoute.kt         # /_sim/payments (вкл. через SIM_ROUTES_ENABLED)
    RequestIdPlugin.kt               # добавляет X-Request-Id
  domain/
    model/                           # Order, OrderItem, OrderStatus
    ports/                           # OrderRepository, OutboxWriter
  infa/                              # DatabaseConfig, KafkaConfig
resources/db/migration/V1__init.sql  # Flyway миграция
docker-compose.yml                   # Postgres (+ брокер, если используется)
```

---

## Ключевые элементы

### Идемпотентность HTTP

* Заголовок `Idempotency-Key` обязателен для `POST /orders`.
* Для ключа хранится **хэш тела запроса** и **ответ**, что позволяет:
  — вернуть тот же ответ при повторе того же запроса;
  — отдать **409** при несовпадении тела (replay с другим payload).

### Транзакционный Outbox

* При создании заказа пишется запись в `outbox` в рамках **той же транзакции**, что и заказ.
* Паблишер (**OutboxPublisher**) публикует записи в Kafka; успешные помечаются как отправленные, неуспешные — для ретрая.
* Абстракция `EventPublisher` позволяет запускать локально без Kafka (`NoopEventPublisher`).

### Имитация платежей

* `/_sim/payments` упрощает ручное E2E — можно проверить смену статуса заказа на `CONFIRMED` без поднятия внешних систем.

---

## Сборка и запуск

### Gradle (backend)

```bash
./gradlew clean build
./gradlew run
# или дистрибутив:
./gradlew installDist
# ./build/install/orders-service/bin/orders-service
```

### Node (frontend)

```bash
cd orders-ui
npm i
npm run dev       # dev-сервер
npm run build     # прод-сборка (в dist/)
```

> На Windows иногда блокируется `esbuild.exe` антивирусом/IDE. Если `npm ci` падает с `EPERM unlink ... esbuild.exe`, закрыть IDE/антивирус или удалить `node_modules` и повторить `npm i`.

---

## Известные нюансы / TODO

* **Outbox status**: выровнять константы статусов между `ExposedOutboxWriter` и `OutboxPublisher` (сейчас `PENDING` vs `NEW`). Нужен общий enum/константы и миграция.
* **Интеграционные тесты**: заготовки присутствуют (Testcontainers/Postgres), но в Windows‑окружении требуют корректной настройки Docker Desktop и переменных окружения БД; стоит довести до «одной кнопки» в CI.
* **Метрики/трассировка**: добавить Prometheus‑метрики и request tracing поверх `X-Request-Id`.
* **Ретраи/бек‑офф**: добавить backoff‑политику для публикации событий и обработки платежей.
* **Валидация входных данных**: расширить (currency, sku, qty > 0 и т.д.).

---

## Почему это неплохая база под прод

* **Надежность запросов**: идемпотентность снижает частоту «двойных» заказов/платежей.
* **Надежность интеграций**: outbox гарантирует, что события не потеряются при падениях брокера/сети.
* **Чистая модульность**: adapters (Ktor/DB/Kafka) изолирован от domain (модели/порты).
* **Постепенное наращивание**: можно начать с Noop‑паблишера, позже сменить на Kafka.
* **Удобный DX**: Flyway, dev‑флаги, фронт для быстрых сценариев ручного тестирования.


