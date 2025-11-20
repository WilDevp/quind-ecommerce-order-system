# ADR-002: Selección de Apache Kafka como Message Broker

## Estado
**DRAFT** - 2025-01-19

## Contexto

Bien, tenemos que resolver cómo van a comunicarse estos tres microservicios (orders, payments, notifications) sin que se conviertan en un nido de dependencias síncronas. Y he visto suficientes sistemas donde todo se llama vía REST y cuando un servicio se cae, se cae medio sistema.

### El problema real que estamos enfrentando

Imagínate el flujo típico de una orden:
1. Usuario hace checkout
2. Creamos la orden
3. Procesamos el pago
4. Enviamos notificaciones
5. Actualizamos inventario (eventualmente)

Si hacemos esto síncronamente (Order Service llama a Payment Service vía HTTP, que llama a Notification Service...), estamos creando una cadena de dependencias que es una bomba de tiempo. ¿Qué pasa si Notification Service está lento? El usuario espera. ¿Qué pasa si cae? La orden falla aunque el pago se procesó. Pesadilla.

### Lo que realmente necesitamos

**Desacoplamiento temporal.** Los servicios no necesitan estar todos vivos al mismo tiempo. Order Service publica un evento "OrderCreated" y sigue con su vida. Payment Service lo consume cuando puede.

**Manejo de picos.** En Black Friday o durante promociones, pueden llegar 10,000 órdenes en minutos. Necesitamos algo que aguante eso sin tirarse, que pueda encolar mensajes y procesarlos a nuestro ritmo.

**Garantías de entrega.** Si publicamos un evento "PaymentProcessed", ese evento NO puede perderse. Punto. Es dinero real. Es información crítica de negocio.

**Event Sourcing.** Aquí viene algo que me tomó tiempo apreciar: queremos guardar el historial completo de eventos. No solo el estado actual de una orden, sino TODO lo que le pasó: cuándo se creó, cuándo se confirmó, cuándo se pagó, cuándo se envió. Es oro para auditorías, debugging, y reconstruir estados.

**Replay de eventos.** ¿Qué pasa si agregamos un nuevo servicio dentro de seis meses y necesita procesar órdenes históricas? O si un bug procesó mal los eventos y necesitamos reprocesar desde ayer? Necesitamos poder "rebobinar" y reproducir eventos.

## Decisión

Vamos con **Apache Kafka**.

Sé que Kafka tiene reputación de ser complejo. Y lo es. Pero para lo que estamos construyendo, es la herramienta correcta. He usado RabbitMQ, SQS, Redis Pub/Sub... todos tienen su lugar. Para un sistema event-driven donde los eventos son nuestra fuente de verdad, Kafka es el indicado.

### Cómo lo vamos a configurar

```yaml
Kafka Cluster:
  Desarrollo:
    - 1 broker (suficiente para dev)
    - Replication factor: 1

  Producción:
    - 3 brokers mínimo (alta disponibilidad)
    - Replication factor: 3 (una copia en cada broker)
    - Min in-sync replicas: 2 (garantía de durabilidad)

Topics:
  - order-events (3 particiones)
  - payment-events (3 particiones)
  - notification-events (3 particiones)

Producer config (importante - aquí es donde garantizamos durabilidad):
  - acks: all (no confirmamos hasta que todas las réplicas escriban)
  - enable.idempotence: true (evita duplicados)
  - retries: Integer.MAX_VALUE (reintentar siempre)
  - max.in.flight.requests: 5 (balance entre throughput y orden)

Consumer config:
  - enable.auto.commit: false (control manual - más seguro)
  - isolation.level: read_committed (solo leer mensajes confirmados)
  - max.poll.records: 100 (procesar en batches)
```

### Estrategia de uso

**Event Publishing:** Cada servicio publica sus eventos de dominio. Order Service publica "OrderCreated", "OrderConfirmed", "OrderCancelled". Payment Service publica "PaymentProcessed", "PaymentFailed".

**Event Consumption:** Los servicios se suscriben a los eventos que les interesan. Payment Service consume "OrderCreated" para iniciar el pago. Notification Service consume TODO para notificar al usuario.

**Idempotencia:** Esto es crítico y lo vamos a hacer bien. Cada consumer mantiene una tabla de eventos procesados. Antes de procesar un evento, verificamos si ya lo procesamos (por el eventId). Si sí, lo ignoramos. Esto permite reintentos seguros.

