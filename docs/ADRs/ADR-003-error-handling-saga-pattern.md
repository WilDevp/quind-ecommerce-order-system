# ADR-003: Estrategia de Manejo de Errores y Saga Pattern

## Estado
**DRAFT** - 2025-01-19

## Contexto

Aquí viene lo complicado. Tenemos microservicios distribuidos, eventos volando por Kafka, múltiples bases de datos... y necesitamos que TODO esto sea consistente. O al menos, eventualmente consistente. Sin perder dinero en el proceso.

### El problema que me quita el sueño

Imagina este escenario (y créanme, pasa):

1. Usuario hace checkout de una orden de $150
2. Order Service crea la orden (estado: PENDING) ✓
3. Payment Service procesa el pago... y **FALLA** porque la tarjeta no tiene fondos ✗

Ahora qué? Tenemos una orden en la base de datos en estado PENDING, pero el pago falló. El usuario no debería ver esa orden como válida. Necesitamos cancelarla. Necesitamos liberar el inventario que reservamos (si lo hicimos). Necesitamos notificar al usuario que algo salió mal.

Y esto es solo un escenario. Hay peores:

- El servicio de pagos cae DESPUÉS de cobrar pero ANTES de publicar el evento PaymentProcessed
- La base de datos de órdenes está temporalmente no disponible
- Kafka está experimentando rebalances
- Un timeout de red hace que reintenemos una operación que ya se ejecutó

En un monolito con una sola base de datos, usaríamos transacciones ACID. Un rollback y listo. Pero aquí? **No podemos hacer un BEGIN TRANSACTION entre microservicios.** Cada uno tiene su propia base de datos. Kafka no es transaccional entre servicios.

Bienvenidos al mundo distribuido. Es hermoso y terrible al mismo tiempo.

## Decisión

Vamos con **Saga Pattern estilo Choreography** para manejar transacciones distribuidas, más una estrategia robusta de manejo de errores a tres niveles.

### Por qué Saga Pattern?

Porque en sistemas distribuidos, no tienes transacciones ACID entre servicios. Period. Entonces tienes dos opciones:

1. **Llorar** (no recomendado)
2. **Usar Sagas** para lograr consistencia eventual

Una Saga es básicamente: "Una secuencia de transacciones locales, donde cada paso puede compensarse si algo falla después."

### Choreography vs Orchestration

Decidimos ir con **Choreography** (cada servicio reacciona a eventos de forma autónoma) en vez de Orchestration (un coordinador central).

**Por qué Choreography:**
- No hay un orquestador central que sea punto único de fallo
- Los servicios son más autónomos (filosofía de microservicios)
- Escala mejor (no hay cuello de botella central)
- Más resiliente (un servicio caído no detiene el resto)

**El costo:**
- El flujo completo está distribuido (no hay un solo lugar que lo muestre)
- Más difícil de razonar sobre el estado global
- Requires buenos diagramas y documentación

Para nuestro caso (flujo relativamente directo: crear orden → procesar pago → notificar), Choreography funciona bien.

### Cómo funciona el flujo

**Happy path (todo sale bien):**

```
1. Order Service: Crea orden (PENDING) → publica OrderCreated
2. Payment Service: Consume OrderCreated → procesa pago → publica PaymentProcessed
3. Order Service: Consume PaymentProcessed → actualiza orden (PAID) → publica OrderConfirmed
4. Notification Service: Consume eventos → envía notificaciones
```

Todos felices. El usuario tiene su orden, su pago fue procesado, recibió un email.

**Sad path (pago falla):**

```
1. Order Service: Crea orden (PENDING) → publica OrderCreated
2. Payment Service: Consume OrderCreated → intenta procesar pago → FALLA → publica PaymentFailed
3. Order Service: Consume PaymentFailed → cancela orden → publica OrderCancelled
4. Notification Service: Consume OrderCancelled → notifica al usuario del fallo
```

La clave es que cada servicio sabe cómo reaccionar a fallos. PaymentFailed desencadena compensaciones.

### Estados y transiciones

Esto es importante definirlo claramente:

```
PENDING → CONFIRMED → PAYMENT_PROCESSING → PAID → SHIPPED → DELIVERED
   ↓           ↓              ↓
CANCELLED ← CANCELLED ← FAILED
```

