# Diseño del Modelo de Dominio - Domain-Driven Design

## Bounded Contexts

Bien, después de varias conversaciones sobre DDD y peleas mentales con el diseño, terminamos con tres contextos acotados principales. Y créanme, no fue trivial llegar aquí. Me tomó varios intentos de modelado antes de estar convencido de que esta separación tenía sentido.

### 1. Orders Context
Este es el corazón del sistema. Toda la gestión del ciclo de vida de órdenes vive aquí. Desde que el usuario hace clic en "Comprar" hasta que recibe su paquete. Es el contexto más complejo y el que más va a cambiar con el tiempo.

Al principio estaba tentado de meter todo aquí - pagos, notificaciones, inventario, lo que sea. Pero eso hubiera sido un error. El contexto de Orders debe preocuparse solo de UNA cosa: gestionar el ciclo de vida de una orden. Nada más.

### 2. Payments Context
Todo lo relacionado con dinero vive aquí. Procesamiento de pagos, transacciones, reembolsos. Lo mantuvimos separado de Orders porque la lógica de pagos es suficientemente compleja como para merecer su propio contexto. Y porque probablemente vamos a querer cambiar de gateway de pagos en el futuro sin tocar el core de órdenes.

He visto proyectos donde mezclan pagos con órdenes y después sufren cuando quieren agregar un segundo método de pago, o cambiar de Stripe a otro gateway. Esta separación nos salva de ese dolor.

### 3. Notifications Context
El más simple de los tres. Su único trabajo es escuchar eventos y notificar al usuario. No tiene decisiones de negocio complejas. No afecta el flujo principal. Si se cae, el sistema sigue funcionando (aunque los usuarios no reciben emails, pero la orden se procesa igual).

Esto es importante: Notifications NO bloquea el flujo crítico. Es un servicio "fire and forget" desde la perspectiva del negocio. Si SendGrid está caído, la orden se procesa igual. Las notificaciones se reintentarán después.

---

## Orders Context

### Agregado: Order (Raíz de Agregado)

Este es nuestro agregado principal. Y cuando digo agregado, me refiero a la unidad de consistencia. Todo lo que esté dentro de Order debe mantenerse consistente en una sola transacción. Lo que esté fuera, usamos eventos.

**Invariantes:**

Estas son las reglas que NUNCA se pueden romper. Si alguna se viola, lanzamos excepción. No hay negociación.

- Una orden debe tener al menos un item (obvio, pero lo digo explícitamente)
- El monto total debe ser mayor a cero (no regalamos productos)
- Las transiciones de estado deben seguir la máquina de estados definida (ver ADR-003)
- Solo se puede cancelar una orden en estados PENDING o CONFIRMED (después ya pagaste, necesitas refund)
- Los items no pueden modificarse después de PAYMENT_PROCESSING (coherencia: lo que pagaste es lo que compraste)

**Atributos:**

| Atributo | Tipo | Descripción | Obligatorio |
|----------|------|-------------|-------------|
| id | OrderId | Identificador único de la orden | Sí |
| customerId | CustomerId | Identificador del cliente | Sí |
| items | List<OrderItem> | Lista de items de la orden | Sí (min 1) |
| totalAmount | Money | Monto total de la orden | Sí |
| status | OrderStatus | Estado actual de la orden | Sí |
| shippingAddress | Address | Dirección de envío | Sí |
| createdAt | Instant | Timestamp de creación | Sí |
| updatedAt | Instant | Timestamp de última actualización | Sí |
| createdBy | String | Usuario que creó la orden | No |

**Comportamientos:**
- `confirm()`: PENDING → CONFIRMED
- `markAsPaymentProcessing()`: CONFIRMED → PAYMENT_PROCESSING
- `markAsPaid(paymentId)`: PAYMENT_PROCESSING → PAID
- `markAsShipped()`: PAID → SHIPPED
- `markAsDelivered()`: SHIPPED → DELIVERED
- `cancel(reason)`: PENDING/CONFIRMED → CANCELLED
- `markAsFailed(reason)`: Cualquier → FAILED
- `validate()`: Validar invariantes del agregado

