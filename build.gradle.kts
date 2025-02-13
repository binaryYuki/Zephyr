import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.tasks.testing.logging.TestLogEvent.*

plugins {
  java
  application
  id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "xyz.tzpro.core"
version = "1.0.0-SNAPSHOT"

repositories {
  mavenCentral()
}

val vertxVersion = "4.5.13"
val junitJupiterVersion = "5.11.4"
val flywayVersion = "11.3.1"
val dotenvVersion = "3.1.0"
val nettyVersion = "4.1.118.Final"
val hibernateCoreVersion = "6.6.5.Final"
val caffeineVersion = "3.2.0"
val hibernateEntityManagerVersion = "5.6.15.Final"
val hikariCPVersion = "6.2.1"
val mysqlConnectorVersion = "8.0.33"
val valkeyVersion = "5.3.0"
val log4jVersion = "2.24.3"

val mainVerticleName = "Zephyr.MainVerticle"
val launcherClassName = "io.vertx.core.Launcher"

val watchForChange = "src/**/*"
val doOnChange = "${projectDir}/gradlew classes"

application {
  mainClass.set(launcherClassName)
}

dependencies {
  implementation(platform("io.vertx:vertx-stack-depchain:$vertxVersion"))
  implementation("io.vertx:vertx-health-check")
  implementation("io.vertx:vertx-web")
  implementation("io.vertx:vertx-auth-otp")
  implementation("io.vertx:vertx-web-openapi")
  implementation("io.vertx:vertx-circuit-breaker")
  implementation("io.vertx:vertx-json-schema")
  implementation("io.vertx:vertx-web-openapi-router")
  implementation("io.vertx:vertx-web-api-contract")
  implementation("io.vertx:vertx-tcp-eventbus-bridge")
  implementation("io.vertx:vertx-mysql-client:$vertxVersion")
  implementation("io.vertx:vertx-web-client:$vertxVersion")
  implementation("org.flywaydb:flyway-mysql:$flywayVersion")
  implementation("io.github.cdimascio:dotenv-java:$dotenvVersion")
  implementation("io.netty:netty-all:$nettyVersion")
  implementation("org.hibernate:hibernate-core:$hibernateCoreVersion")
  implementation("com.github.ben-manes.caffeine:caffeine:$caffeineVersion")
  implementation("org.hibernate:hibernate-entitymanager:$hibernateEntityManagerVersion")
  implementation("com.zaxxer:HikariCP:$hikariCPVersion")
  implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
  implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.18.2")
  implementation("mysql:mysql-connector-java:$mysqlConnectorVersion")
  implementation("io.valkey:valkey-java:$valkeyVersion")
  implementation("org.apache.logging.log4j:log4j-api:$log4jVersion")
  implementation("org.apache.logging.log4j:log4j-core:$log4jVersion")
  testImplementation("io.vertx:vertx-junit5")
  testImplementation("org.junit.jupiter:junit-jupiter:$junitJupiterVersion")
}

java {
  sourceCompatibility = JavaVersion.VERSION_21
  targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<ShadowJar> {
  archiveClassifier.set("fat")
  manifest {
    attributes(mapOf("Main-Verticle" to mainVerticleName))
  }
  mergeServiceFiles()
}

tasks.withType<Test> {
  useJUnitPlatform()
  testLogging {
    events = setOf(PASSED, SKIPPED, FAILED)
  }
}
tasks.withType<JavaExec> {
  args = listOf("run", mainVerticleName, "--redeploy=$watchForChange", "--launcher-class=$launcherClassName", "--on-redeploy=$doOnChange")
}
