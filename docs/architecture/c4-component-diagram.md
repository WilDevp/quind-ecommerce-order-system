# Diagrama C4 - Nivel de Componentes

Este documento muestra la arquitectura del sistema usando el modelo C4. Si no estás familiarizado con C4, es básicamente una forma de visualizar arquitectura en diferentes niveles de zoom: Context → Containers → Components → Code. Aquí nos enfocamos en Containers y Components.

Por qué C4? Porque después de intentar explicar la arquitectura con diagramas UML tradicionales y ver caras confundidas, encontré que C4 es más intuitivo. La gente lo entiende rápido.

## Vista General del Sistema

```mermaid
C4Context
    title Sistema de Gestión de Órdenes E-commerce

    Person(customer, "Cliente", "Usuario del sistema de e-commerce")

    System_Boundary(ecommerce, "E-commerce Order System") {
        Container(orderService, "Order Service", "Spring Boot, WebFlux", "Gestiona el ciclo de vida de órdenes")
        Container(paymentService, "Payment Service", "Spring Boot, WebFlux", "Procesa pagos")
        Container(notificationService, "Notification Service", "Spring Boot, WebFlux", "Envía notificaciones")

        ContainerDb(postgres, "PostgreSQL", "R2DBC", "Almacena datos transaccionales")
        ContainerDb(mongo, "MongoDB", "Reactive Driver", "Event Store y auditoría")
        ContainerQueue(kafka, "Apache Kafka", "Message Broker", "Event streaming platform")
    }

    System_Ext(paymentGateway, "Payment Gateway", "Procesa pagos externos (Stripe, PayPal)")
    System_Ext(emailService, "Email Service", "Servicio externo de email (SendGrid)")

    Rel(customer, orderService, "Crea órdenes", "HTTPS/JSON")
    Rel(orderService, postgres, "Lee/Escribe órdenes", "R2DBC")
    Rel(orderService, kafka, "Publica eventos", "Kafka Protocol")
    Rel(orderService, mongo, "Guarda event log", "MongoDB Protocol")

    Rel(paymentService, kafka, "Consume/Publica eventos", "Kafka Protocol")
    Rel(paymentService, postgres, "Lee/Escribe pagos", "R2DBC")
    Rel(paymentService, paymentGateway, "Procesa pagos", "HTTPS/REST")

    Rel(notificationService, kafka, "Consume eventos", "Kafka Protocol")
    Rel(notificationService, mongo, "Guarda notificaciones", "MongoDB Protocol")
    Rel(notificationService, emailService, "Envía emails", "HTTPS/REST")
```

---

## Nivel de Contenedores - Order Service

Aquí viene lo interesante. Voy a desglosar cada servicio mostrando su estructura interna según Clean Architecture (ver ADR-001).

La clave aquí es que TODOS los servicios siguen la misma estructura. Order Service, Payment Service, Notification Service... todos tienen las mismas capas. Esto no es coincidencia, es intencional. Cuando saltas de un servicio a otro, sabes exactamente dónde encontrar las cosas.

```mermaid
graph TB
    subgraph "Order Service - Puerto 8080"
        API[REST API Layer<br/>WebFlux Controllers]

        subgraph "Application Layer"
            CMD[Command Handlers<br/>CreateOrder, CancelOrder]
            QRY[Query Handlers<br/>GetOrder, ListOrders]
        end

        subgraph "Domain Layer"
            AGG[Order Aggregate<br/>Business Logic]
            VO[Value Objects<br/>Money, Address, etc]
            EVT[Domain Events<br/>OrderCreated, etc]
        end

        subgraph "Infrastructure Layer"
            REPO[Order Repository<br/>R2DBC Adapter]
            KAFKA_P[Kafka Producer<br/>Event Publisher]
            EVENT_STORE[Event Store<br/>MongoDB Adapter]
        end
    end

    CLIENT[Client] -->|HTTP POST/GET| API
    API -->|Commands| CMD
    API -->|Queries| QRY
    CMD -->|Uses| AGG
    QRY -->|Uses| REPO
    AGG -->|Produces| EVT
    CMD -->|Persists| REPO
    EVT -->|Published by| KAFKA_P
    EVT -->|Stored in| EVENT_STORE

    REPO -->|R2DBC| DB[(PostgreSQL)]
    KAFKA_P -->|Produces| KAFKA[Apache Kafka]
    EVENT_STORE -->|Writes| MONGO[(MongoDB)]
```

---

## Nivel de Contenedores - Payment Service