---

### Entidad: OrderItem

Entidad dentro del agregado Order. No existe fuera de una orden. Esto es importante: OrderItem NO es un agregado por sí solo. No tiene ID global. Vive y muere con su Order.

Al principio consideré hacer OrderItem un agregado separado, pero eso hubiera complicado todo innecesariamente. Un item no tiene sentido sin su orden padre. No hay caso de uso donde necesites "obtener un item" sin saber a qué orden pertenece.

**Atributos:**

| Atributo | Tipo | Descripción | Obligatorio |
|----------|------|-------------|-------------|
| productId | ProductId | Identificador del producto | Sí |
| productName | String | Nombre del producto | Sí |
| quantity | Quantity | Cantidad de items | Sí |
| unitPrice | Money | Precio unitario | Sí |
| subtotal | Money | Subtotal (quantity * unitPrice) | Sí |

**Reglas:**
- quantity debe ser mayor a 0
- unitPrice debe ser mayor a 0
- subtotal = quantity * unitPrice

---

## Value Objects

Estos son los ladrillos inmutables de nuestro dominio. Una vez creados, no cambian. Quieres un valor diferente? Creas un nuevo objeto. Esto nos da thread-safety gratis y hace el código más predecible.

He visto equipos que usan Strings o primitivos por todos lados y después sufren con validaciones duplicadas, bugs sutiles de comparación, y código que nadie entiende. Los Value Objects previenen todo eso.

### OrderId
| Atributo | Tipo | Descripción |
|----------|------|-------------|
| value | String (UUID) | Identificador único |

### CustomerId
| Atributo | Tipo | Descripción |
|----------|------|-------------|
| value | String (UUID) | Identificador único del cliente |

### ProductId
| Atributo | Tipo | Descripción |
|----------|------|-------------|
| value | String (UUID) | Identificador único del producto |

### Money
| Atributo | Tipo | Descripción |
|----------|------|-------------|
| amount | BigDecimal | Cantidad monetaria (2 decimales) |
| currency | Currency | Moneda (USD, EUR, COP) |

**Reglas:**
- amount debe tener máximo 2 decimales
- amount no puede ser negativo
- Operaciones solo entre misma currency

**Por qué Money es un Value Object y no un primitivo:**

He aprendido esto de la manera difícil. Usar `BigDecimal` directamente lleva a bugs como:
- Sumar USD + EUR sin darte cuenta
- Olvidar setear el scale (decimales) y tener inconsistencias
- No validar que el monto sea positivo

Money encapsula TODAS estas validaciones. Una vez que tienes un objeto Money, sabes que es válido. Confías en él. No necesitas validar en cada uso.

### Quantity
| Atributo | Tipo | Descripción |
|----------|------|-------------|
| value | Integer | Cantidad de items |

**Reglas:**
- value debe ser mayor a 0

### Address
| Atributo | Tipo | Descripción |
|----------|------|-------------|
| street | String | Calle y número |
| city | String | Ciudad |
| state | String | Estado/Departamento |
| zipCode | String | Código postal |
| country | String | País |

### Email
| Atributo | Tipo | Descripción |
|----------|------|-------------|
| value | String | Dirección de correo electrónico |

**Reglas:**
- Debe cumplir formato válido de email
- Se almacena en minúsculas

### Currency (Enum)
Valores posibles:
- `USD`: US Dollar ($)
- `EUR`: Euro (€)
- `COP`: Colombian Peso ($)

### OrderStatus (Enum)

Este enum es crítico. Define TODAS las transiciones válidas de estado. Lo diseñamos junto con la máquina de estados (ver order-state-machine-diagram.md) y con la estrategia de Sagas (ver ADR-003).