**Dead Letter Queue:** Cuando un evento falla múltiples veces (digamos 5 reintentos), lo mandamos a un topic DLQ (order-events-dlq). Ahí lo revisamos manualmente. Puede ser un bug, puede ser datos inválidos, puede ser un timeout externo. Lo importante es que no bloqueamos el consumer.

## Consecuencias

Vamos a ser realistas sobre lo que Kafka nos trae.

### Lo bueno (y es muy bueno)

**Throughput brutal.**
Kafka puede manejar literalmente millones de mensajes por segundo. Nuestra carga estimada está en el orden de miles por minuto en picos. Kafka no va ni a sudar. He visto clusters manejando 2TB de eventos al día sin problemas.

La latencia típica está por debajo de 10ms. Eso es prácticamente tiempo real para lo que necesitamos.

**Durabilidad real.**
Con la configuración que propuse (acks=all, replication factor 3, min in-sync replicas 2), literalmente necesitaríamos que dos brokers se caigan AL MISMO TIEMPO para perder mensajes. Y aún así, los mensajes que ya se confirmaron estarían seguros.

Kafka escribe en disco. No es memoria volátil. Si se reinicia un broker, los mensajes siguen ahí.

**Event Sourcing nativo.**
Esto es lo que me enamoró de Kafka la primera vez que lo usé bien. Los topics de Kafka SON tu event log. No es que "puedes usarlo para event sourcing", es que ES un event log distribuido.

Puedes configurar retención por tiempo (30 días, 90 días) o infinita. Puedes procesar eventos del año pasado si quieres. Puedes crear un nuevo consumer y decirle "procesa desde el principio" y tiene todos los eventos históricos.

**Escalabilidad horizontal simple.**
¿Procesamiento lento? Agrega más consumers al consumer group. Kafka automáticamente balancea las particiones entre ellos.

¿Más throughput? Agrega más particiones al topic. Agrega más brokers al cluster.

No es que sea trivial, pero es straightforward. No hay magia negra.

**Desacoplamiento temporal completo.**
Order Service puede publicar 10,000 eventos y morir (deployment, crash, lo que sea). Cuando vuelve, no pasa nada. Payment Service los consume cuando puede. Los eventos están ahí, esperando pacientemente en Kafka.

Esto hace los deployments mucho menos estresantes. No tienes que coordinar "todos los servicios abajo al mismo tiempo".

**Ecosistema maduro.**
Spring Kafka es excelente. La integración con Spring Boot es de primera. Hay monitoring tools (Kafka Manager, Conduktor), hay métricas JMX detalladas, hay conectores para todo (Kafka Connect).

Si tienes un problema con Kafka, hay un 99% de probabilidad de que alguien ya lo tuvo y está en StackOverflow.

### Lo no tan bueno (hay que ser honestos)

**Complejidad operacional.**
Esto no es algo que instalas y olvidas. Kafka requiere operación activa. Hasta Kafka 3.x necesitas Zookeeper (otro sistema distribuido más que mantener). Desde Kafka 4.x tiene KRaft que elimina Zookeeper, pero aún así, necesitas saber qué estás haciendo.

Configurar replication, partitions, consumer groups, offset management... hay una curva de aprendizaje. He visto equipos que subestiman esto y sufren.

**Consumo de recursos.**
Kafka no es liviano. Un cluster de producción (3 brokers + Zookeeper) va a necesitar recursos decentes: RAM, disco (SSD preferible), CPU. No es que sea prohibitivo, pero es más que un RabbitMQ o un Redis.

El disco especialmente. Si configuras retención larga, vas a necesitar espacio. Pero el disco es barato comparado con perder eventos.

**Curva de aprendizaje.**
Conceptos como partitions, consumer groups, offsets, rebalancing... no son intuitivos al principio. Tendremos que sentarnos con el equipo y explicar:
- Por qué un mensaje fue al partition 2 y no al 0
- Qué significa "consumer lag"
- Por qué un consumer se quedó sin offset
- Qué es un rebalance y por qué está pasando

Dale tiempo al equipo para internalizarlo. Vale la pena, pero toma tiempo.

**No para bajo volumen.**
Si tu sistema va a manejar 100 mensajes al día, Kafka es overkill. Usa SQS o algo más simple.