```mermaid
graph TB
    subgraph "Payment Service - Puerto 8081"
        API[REST API Layer<br/>WebFlux Controllers]

        subgraph "Application Layer"
            CMD[Command Handlers<br/>ProcessPayment]
            QRY[Query Handlers<br/>GetPayment]
        end

        subgraph "Domain Layer"
            AGG[Payment Aggregate<br/>Business Logic]
            EVT[Domain Events<br/>PaymentProcessed, etc]
        end

        subgraph "Infrastructure Layer"
            REPO[Payment Repository<br/>R2DBC Adapter]
            KAFKA_C[Kafka Consumer<br/>Event Listener]
            KAFKA_P[Kafka Producer<br/>Event Publisher]
            GATEWAY[Payment Gateway Client<br/>External API]
        end
    end

    CLIENT[Client] -->|HTTP GET/POST| API
    KAFKA[Kafka<br/>OrderCreated] -->|Consumes| KAFKA_C
    KAFKA_C -->|Triggers| CMD
    CMD -->|Uses| AGG
    AGG -->|Produces| EVT
    EVT -->|Published by| KAFKA_P
    KAFKA_P -->|Produces| KAFKA_OUT[Kafka<br/>PaymentProcessed]
    CMD -->|Persists| REPO
    REPO -->|R2DBC| DB[(PostgreSQL)]
    AGG -->|Calls| GATEWAY
    GATEWAY -->|HTTPS| EXT[External Payment<br/>Gateway]
```

---

## Nivel de Contenedores - Notification Service

```mermaid
graph TB
    subgraph "Notification Service - Puerto 8082"
        API[REST API Layer<br/>WebFlux Controllers<br/>Query Only]

        subgraph "Application Layer"
            CMD[Command Handlers<br/>SendNotification]
            QRY[Query Handlers<br/>GetNotifications]
        end

        subgraph "Domain Layer"
            AGG[Notification Aggregate<br/>Business Logic]
        end

        subgraph "Infrastructure Layer"
            REPO[Notification Repository<br/>MongoDB Adapter]
            KAFKA_C[Kafka Consumer<br/>Multi-topic Listener]
            EMAIL[Email Client<br/>SendGrid/SMTP]
        end
    end

    CLIENT[Client] -->|HTTP GET| API
    KAFKA[Kafka<br/>Multiple Topics] -->|Consumes| KAFKA_C
    KAFKA_C -->|Triggers| CMD
    CMD -->|Uses| AGG
    CMD -->|Persists| REPO
    AGG -->|Sends| EMAIL
    EMAIL -->|HTTPS| EXT[External Email<br/>Service]
    REPO -->|MongoDB Protocol| MONGO[(MongoDB)]
    QRY -->|Queries| REPO
```

---

## Flujo de Comunicación Entre Servicios

Este diagrama de secuencia muestra el happy path completo. En producción vas a ver variaciones (pagos que fallan, timeouts, etc), pero este es el flujo ideal que queremos que pase el 95% del tiempo.

Fíjate cómo todo es asíncrono excepto la interacción inicial del cliente. El usuario hace POST, recibe 201 Created inmediatamente, y el resto pasa en background. Esto es crítico para UX - el usuario no espera 30 segundos a que se procese el pago.

```mermaid
sequenceDiagram
    participant Client
    participant OrderService
    participant Kafka
    participant PaymentService
    participant NotificationService
    participant PaymentGateway
    participant EmailService

    Client->>OrderService: POST /api/v1/orders
    OrderService->>OrderService: Create Order (PENDING)
    OrderService->>Kafka: Publish OrderCreated
    OrderService-->>Client: 201 Created

    Kafka->>PaymentService: Consume OrderCreated
    PaymentService->>PaymentService: Create Payment
    PaymentService->>PaymentGateway: Process Payment
    PaymentGateway-->>PaymentService: Payment Success
    PaymentService->>Kafka: Publish PaymentProcessed

    Kafka->>OrderService: Consume PaymentProcessed
    OrderService->>OrderService: Update Order (PAID)
    OrderService->>Kafka: Publish OrderPaid

    Kafka->>NotificationService: Consume OrderCreated
    NotificationService->>EmailService: Send Order Confirmation

    Kafka->>NotificationService: Consume PaymentProcessed
    NotificationService->>EmailService: Send Payment Receipt
```

---

## Infraestructura - Docker Compose

Esto es cómo corre todo localmente en desarrollo. En producción tendrías clusters de Kubernetes o similar, pero el concepto es el mismo: servicios independientes comunicándose vía red.

La red `ecommerce-network` es importante. Todos los containers están en la misma red Docker, lo que significa que pueden hablarse por nombre (postgres:5432, kafka:9092). Sin esto, tendrías que exponer puertos y usar localhost, que es más frágil.

```mermaid
graph TB
    subgraph "Docker Network: ecommerce-network"
        subgraph "Application Services"
            OS[order-service<br/>:8080]
            PS[payment-service<br/>:8081]
            NS[notification-service<br/>:8082]
        end

        subgraph "Data Stores"
            PG[postgres<br/>:5432]
            MG[mongodb<br/>:27017]
        end

        subgraph "Message Broker"
            ZK[zookeeper<br/>:2181]
            KF[kafka<br/>:9092]
        end
    end

    OS -->|R2DBC| PG
    OS -->|Reactive Driver| MG
    OS -->|Producer| KF

    PS -->|R2DBC| PG
    PS -->|Consumer/Producer| KF

    NS -->|Reactive Driver| MG
    NS -->|Consumer| KF

    KF -->|Depends on| ZK
```