**Reglas:**
- Desde PENDING puedes ir a CONFIRMED o CANCELLED
- Desde CONFIRMED puedes ir a PAYMENT_PROCESSING o CANCELLED (timeout esperando confirmación)
- Desde PAYMENT_PROCESSING puedes ir a PAID o FAILED
- FAILED es terminal (requiere crear nueva orden)
- CANCELLED es terminal

No puedes ir de PAID a CANCELLED directamente. Si ya pagaste y quieres cancelar, es un REFUND (flujo diferente).

### Compensaciones

Aquí es donde se pone interesante. Cuando algo falla, necesitamos deshacer lo que ya hicimos.

| Servicio | Acción Original | Evento de Compensación | Qué hace |
|----------|----------------|------------------------|----------|
| Order Service | Crear orden (PENDING) | OrderCancelled | Marca orden como CANCELLED |
| Inventory Service | Reservar stock | ReleaseInventory | Libera items reservados |
| Payment Service | Procesar pago | RefundPayment | Reversa el cobro (si ya se hizo) |
| Notification Service | N/A | NotifyFailure | Informa al usuario |

**Importante:** Las compensaciones NO son rollbacks perfectos. Son "mejor esfuerzo" para volver a un estado consistente. A veces requieren intervención manual.

## Manejo de errores en tres niveles

Aquí viene la estrategia de combate real. Dividimos errores en tres categorías:

### 1. Errores transitorios (recuperables con retry)

**Qué son:**
- Timeout de red
- Base de datos temporalmente no disponible
- Kafka broker reiniciando
- Service discovery en progreso

**Estrategia: Retry exponencial con backoff**

```
Intento 1: inmediato
Intento 2: espera 1s
Intento 3: espera 2s
Intento 4: espera 4s
Intento 5: espera 8s
Máximo: 5 reintentos
```

Usamos **Resilience4j** para esto. Circuit Breaker + Retry.

Si después de 5 reintentos sigue fallando → DLQ (Dead Letter Queue).

**Por qué esto funciona:** La mayoría de problemas de red/infraestructura se resuelven en segundos. Esperar un poco y reintentar resuelve el 80% de fallos transitorios.

### 2. Errores de negocio (no recuperables, no reintentar)

**Qué son:**
- Pago rechazado por fondos insuficientes
- Producto sin stock
- Tarjeta expirada
- Datos inválidos (precio negativo, email inválido)

**Estrategia: Fail fast + compensación**

**NO reintentamos.** No tiene sentido. Si la tarjeta no tiene fondos, reintentar 5 veces no va a hacer que aparezca dinero mágicamente.

Lo que hacemos:
1. Detectar el error de negocio
2. Publicar evento de fallo inmediatamente (PaymentFailed, OutOfStock, etc.)
3. Activar flujo de compensación
4. Notificar al usuario con mensaje claro

```java
try {
    paymentGateway.charge(amount);
} catch (InsufficientFundsException e) {
    // Es error de negocio, no reintentar
    log.warn("Payment failed: insufficient funds for order {}", orderId);
    publishEvent(new PaymentFailed(orderId, "INSUFFICIENT_FUNDS"));
    return; // NO throw, NO retry
}
```

### 3. Errores técnicos (bugs, excepciones inesperadas)

**Qué son:**
- NullPointerException
- ArrayIndexOutOfBoundsException
- Bugs en código
- Estados inesperados

**Estrategia: Log + DLQ + Alert**

Estos son los peores porque no deberían pasar. Son bugs.

1. Log completo (stack trace, evento que lo causó, estado del sistema)
2. Reintentar 3 veces (por si es concurrencia rara)
3. Si sigue fallando → DLQ
4. **Alert al equipo de ops**
5. Requiere análisis manual

No queremos que un bug bloquee el consumer indefinidamente. Lo mandamos a DLQ y seguimos procesando otros eventos.

## Idempotencia (crítico)

Esto no es opcional. En sistemas distribuidos con reintentos, los eventos SE VAN A PROCESAR MÚLTIPLES VECES. Es inevitable.

### Por qué pasa:

- Kafka reintenta por timeout de red
- Consumer se cae después de procesar pero antes de commitear offset
- Rebalance del consumer group
- Manual replay de eventos

### Cómo lo manejamos:

**Tabla de deduplicación en CADA servicio:**

```sql
CREATE TABLE processed_events (
    event_id VARCHAR(36) PRIMARY KEY,
    event_type VARCHAR(50) NOT NULL,
    aggregate_id VARCHAR(36),
    processed_at TIMESTAMP NOT NULL,
    service_version VARCHAR(10),
    INDEX idx_aggregate_id (aggregate_id),
    INDEX idx_processed_at (processed_at)
);
```

**Patrón de procesamiento:**

```java
@Transactional  // IMPORTANTE: todo en una transacción
public void handleOrderCreated(OrderCreatedEvent event) {
    // 1. Check: ya procesé este evento?
    if (processedEventsRepo.existsById(event.getEventId())) {
        log.info("Event {} already processed, skipping (idempotent)",
                 event.getEventId());
        return;  // Safe exit
    }

    // 2. Procesar lógica de negocio
    Order order = createOrder(event);
    orderRepo.save(order);

    // 3. Marcar como procesado (MISMA transacción!)
    processedEventsRepo.save(new ProcessedEvent(
        event.getEventId(),
        event.getEventType(),
        event.getAggregateId(),
        Instant.now(),
        appVersion
    ));

    // Si cualquier paso falla → rollback de TODO
}
```

La clave es que el check de idempotencia y el procesamiento están en la **misma transacción de base de datos**. O se hacen ambos, o ninguno.

Con esto, puedes reintentar eventos infinitas veces sin efectos secundarios. Es **oro**.

## Timeouts y políticas por operación

No todos los timeouts son iguales. Ajustamos según la naturaleza de la operación:

| Operación | Timeout | Reintentos | Backoff | Por qué |
|-----------|---------|------------|---------|---------|
| Kafka Producer | 30s | Infinito | Exponencial | Crítico que se publique |
| Kafka Consumer | 5min | N/A | N/A | Dar tiempo a procesar |
| HTTP externo | 10s | 3 | 1s, 2s, 4s | APIs externas son impredecibles |
| DB query | 5s | 3 | 500ms, 1s, 2s | DB local debería ser rápida |
| Payment Gateway | 30s | 2 | 5s, 10s | Operación costosa, dar tiempo |

Estos números vienen de experiencia. Los vas a necesitar ajustar según tu infraestructura.

## Dead Letter Queue (DLQ)

Los DLQs son tu red de seguridad. Cuando todo lo demás falla, el evento va aquí para análisis manual.

**Topics DLQ:**
- `order-events-dlq`
- `payment-events-dlq`
- `notification-events-dlq`

**Estructura del mensaje en DLQ:**

```json
{
  "originalEvent": {
    "eventId": "uuid",
    "eventType": "PaymentProcessed",
    "payload": { ... }
  },
  "errorDetails": {
    "errorType": "PaymentGatewayTimeout",
    "errorMessage": "Connection timeout after 30s",
    "stackTrace": "...",
    "attemptNumber": 5,
    "lastAttemptTimestamp": "2025-01-19T10:30:00Z"
  },
  "metadata": {
    "serviceName": "payment-service",
    "serviceVersion": "1.2.3",
    "correlationId": "...",
    "environment": "production"
  }
}
```

**Proceso:**
1. Evento falla después de todos los reintentos configurados
2. Se serializa con contexto completo del error
3. Se publica a topic DLQ correspondiente
4. Sistema de alertas notifica al equipo (PagerDuty, Slack, lo que uses)
5. Equipo analiza: es bug? es datos malos? es problema de infraestructura?
6. Se corrige el problema
7. **Opcionalmente** se reprocesan eventos desde DLQ

DLQ no es un lugar donde eventos van a morir. Es un lugar donde van a ser investigados.

## Consecuencias

Como siempre, hay trade-offs.

### Lo bueno

**Resiliencia real.**
El sistema puede recuperarse automáticamente de errores transitorios. He visto esto en producción: Kafka se reinicia, hay un blip de 30 segundos, y el sistema se auto-recupera. Sin intervención manual.

