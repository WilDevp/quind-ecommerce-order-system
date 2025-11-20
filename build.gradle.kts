import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    java
    id("org.springframework.boot") version "3.2.1" apply false
    id("io.spring.dependency-management") version "1.1.4" apply false
    kotlin("jvm") version "1.9.21" apply false
    kotlin("plugin.spring") version "1.9.21" apply false
}

/**
 * Config común para todos los módulos. Lo centralizamos aquí porque repetir
 * la misma config en 9 build.gradle.kts es un dolor de cabeza para mantener.
 * Cuando necesitamos cambiar algo (por ej. versión de Java), lo hacemos una sola vez.
 */

subprojects {
    apply(plugin = "java")
    apply(plugin = "io.spring.dependency-management")

    group = "com.quind.ecommerce"
    version = "1.0.0-SNAPSHOT"

    java {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    repositories {
        mavenCentral()
    }

    /**
     * Dependencias base que todos los módulos vamos a usar.
     * Usamos Spring Boot BOM para no tener que especificar versiones manualmente.
     * Error común: mezclar versiones de Spring Boot - el BOM previene eso.
     */

    dependencies {
        // BOM de Spring Boot - esto garantiza compatibilidad entre todas las deps
        implementation(platform("org.springframework.boot:spring-boot-dependencies:3.2.1"))

        // Lombok para reducir boilerplate. Sí, algunos puristas lo odian, pero reduce cientos de líneas de getters/setters/constructors que nadie quiere mantener.
        compileOnly("org.projectlombok:lombok:1.18.30")
        annotationProcessor("org.projectlombok:lombok:1.18.30")

        // Stack de testing - AssertJ en vez de las assertions de JUnit porque son mucho más legibles
        testImplementation("org.springframework.boot:spring-boot-starter-test")
        testImplementation("io.projectreactor:reactor-test")
        testImplementation("org.junit.jupiter:junit-jupiter")
        testImplementation("org.mockito:mockito-core")
        testImplementation("org.assertj:assertj-core")
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}

/**
 * Configuración para módulos de dominio (order-domain, payment-domain, notification-domain)
 * Regla de oro: el dominio NO debe depender de frameworks. Nada de Spring, ni Hibernate, ni nada.
 * Solo anotaciones estándar de Jakarta para marcar entidades y validaciones.
 *
 * ¿Por qué? Porque nuestra lógica de negocio debe sobrevivir migraciones de frameworks.
 * Suelen pasar que equipos inicizron mal y sus proyectos fueron re-escritos completamente porque acoplaron la lógica a frameworks obsoletos.
 */

configure(subprojects.filter { it.name.endsWith("-domain") }) {
    dependencies {
        // Validation API para validar value objects y entidades
        implementation("jakarta.validation:jakarta.validation-api")

        // JPA API solo para anotaciones (@Entity, @Id). No usamos Hibernate aquí, solo las interfaces estándar. La implementación va en infrastructure.
        implementation("jakarta.persistence:jakarta.persistence-api:3.1.0")
    }
}

/**
 * Capa de aplicación - casos de uso y orquestación.
 * Aquí vive la lógica de "cómo" se ejecutan las operaciones de negocio.
 * Depende del dominio pero no conoce detalles de bases de datos o APIs externas.
 *
 * Trade-off: permitimos Spring aquí porque necesitamos DI y transacciones.
 * Podríamos usar interfaces puras, pero seamos pragmáticos - no vale la pena la complejidad extra.
 */

configure(subprojects.filter { it.name.endsWith("-application") }) {
    dependencies {
        // Referencia al módulo domain del mismo servicio
        val serviceName = project.parent?.name ?: ""
        implementation(project(":$serviceName:${serviceName.replace("-service", "")}-domain"))

        // Spring solo para DI y gestión de transacciones
        implementation("org.springframework:spring-context")
        implementation("org.springframework:spring-tx")

        // Reactor porque trabajamos con flujos reactivos (Mono/Flux)
        implementation("io.projectreactor:reactor-core")
    }
}

/**
 * Infraestructura - donde ponemos todo el "ruido" técnico.
 * Controllers, repos, Kafka, bases de datos, configuración de Spring Boot, etc.
 * Esta capa puede ser un desastre si no tenemos cuidado, así que mantenemos los adaptadores limpios.
 *
 * Importante: estos módulos generan los JARs ejecutables (bootJar).
 */

configure(subprojects.filter { it.name.endsWith("-infrastructure") }) {
    apply(plugin = "org.springframework.boot")

    dependencies {
        // Referencias a las otras capas del mismo servicio
        val serviceName = project.parent?.name ?: ""
        val moduleBaseName = serviceName.replace("-service", "")
        implementation(project(":$serviceName:$moduleBaseName-domain"))
        implementation(project(":$serviceName:$moduleBaseName-application"))

        // Spring Boot starters - WebFlux porque vamos reactivo
        implementation("org.springframework.boot:spring-boot-starter-webflux")
        implementation("org.springframework.boot:spring-boot-starter-validation")
        implementation("org.springframework.boot:spring-boot-starter-actuator")

        // R2DBC para PostgreSQL reactivo. No usaremos JDBC con WebFlux porque vamos a bloquear threads.
        implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
        implementation("org.postgresql:r2dbc-postgresql")

        // MongoDB reactivo para event store y queries de notificaciones
        implementation("org.springframework.boot:spring-boot-starter-data-mongodb-reactive")

        // Kafka para comunicación asíncrona entre servicios
        implementation("org.springframework.kafka:spring-kafka")
        implementation("io.projectreactor.kafka:reactor-kafka")

        // Jackson para serialización JSON. Los módulos extra son para LocalDateTime y Kotlin.
        implementation("com.fasterxml.jackson.core:jackson-databind")
        implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
        implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

        // Resilience4j - circuit breakers, retries, rate limiting. No lo agregamos "por si acaso", lo usamos cuando realmente necesitamos resiliencia en llamadas externas.
        implementation("io.github.resilience4j:resilience4j-spring-boot3:2.2.0")
        implementation("io.github.resilience4j:resilience4j-reactor:2.2.0")

        // OpenAPI/Swagger - documentación automática de APIs
        implementation("org.springdoc:springdoc-openapi-starter-webflux-ui:2.3.0")

        // Métricas con Prometheus - útil para monitoreo en producción
        runtimeOnly("io.micrometer:micrometer-registry-prometheus")

        // Testcontainers - levanta Postgres, Mongo y Kafka reales para integration tests. Sí, son más lentos que mocks, pero encuentran bugs que los mocks no encuentran.
        testImplementation("org.testcontainers:testcontainers:1.19.3")
        testImplementation("org.testcontainers:junit-jupiter:1.19.3")
        testImplementation("org.testcontainers:postgresql:1.19.3")
        testImplementation("org.testcontainers:mongodb:1.19.3")
        testImplementation("org.testcontainers:kafka:1.19.3")
        testImplementation("org.springframework.boot:spring-boot-testcontainers")
    }
}

// Tasks personalizadas para facilitar el trabajo con múltiples servicios. En vez de buildear cada servicio manualmente, usamos estas.

tasks.register("buildAllServices") {
    group = "build"
    description = "Builds all microservices (Order, Payment, Notification)"

    dependsOn(
        ":order-service:order-infrastructure:build",
        ":payment-service:payment-infrastructure:build",
        ":notification-service:notification-infrastructure:build"
    )
}

tasks.register("testAll") {
    group = "verification"
    description = "Runs tests for all modules"

    dependsOn(subprojects.map { "${it.path}:test" })
}

// Gradle Wrapper - versión 8.5 con todo incluido para que los IDEs tengan el source code. Siempre commiteamos el wrapper al repo para que todos usen la misma versión de Gradle.

tasks.wrapper {
    gradleVersion = "8.5"
    distributionType = Wrapper.DistributionType.ALL
}