---

## Despliegue de Componentes

Esta es la visión de producción. Nota las diferencias clave vs desarrollo:

- **Múltiples instancias de cada servicio** (alta disponibilidad + escalabilidad)
- **Clusters de bases de datos** (no single points of failure)
- **API Gateway opcional** (podríamos usar uno, o exponer servicios directamente)

El número de instancias (2+ para Orders/Payments, 1+ para Notifications) no es arbitrario. Orders y Payments son críticos - necesitan redundancia. Notifications es menos crítico, puede correr con una instancia (aunque en producción real probablemente tendrías 2 también).

```mermaid
graph LR
    subgraph "Client Layer"
        WEB[Web Browser]
        MOBILE[Mobile App]
    end

    subgraph "API Layer"
        GW[API Gateway<br/>Optional]
    end

    subgraph "Service Layer"
        OS[Order Service<br/>Instances: 2+]
        PS[Payment Service<br/>Instances: 2+]
        NS[Notification Service<br/>Instances: 1+]
    end

    subgraph "Data Layer"
        PG[PostgreSQL<br/>Cluster]
        MG[MongoDB<br/>Replica Set]
        KF[Kafka Cluster<br/>3 Brokers]
    end

    WEB -->|HTTPS| GW
    MOBILE -->|HTTPS| GW
    GW -->|Load Balance| OS
    GW -->|Load Balance| PS
    GW -->|Load Balance| NS

    OS -->|Connection Pool| PG
    OS -->|Connection Pool| MG
    OS -->|Producer| KF

    PS -->|Connection Pool| PG
    PS -->|Consumer Group| KF

    NS -->|Connection Pool| MG
    NS -->|Consumer Group| KF
```

---

## Notas de Arquitectura

### Principios Aplicados

**Clean Architecture (ver ADR-001):**
- Cada servicio tiene capas claramente definidas (Domain, Application, Infrastructure)
- Las dependencias apuntan hacia adentro (Infrastructure → Application → Domain)
- El dominio no conoce detalles de infraestructura

Esto nos costó código extra (interfaces, adapters, mappers), pero vale la pena. Cambiar de PostgreSQL a MySQL? Solo tocas Infrastructure. Cambiar lógica de negocio? Solo tocas Domain. El aislamiento es real.

**Event-Driven (ver ADR-002):**
- Comunicación asíncrona vía Kafka
- Servicios desacoplados temporalmente
- Event Sourcing en MongoDB para auditoría

La parte de Event Sourcing es especialmente poderosa. Cada cambio en el sistema está registrado como evento en MongoDB. Puedes reconstruir el estado de cualquier orden en cualquier momento histórico. Debugging se vuelve arqueología: "qué pasó con esta orden el martes pasado a las 3pm?" → miras los eventos, ahí está todo.

**Programación Reactiva:**
- Spring WebFlux (non-blocking I/O)
- R2DBC para acceso reactivo a PostgreSQL
- MongoDB Reactive Driver

Reactive no es solo buzz. Con WebFlux, un thread puede manejar miles de requests concurrentes (vs ~200 con threads tradicionales). Esto es crítico para órdenes - esperamos picos de tráfico en promociones.

**Escalabilidad:**
- Servicios stateless (pueden escalar horizontalmente)
- Kafka permite paralelización con particiones
- Bases de datos pueden clusterizarse

Stateless es la clave. No hay sesiones en memoria, no hay caches locales. Puedes agregar una instancia de Order Service en cualquier momento y funciona. Kubernetes puede hacer autoscaling sin que te enteres.

### Patrones de Integración

**Choreography-based Saga (ver ADR-003):**
- No hay orquestador central
- Cada servicio reacciona a eventos de forma autónoma
- Compensaciones manejadas por eventos (OrderCancelled, PaymentFailed)

Choreography vs Orchestration fue una decisión difícil. Choreography es más resiliente pero más difícil de razonar. Para nuestro flujo (relativamente simple), vale la pena.

**CQRS (Command Query Responsibility Segregation):**
- Separación de Commands y Queries
- Commands modifican estado y publican eventos
- Queries solo leen datos

CQRS no es solo un patrón fancy. Nos permite optimizar reads y writes independientemente. Podríamos tener una read database (MongoDB) optimizada para queries complejas, y una write database (PostgreSQL) optimizada para transacciones. No lo hacemos aún, pero la arquitectura lo permite.

**Event Sourcing:**
- Eventos almacenados en MongoDB
- Permite reconstruir estado histórico
- Auditoría completa del sistema

Esto nos salvó en un bug de producción real: un pago se procesó dos veces. Pudimos rastrear EXACTAMENTE qué pasó mirando los eventos. Sin Event Sourcing, hubiera sido un misterio.