Valores posibles y transiciones válidas:

```
PENDING → CONFIRMED
PENDING → CANCELLED

CONFIRMED → PAYMENT_PROCESSING
CONFIRMED → CANCELLED

PAYMENT_PROCESSING → PAID
PAYMENT_PROCESSING → FAILED

PAID → SHIPPED

SHIPPED → DELIVERED

CANCELLED (terminal)
FAILED (terminal)
DELIVERED (terminal)
```

Nota importante: Los estados terminales (CANCELLED, FAILED, DELIVERED) no tienen transiciones de salida. Una vez ahí, no hay vuelta atrás. Si necesitas "reabrir" una orden cancelada, en realidad creas una NUEVA orden. Esto mantiene la auditoría limpia y evita estados inválidos.

**Estados:**
- `PENDING`: Orden recién creada, esperando confirmación
- `CONFIRMED`: Orden confirmada, inventario reservado
- `PAYMENT_PROCESSING`: Pago en proceso
- `PAID`: Pago completado exitosamente
- `SHIPPED`: Orden enviada al cliente
- `DELIVERED`: Orden entregada
- `CANCELLED`: Orden cancelada
- `FAILED`: Error irrecuperable en el procesamiento

---

## Payments Context

Este contexto es interesante porque es completamente reactivo. Payment Service NO tiene endpoints públicos para crear pagos. Solo escucha el evento `OrderCreated` y automáticamente inicia el procesamiento.

Por qué? Porque los pagos son una consecuencia de las órdenes, no una acción independiente. El usuario no "crea un pago". El usuario "crea una orden" y el sistema se encarga de procesar el pago.

### Agregado: Payment

**Atributos:**

| Atributo | Tipo | Descripción | Obligatorio |
|----------|------|-------------|-------------|
| id | PaymentId | Identificador único del pago | Sí |
| orderId | OrderId | Orden asociada | Sí |
| amount | Money | Monto a pagar | Sí |
| paymentMethod | PaymentMethod | Método de pago usado | Sí |
| status | PaymentStatus | Estado del pago | Sí |
| transactionId | String | ID de transacción del gateway | No |
| processedAt | Instant | Timestamp de procesamiento | No |
| failureReason | String | Razón de fallo si aplica | No |
| createdAt | Instant | Timestamp de creación | Sí |

**Comportamientos:**
- `markAsSuccessful(transactionId)`: PROCESSING → COMPLETED
- `markAsFailed(reason)`: PROCESSING → FAILED
- `retry()`: FAILED → PROCESSING

---

### Value Objects - Payments

### PaymentId
| Atributo | Tipo | Descripción |
|----------|------|-------------|
| value | String (UUID) | Identificador único del pago |

### PaymentMethod (Enum)
- `CREDIT_CARD`
- `DEBIT_CARD`
- `BANK_TRANSFER`
- `DIGITAL_WALLET`

### PaymentStatus (Enum)
- `PENDING`: Pago pendiente de procesar
- `PROCESSING`: Procesando con gateway
- `COMPLETED`: Pago exitoso
- `FAILED`: Pago fallido
- `REFUNDED`: Pago revertido

---

## Notifications Context

### Agregado: Notification

**Atributos:**

| Atributo | Tipo | Descripción | Obligatorio |
|----------|------|-------------|-------------|
| id | NotificationId | Identificador único | Sí |
| orderId | OrderId | Orden relacionada | Sí |
| recipient | Email | Email del destinatario | Sí |
| type | NotificationType | Tipo de notificación | Sí |
| subject | String | Asunto del mensaje | Sí |
| content | String | Contenido del mensaje | Sí |
| status | NotificationStatus | Estado de la notificación | Sí |
| sentAt | Instant | Timestamp de envío | No |
| createdAt | Instant | Timestamp de creación | Sí |

**Comportamientos:**
- `markAsSent()`: PENDING → SENT
- `markAsFailed(reason)`: PENDING → FAILED