Pero nosotros estamos preparando para escalar. En campañas promocionales esperamos miles de órdenes por minuto. Ahí Kafka brilla.

**Routing no es tan flexible.**
RabbitMQ tiene exchanges (fanout, topic, direct, headers) que permiten routing complejo. Kafka es más simple: tienes topics y particiones.

Para routing complejo necesitas múltiples topics o Kafka Streams. Para nuestro caso (event-driven relativamente directo), no es limitación.

## Alternativas que consideré

### RabbitMQ

Lo he usado en varios proyectos. Es sólido, más simple de configurar que Kafka, excelente para colas de trabajo tradicionales.

**Por qué no:**
- RabbitMQ no está diseñado para Event Sourcing. Los mensajes se eliminan al consumirse. Puedes configurar retención, pero no es su caso de uso natural.
- El throughput es significativamente menor. RabbitMQ puede hacer ~10,000-50,000 mensajes/seg (bien configurado). Kafka hace 100,000-1,000,000+ mensajes/seg.
- No hay replay fácil de mensajes históricos.
- Para event-driven architecture pura, Kafka es más natural.

Si estuviéramos haciendo un sistema de tareas/jobs tradicional, RabbitMQ sería perfectamente válido. Pero queremos eventos como fuente de verdad.

### AWS SQS/SNS

**Por qué NO (y esto es importante):**

Vendor lock-in. Una vez que estás en SQS/SNS, migrar fuera de AWS es doloroso. El código queda acoplado a AWS SDK. La infraestructura requiere AWS.

No hay Event Sourcing. SQS elimina mensajes al consumirse. SNS es pub/sub pero sin persistencia.

La latencia es mayor (~100ms vs ~10ms de Kafka).

El orden no está garantizado estrictamente. Incluso con FIFO queues, hay limitaciones (throughput reducido, grupos de mensajes).

Los costos escalan. Con alto volumen, SQS puede salir caro. Kafka tiene costo fijo (tus servidores).

**Cuándo SÍ usar SQS:**
- Estás 100% en AWS y no te importa lock-in
- Tu volumen es variable e impredecible
- No quieres operar infraestructura
- Event Sourcing no es importante

Para nosotros, no calza.

### Redis Pub/Sub o Streams

Redis es increíblemente rápido (~1ms latencia). Es simple. Es liviano.

**Por qué no:**

Redis Pub/Sub es fire-and-forget. Si un consumer no está escuchando cuando publicas, perdió el mensaje. Inaceptable para nosotros.

Redis Streams es mejor (tiene persistencia), pero:
- Es limitado comparado con Kafka (menos features de consumer groups, offset management)
- No está diseñado como message broker principal
- La durabilidad es menor (Kafka replica en múltiples brokers, Redis es primariamente in-memory)
- El ecosistema para messaging es mucho menor que Kafka

Redis es excelente como caché, como session store, como pub/sub para notificaciones no críticas. No como event store principal.

### Apache Pulsar

Esto es interesante. Pulsar es arquitectónicamente moderno (separación de storage y compute), tiene multi-tenancy nativo, geo-replicación mejorada.

**Por qué no (todavía):**

La adopción es menor. Kafka tiene años de ventaja. Hay más recursos, más ejemplos, más gente que lo conoce.

Spring no tiene integración tan madura con Pulsar como con Kafka.

La comunidad es más pequeña. Cuando tengas un problema raro, es más probable encontrar la solución para Kafka.

Pulsar es promisorio. Para un proyecto nuevo en 2026-2027, lo consideraría seriamente. Para hoy, con este equipo, Kafka es más seguro.

## Referencias

Estos son los materiales que me ayudaron a tomar esta decisión:

- [Apache Kafka Documentation](https://kafka.apache.org/documentation/) - La documentación oficial es excelente. Larga, pero excelente.
- [Spring for Apache Kafka](https://spring.io/projects/spring-kafka) - Referencia oficial de Spring Kafka
- [Designing Event-Driven Systems - Ben Stopford](https://www.confluent.io/designing-event-driven-systems/) - Libro gratuito de Confluent. Lectura obligatoria.
- [Kafka: The Definitive Guide - Neha Narkhede](https://www.confluent.io/resources/kafka-the-definitive-guide/) - La biblia de Kafka
- [Building Event-Driven Microservices - Adam Bellemare](https://www.oreilly.com/library/view/building-event-driven-microservices/9781492057888/) - Excelente para entender patrones event-driven

## Notas de implementación

Decisiones prácticas de cómo vamos a usar Kafka:

### Naming convention de topics

```
{domain}-events
```

Ejemplos:
- `order-events` (todos los eventos relacionados con órdenes)
- `payment-events` (todos los eventos de pagos)
- `notification-events` (eventos de notificaciones)

Simple, predecible, escalable.

### Schema de eventos (JSON)

```json
{
  "eventId": "550e8400-e29b-41d4-a716-446655440000",
  "eventType": "OrderCreated",
  "aggregateId": "order-12345",
  "timestamp": "2025-01-19T14:30:00Z",
  "version": "1.0",
  "payload": {
    "orderId": "order-12345",
    "customerId": "customer-789",
    "totalAmount": 150.00,
    "items": [...]
  },
  "metadata": {
    "correlationId": "550e8400-...",
    "causationId": "event-anterior-id",
    "userId": "user-123",
    "source": "order-service"
  }
}
```

**Por qué este schema:**
- `eventId`: UUID único para idempotencia
- `eventType`: Qué pasó (OrderCreated, PaymentProcessed, etc.)
- `aggregateId`: El ID de la entidad (order ID, payment ID)
- `timestamp`: Cuándo pasó (ISO-8601)
- `version`: Para evolución del schema
- `payload`: Los datos específicos del evento
- `metadata`: Información de trazabilidad (correlation ID es ORO para debugging)

### Estrategia de particionamiento

Key para Kafka: `aggregateId` (Order ID, Payment ID)

**Por qué:** Garantiza que todos los eventos de la misma orden van a la misma partición. Esto mantiene el orden. Si eventos de order-123 van a particiones diferentes, puedes procesar "OrderPaid" antes que "OrderCreated". Caos.

Con esta estrategia:
- Orden garantizado por agregado
- Permite paralelización (diferentes órdenes pueden procesarse en paralelo)
- Balanceo de carga natural (si tienes distribución uniforme de order IDs)

### Idempotencia (crítico)

Tabla en cada servicio:

```sql
CREATE TABLE processed_events (
    event_id VARCHAR(36) PRIMARY KEY,
    event_type VARCHAR(50) NOT NULL,
    aggregate_id VARCHAR(36),
    processed_at TIMESTAMP NOT NULL,
    INDEX idx_aggregate_id (aggregate_id),
    INDEX idx_processed_at (processed_at)
);
```

Consumer handler:

```java
@Transactional
public void handleEvent(DomainEvent event) {
    // 1. Ya lo procesé?
    if (processedEventsRepo.existsById(event.getEventId())) {
        log.info("Event {} already processed, skipping", event.getEventId());
        return; // Idempotente!
    }

    // 2. Procesar lógica de negocio
    processBusinessLogic(event);

    // 3. Marcar como procesado (en la MISMA transacción)
    processedEventsRepo.save(new ProcessedEvent(
        event.getEventId(),
        event.getEventType(),
        event.getAggregateId()
    ));

    // Si algo falla, rollback de TODO
}
```

Esto es oro. Con esto puedes reintentar eventos infinitas veces sin efectos secundarios.

### Monitoring (no negociable)

**Consumer Lag:** La métrica más importante. Cuántos mensajes está atrasado el consumer. Si crece constantemente, tienes un problema.

**Throughput por topic:** Mensajes/seg publicados y consumidos.

**Error rate:** % de mensajes que van a DLQ.

**Rebalance frequency:** Rebalances frecuentes indican problemas (consumers cayendo, processing lento).

Usa Prometheus + Grafana, o Kafka Manager, o Confluent Control Center (si tienes presupuesto).

### Una última cosa

Kafka no es plug-and-play. Va a requerir aprendizaje. Va a requerir tuning. Van a haber problemas raros con offsets o rebalances.

Pero para lo que estamos construyendo - un sistema event-driven con múltiples servicios donde los eventos son la fuente de verdad - es la herramienta correcta.

Dale tiempo al equipo para aprender. Invierte en monitoring desde el día 1. Y documentemos decisiones de configuración (por qué elegiste 3 particiones, por qué ese replication factor, etc.).

Vale la pena.