**Consistencia eventual garantizada.**
Aunque haya fallos parciales, sabemos que el sistema eventualmente va a converger a un estado consistente. Las compensaciones aseguran eso.

**No hay puntos únicos de fallo.**
No hay un orquestador central. Si Payment Service se cae, Order Service y Notification Service siguen funcionando. Cuando Payment vuelve, procesa los eventos pendientes.

**Trazabilidad completa.**
Con correlation IDs y event sourcing en Kafka + MongoDB, puedes seguir una orden a través de TODO el sistema. "Por qué esta orden se canceló?" → Miras los eventos, ahí está toda la historia.

**Idempotencia robusta.**
Puedes reintentar lo que quieras. Reprocesar eventos históricos. Hacer replays. Todo es seguro.

### Lo no tan bueno (hay que ser realistas)

**Complejidad significativa.**
Esto no es trivial. Tienes lógica de compensación, manejo de errores en tres niveles, DLQs, idempotencia... Es código extra. Es complejidad mental.

Desarrolladores junior van a sufrir al principio. Necesitas tiempo para entrenar al equipo.

**Eventual consistency puede confundir usuarios.**
El usuario hace checkout y ve "Tu pago está siendo procesado..." durante unos segundos. No es instantáneo como en un monolito.

Necesitas UX que refleje esto. Spinners, estados intermedios, notificaciones push cuando se completa.

**Testing es MÁS complejo.**
No solo testas el happy path. Necesitas testear:
- ¿Qué pasa si el pago falla?
- ¿Qué pasa si el servicio se cae después de procesar pero antes de publicar evento?
- ¿Qué pasa si procesas el mismo evento dos veces?
- ¿Qué pasa si los eventos llegan fuera de orden?

Es trabajo extra. Pero necesario.

**El flujo completo está distribuido.**
No hay un solo archivo que diga "esto es lo que pasa cuando creas una orden". Está repartido entre Order Service, Payment Service, Notification Service...

Necesitas **buenos diagramas de secuencia**. Documentación actualizada. O vas a estar perdido.

**Compensaciones pueden fallar.**
Esto es lo que me quita el sueño: ¿Qué pasa si cobramos el pago, pero el evento PaymentProcessed se pierde? ¿Qué pasa si intentamos hacer refund y el gateway está caído?

En casos extremos, necesitas **intervención manual**. Necesitas monitoreo activo. Necesitas alertas.

No es mágico. Requiere operación consciente.

## Alternativas consideradas

### Saga Orchestration (con orquestador central)

Tendrías un Order Saga Manager que coordina todo:

```
OrderSagaManager:
  1. Crear orden
  2. Llamar a Payment Service
  3. Si pago OK → confirmar orden
  4. Si pago FAIL → cancelar orden
```

**Ventajas:**
- Un solo lugar que muestra TODO el flujo
- Más fácil de entender
- Mejor visibilidad del estado

**Por qué NO:**
- El orquestador se convierte en punto único de fallo
- Acoplamiento: el orquestador conoce todos los servicios
- El orquestador se vuelve un "monolito lógico"
- Va contra la filosofía de microservicios autónomos

Si tuviéramos un flujo MUY complejo (20 pasos, lógica condicional compleja), consideraría Orchestration. Para nuestro caso, Choreography es suficiente y más resiliente.

### Two-Phase Commit (2PC)

El clásico protocolo distribuido de transacciones.

**Por qué NO:**
- Requiere locks distribuidos (performance horrible)
- Bloquea recursos durante TODA la transacción
- No escala
- Kafka no lo soporta nativamente
- Es de los 90s, no es apropiado para sistemas modernos de alta disponibilidad

En teoría da consistencia fuerte. En práctica, mata performance y disponibilidad.

### Eventual Consistency sin compensaciones

Solo dejamos que los eventos propaguen y confiamos en que eventualmente todo se arregla.

**Por qué NO:**
- Si el pago falla, qué hacemos con la orden? Se queda en PENDING forever?
- No hay forma de deshacer cambios
- Dejaríamos el sistema en estados inconsistentes
- No cumple requerimientos de negocio (no podemos cobrar sin entregar producto)

No es una opción real para un sistema de pagos.

## Referencias

Estos son los recursos que me ayudaron a entender esto:

- [Microservices Patterns - Chris Richardson](https://microservices.io/patterns/data/saga.html) - El artículo definitivo sobre Sagas
- [Saga Pattern - Caitie McCaffrey](https://www.youtube.com/watch?v=xDuwrtwYHu8) - Excelente talk
- [Managing Data in Microservices - Randy Shoup](https://www.infoq.com/presentations/microservices-data-centric/) - Perspectiva práctica
- [Resilience4j Documentation](https://resilience4j.readme.io/) - La librería que vamos a usar
- [Building Event-Driven Microservices - Adam Bellemare](https://www.oreilly.com/library/view/building-event-driven-microservices/9781492057888/) - Lectura obligatoria

## Notas de implementación

### Schema de eventos de compensación

Cuando algo falla y necesitamos compensar:

```json
{
  "eventId": "uuid-v4",
  "eventType": "OrderCancelled",
  "aggregateId": "order-12345",
  "timestamp": "2025-01-19T10:30:00Z",
  "payload": {
    "orderId": "order-12345",
    "reason": "PAYMENT_FAILED",
    "originalEventId": "event-que-triggereo-esto",
    "compensationDetails": {
      "refundAmount": 150.00,
      "itemsToRelease": [...]
    }
  },
  "metadata": {
    "correlationId": "uuid",
    "causationId": "payment-failed-event-id",
    "isCompensation": true,  // Flag importante!
    "compensatesEventId": "original-order-created-id"
  }
}
```

El flag `isCompensation: true` es útil para monitoring. Quieres saber cuántas compensaciones estás haciendo.

### Configuración Resilience4j

```yaml
resilience4j:
  circuitbreaker:
    instances:
      paymentGateway:
        registerHealthIndicator: true
        slidingWindowSize: 10          # Ventana de 10 requests
        minimumNumberOfCalls: 5         # Mínimo 5 calls antes de abrir
        failureRateThreshold: 50        # Si 50% fallan, abrir circuito
        waitDurationInOpenState: 10s    # Esperar 10s antes de intentar cerrar
        permittedNumberOfCallsInHalfOpenState: 3
        automaticTransitionFromOpenToHalfOpenEnabled: true

  retry:
    instances:
      paymentGateway:
        maxAttempts: 3
        waitDuration: 1s
        enableExponentialBackoff: true
        exponentialBackoffMultiplier: 2
        retryExceptions:
          - java.net.SocketTimeoutException
          - org.springframework.web.client.ResourceAccessException
        ignoreExceptions:
          - com.quind.payment.InsufficientFundsException  # Error de negocio, no reintentar
```

### Monitoreo clave

**Métricas que DEBES monitorear:**

1. **Tasa de eventos en DLQ** (debe ser < 1% en producción)
2. **Tiempo promedio de saga completa** (crear orden → pago confirmado)
3. **Tasa de compensaciones** (cuántas órdenes se cancelan por errores)
4. **Circuit breaker status** (cuánto tiempo pasa en OPEN)
5. **Consumer lag en Kafka** (debe estar cerca de 0 en steady state)

**Alertas que debes configurar:**

- DLQ rate > 5% → Alert P2
- DLQ rate > 20% → Alert P1 (algo está muy mal)
- Circuit breaker abierto > 5 min → Alert P2
- Consumer lag > 10,000 mensajes → Alert P1
- Compensations > 10% de órdenes → Alert P2 (indica problema sistémico)

### Una reflexión final

Implementar Sagas y manejo robusto de errores es DIFÍCIL. Va a tomar tiempo. Van a haber bugs que no anticipaste. Van a haber edge cases raros.

Pero la alternativa es peor: un sistema distribuido sin estrategia de manejo de errores es una bomba de tiempo. Funciona bien en dev, en staging... y explota en producción con carga real.

Dale tiempo al equipo. Invierte en tests de integración que simulen fallos. Haz chaos engineering (mata servicios aleatoriamente y verifica que el sistema se recupera).

Y sobre todo: **monitorea proactivamente**. No esperes a que usuarios reporten problemas. Tus métricas y alertas deben avisarte antes.

Esto es complejo, pero es el precio de hacer sistemas distribuidos bien hechos.