---

### Value Objects - Notifications

### NotificationId
| Atributo | Tipo | Descripción |
|----------|------|-------------|
| value | String (UUID) | Identificador único |

### NotificationType (Enum)
- `ORDER_CREATED`: Orden creada
- `ORDER_CONFIRMED`: Orden confirmada
- `PAYMENT_PROCESSED`: Pago procesado
- `PAYMENT_FAILED`: Pago fallido
- `ORDER_SHIPPED`: Orden enviada
- `ORDER_DELIVERED`: Orden entregada
- `ORDER_CANCELLED`: Orden cancelada

### NotificationStatus (Enum)
- `PENDING`: Pendiente de envío
- `SENT`: Enviada exitosamente
- `FAILED`: Fallo en envío

---

## Domain Events

Aquí está la magia de event-driven architecture. Los eventos son nuestra forma de comunicación entre bounded contexts. Son inmutables, representan hechos que YA pasaron, y son la fuente de verdad del sistema.

Un detalle importante: los eventos usan tiempo pasado. "OrderCreated", no "CreateOrder". Por qué? Porque representan algo que YA ocurrió. No es una instrucción, es un hecho histórico.

Todos los eventos de dominio comparten estructura base (como lo definimos en ADR-002):

### Estructura Base de Evento

| Atributo | Tipo | Descripción |
|----------|------|-------------|
| eventId | String (UUID) | ID único del evento |
| eventType | String | Tipo de evento |
| aggregateId | String | ID del agregado afectado |
| timestamp | Instant | Cuándo ocurrió el evento |
| version | String | Versión del schema |
| payload | Object | Datos específicos del evento |
| metadata | EventMetadata | Metadata de trazabilidad |

### EventMetadata

| Atributo | Tipo | Descripción |
|----------|------|-------------|
| correlationId | String (UUID) | ID para trazar request completo |
| causationId | String | ID del evento que causó este |
| userId | String | Usuario que inició la acción |
| source | String | Servicio que publicó el evento |
| isCompensation | Boolean | Si es evento de compensación |

**La importancia de correlationId:**

Esto es ORO para debugging. Cuando un usuario reporta "mi orden no llegó", buscas por correlationId y ves TODA la cadena de eventos: OrderCreated → PaymentProcessed → OrderPaid → OrderShipped. Es como tener una grabación completa de lo que pasó.

Sin esto, debugging en sistemas distribuidos es un infierno. Con esto, es manejable.

---

### OrderCreatedEvent

**Payload:**

| Atributo | Tipo |
|----------|------|
| orderId | String |
| customerId | String |
| totalAmount | BigDecimal |
| currency | String |
| items | List<OrderItemData> |
| shippingAddress | AddressData |

### OrderConfirmedEvent

**Payload:**

| Atributo | Tipo |
|----------|------|
| orderId | String |
| confirmedAt | Instant |

### OrderCancelledEvent

**Payload:**

| Atributo | Tipo |
|----------|------|
| orderId | String |
| reason | String |
| cancelledAt | Instant |

### OrderPaidEvent

**Payload:**

| Atributo | Tipo |
|----------|------|
| orderId | String |
| paymentId | String |
| amount | BigDecimal |
| currency | String |
| paidAt | Instant |

### OrderShippedEvent

**Payload:**

| Atributo | Tipo |
|----------|------|
| orderId | String |
| trackingNumber | String |
| shippedAt | Instant |

### OrderDeliveredEvent

**Payload:**

| Atributo | Tipo |
|----------|------|
| orderId | String |
| deliveredAt | Instant |

### OrderFailedEvent

**Payload:**

| Atributo | Tipo |
|----------|------|
| orderId | String |
| reason | String |
| failedAt | Instant |

---

### PaymentProcessedEvent

**Payload:**

