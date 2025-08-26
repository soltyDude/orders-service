plugins {
    kotlin("jvm") version "1.9.24"
    application
}

repositories {
    mavenCentral()
}
val ktorVersion = "2.3.12"
val exposedVersion = "0.50.1"
val flywayVersion = "10.15.0"
val testcontainersVersion = "1.20.1"
val junitVersion = "5.10.2"

dependencies {
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-cors:$ktorVersion")
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")

    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")

    implementation("org.postgresql:postgresql:42.7.3")
    implementation("org.flywaydb:flyway-core:$flywayVersion")
    implementation("org.flywaydb:flyway-database-postgresql:$flywayVersion")

    implementation("org.apache.kafka:kafka-clients:3.7.0")

    testImplementation("io.ktor:ktor-client-core:$ktorVersion")
    testImplementation("io.ktor:ktor-client-cio:$ktorVersion")
    testImplementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    testImplementation("io.ktor:ktor-serialization-jackson:$ktorVersion")

    testImplementation("org.testcontainers:postgresql:$testcontainersVersion")
    testImplementation("org.testcontainers:junit-jupiter:$testcontainersVersion")
    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")

    testImplementation("org.slf4j:slf4j-simple:2.0.13")
}


kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("com.example.orders.app.ApplicationKt")
}

tasks.test {
    useJUnitPlatform()
}