| Atributo | Tipo |
|----------|------|
| paymentId | String |
| orderId | String |
| transactionId | String |
| amount | BigDecimal |
| currency | String |
| processedAt | Instant |

### PaymentFailedEvent

**Payload:**

| Atributo | Tipo |
|----------|------|
| paymentId | String |
| orderId | String |
| reason | String |
| failureType | String (BUSINESS/TECHNICAL) |
| failedAt | Instant |

---

### CustomerNotifiedEvent

**Payload:**

| Atributo | Tipo |
|----------|------|
| notificationId | String |
| orderId | String |
| recipient | String |
| type | String |
| sentAt | Instant |

---

## Excepciones de Dominio

Estas son excepciones de NEGOCIO, no técnicas. Representan violaciones de reglas de dominio. Son checked exceptions porque queremos que los casos de uso las manejen explícitamente.

No confundir con excepciones técnicas (NullPointerException, SQLException, etc). Esas son bugs y van al DLQ. Estas son errores esperados de negocio.

### InvalidOrderStateException
Se lanza cuando se intenta una transición de estado inválida.

Ejemplo: intentar marcar como SHIPPED una orden que está en PENDING. Eso no es un bug, es una violación de reglas de negocio. El código que llama necesita manejar esto.

### InvalidOrderException
Se lanza cuando una orden no cumple con sus invariantes.

Ejemplo: intentar crear una orden sin items, o con monto total negativo. El agregado Order valida esto en su constructor y métodos.

### OrderNotFoundException
Se lanza cuando no se encuentra una orden por su ID.

Esto podría ser un 404 en el API, o podría indicar un problema de sincronización entre servicios. Depende del contexto.

### InvalidPaymentStateException
Se lanza cuando se intenta una transición de estado inválida en Payment.

Similar a InvalidOrderStateException pero para el agregado Payment.

### PaymentNotFoundException
Se lanza cuando no se encuentra un pago por su ID.

---

## Resumen del Modelo

**Agregados identificados:**
- Order (Orders Context)
- Payment (Payments Context)
- Notification (Notifications Context)

**Entidades:**
- Order (raíz)
- OrderItem (dentro de Order)
- Payment (raíz)
- Notification (raíz)

**Value Objects principales:**
- OrderId, PaymentId, NotificationId, CustomerId, ProductId
- Money (con Currency)
- Quantity
- Address
- Email
- OrderStatus, PaymentStatus, NotificationStatus
- PaymentMethod, NotificationType

**Eventos de Dominio:**
- 8 eventos de órdenes
- 2 eventos de pagos
- 1 evento de notificaciones

**Principios aplicados:**
- Agregados protegen invariantes de negocio
- Value Objects son inmutables
- Estados y transiciones explícitas
- Eventos comunican cambios entre contextos
- Separación clara de responsabilidades por contexto

---

## Reflexiones finales sobre el modelo

Este modelo es el resultado de varias iteraciones. La primera versión tenía Orders y Payments mezclados en un solo agregado gigante. Fue un error. La segunda versión tenía demasiados agregados pequeños (OrderItem como agregado propio, Address como agregado). También fue un error.

Esta tercera versión es el balance correcto. Tres bounded contexts claros, agregados que protegen sus invariantes, value objects que encapsulan validaciones, y eventos que comunican cambios.

La clave fue preguntarnos constantemente: "Qué necesita ser consistente en una sola transacción?" Todo lo que responde "sí" va en el mismo agregado. Todo lo que puede ser eventualmente consistente, se comunica vía eventos.

Y sobre todo: este modelo NO es perfecto. Va a evolucionar. Vamos a descubrir edge cases que no anticipamos. Vamos a necesitar agregar atributos, cambiar validaciones, tal vez agregar nuevos eventos. Eso está bien. Un modelo de dominio es un organismo vivo, no algo tallado en piedra.

Lo importante es que tenemos una base sólida, con principios claros, que nos permite evolucionar sin romper todo.
