// ============================================
// MongoDB Initialization Script
// ============================================
//
// Este script se ejecuta automáticamente cuando el container de MongoDB
// inicia por primera vez (gracias al volumen /docker-entrypoint-initdb.d).
//
// Propósito:
// - Crear collections para Event Store y Notifications
// - Crear índices para queries eficientes
// - Configurar validación de schema (opcional pero recomendado)
// - Crear usuarios con permisos específicos (opcional para producción)
//
// Notas:
// - MongoDB ejecuta scripts .js automáticamente en orden alfabético
// - Este script es idempotent (se puede ejecutar múltiples veces)
// - La variable 'db' está disponible automáticamente (apunta a MONGO_INITDB_DATABASE)
//
// ============================================

// Conectar a la base de datos especificada en MONGO_INITDB_DATABASE
// (definida en docker-compose.yml, default: ecommerce_events)
db = db.getSiblingDB('ecommerce_events');

print('==========================================');
print('Initializing MongoDB for E-commerce System');
print('Database: ' + db.getName());
print('==========================================');

// ============================================
// Collection: events (Event Store)
// ============================================
// Esta collection almacena TODOS los eventos del sistema.
// Es append-only: eventos nunca se borran ni modifican (inmutables).
//
// Diseño:
// - Cada documento es un evento (OrderCreatedEvent, PaymentProcessedEvent, etc)
// - eventId es único (garantiza no duplicados)
// - aggregateId permite buscar todos los eventos de una orden
// - correlationId permite tracing de transacciones distribuidas

print('\n[1/3] Creating events collection...');

// Crear collection si no existe
db.createCollection('events');

// Crear índices
// IMPORTANTE: Los índices son críticos para performance en MongoDB
// Sin índices, queries hacen collection scan (lento en colecciones grandes)

// Índice único en eventId (previene eventos duplicados)
db.events.createIndex(
    { "eventId": 1 },
    {
        unique: true,
        name: "idx_events_eventId"
    }
);
print('  ✓ Created unique index on eventId');

// Índice compound en aggregateId + version
// Para event sourcing: "Dame todos los eventos de order-123 hasta version 5"
db.events.createIndex(
    { "aggregateId": 1, "version": 1 },
    { name: "idx_events_aggregateId_version" }
);
print('  ✓ Created compound index on aggregateId + version');

// Índice en correlationId
// Para tracing: "Muéstrame todos los eventos de esta transacción"
// Este índice es ORO para debugging
db.events.createIndex(
    { "correlationId": 1 },
    { name: "idx_events_correlationId" }
);
print('  ✓ Created index on correlationId');

// Índice compound en eventType + occurredAt
// Para queries temporales: "Todos los PaymentProcessed en las últimas 24h"
db.events.createIndex(
    { "eventType": 1, "occurredAt": -1 },
    { name: "idx_events_eventType_occurredAt" }
);
print('  ✓ Created compound index on eventType + occurredAt');

// Índice en occurredAt (para queries por fecha)
db.events.createIndex(
    { "occurredAt": -1 },
    { name: "idx_events_occurredAt" }
);
print('  ✓ Created index on occurredAt');

// Schema validation with moderate level and warn action
// MongoDB allows validating document structure with JSON Schema
// This helps maintain data quality while being flexible during development
//
// validationLevel: "moderate" - Only validates INSERTs and UPDATEs to valid docs (ignores existing invalid docs)
// validationAction: "warn" - Logs warnings instead of rejecting writes (safe for development/production)
//
// Why "warn" instead of "error"?
// - Allows system to keep running even if schema evolves
// - Gives visibility into schema violations without blocking operations
// - You can monitor logs and fix issues proactively
// - Change to "error" in production once schema is stable

db.runCommand({
    collMod: "events",
    validator: {
        $jsonSchema: {
            bsonType: "object",
            required: ["eventId", "eventType", "aggregateId", "aggregateType", "correlationId", "occurredAt", "payload"],
            properties: {
                eventId: {
                    bsonType: "string",
                    description: "UUID del evento - required"
                },
                eventType: {
                    bsonType: "string",
                    description: "Tipo de evento (OrderCreatedEvent, etc) - required"
                },
                aggregateId: {
                    bsonType: "string",
                    description: "ID del agregado (order-123, payment-456) - required"
                },
                aggregateType: {
                    bsonType: "string",
                    description: "Tipo de agregado (Order, Payment) - required"
                },
                correlationId: {
                    bsonType: "string",
                    description: "UUID para tracing - required"
                },
                causationId: {
                    bsonType: "string",
                    description: "Evento que causó este evento"
                },
                occurredAt: {
                    bsonType: "date",
                    description: "Timestamp del evento - required"
                },
                version: {
                    bsonType: "int",
                    description: "Version del agregado"
                },
                payload: {
                    bsonType: "object",
                    description: "Datos específicos del evento - required"
                },
                metadata: {
                    bsonType: "object",
                    description: "Metadata técnico"
                }
            }
        }
    },
    validationLevel: "moderate",  // Only validate inserts and updates to valid documents
    validationAction: "warn"      // Log warnings, don't reject writes
});
print('  ✓ Applied schema validation (level: moderate, action: warn)');

// Write concern configuration for replica sets
// Write concern controls acknowledgment behavior for write operations
//
// IMPORTANT: This config is for REPLICA SETS. Single node ignores it.
// If you're running single MongoDB node (like in docker-compose), this is informational only.
//
// In production with replica sets:
// - w: "majority" - Wait for write to be acknowledged by majority of nodes
// - j: true - Wait for write to be written to journal (survives crashes)
// - wtimeout: 5000 - Timeout if write can't be acknowledged in 5 seconds
//
// Trade-off: Slower writes, but guaranteed durability and consistency
// For event sourcing, this is CRITICAL - You can't lose events!

// Note: Can't set default write concern on collection level, only on database or client level
// Services should configure this in their MongoDB connection string:
// mongodb://host:port/db?w=majority&journal=true&wtimeoutMS=5000
print('  ℹ️  Note: Configure write concern in services connection string for replica sets');
print('     Example: ?w=majority&journal=true&wtimeoutMS=5000');

print('✓ Events collection initialized successfully');

// ============================================
// Collection: notifications
// ============================================
// Esta collection almacena el historial de notificaciones enviadas.
//
// Diseño:
// - Cada documento es una notificación (email, SMS, push)
// - notificationId es único
// - orderId permite buscar todas las notificaciones de una orden
// - TTL index auto-borra notificaciones después de 90 días

print('\n[2/3] Creating notifications collection...');

db.createCollection('notifications');

// Índice único en notificationId
db.notifications.createIndex(
    { "notificationId": 1 },
    {
        unique: true,
        name: "idx_notifications_notificationId"
    }
);
print('  ✓ Created unique index on notificationId');

// Índice en orderId (FK lógico a Order)
db.notifications.createIndex(
    { "orderId": 1 },
    { name: "idx_notifications_orderId" }
);
print('  ✓ Created index on orderId');

// Índice compound en customerId + createdAt
// Para queries: "Todas las notificaciones del cliente X, ordenadas por fecha"
db.notifications.createIndex(
    { "customerId": 1, "createdAt": -1 },
    { name: "idx_notifications_customerId_createdAt" }
);
print('  ✓ Created compound index on customerId + createdAt');

// Índice en status (para queries: "notificaciones FAILED que requieren retry")
db.notifications.createIndex(
    { "status": 1 },
    { name: "idx_notifications_status" }
);
print('  ✓ Created index on status');

// TTL Index en createdAt
// MongoDB automáticamente borra documentos después de expireAfterSeconds
// 7776000 segundos = 90 días
// Esto previene que la collection crezca indefinidamente
db.notifications.createIndex(
    { "createdAt": 1 },
    {
        expireAfterSeconds: 7776000,
        name: "idx_notifications_ttl"
    }
);
print('  ✓ Created TTL index (90 days expiration)');

print('✓ Notifications collection initialized successfully');

// ============================================
// Crear usuarios con permisos específicos (Opcional)
// ============================================
// En producción, crea usuarios separados con permisos mínimos:
// - event_store_writer: solo INSERT en events
// - notification_service: solo READ/WRITE en notifications
// - analytics_user: solo READ en events (para reportes)
//
// Por ahora usamos el usuario root definido en docker-compose.yml

print('\n[3/3] Configuring permissions...');

// Ejemplo de cómo crear usuarios (comentado)
/*
db.createUser({
    user: "event_store_writer",
    pwd: "secure_password_here",
    roles: [
        {
            role: "readWrite",
            db: "ecommerce_events",
            collection: "events"
        }
    ]
});

db.createUser({
    user: "notification_service",
    pwd: "secure_password_here",
    roles: [
        {
            role: "readWrite",
            db: "ecommerce_events",
            collection: "notifications"
        }
    ]
});

print('  ✓ Created service-specific users');
*/

print('  → Using root user (acceptable for development)');
print('  → For production: Create service-specific users with minimal permissions');

// ============================================
// Verificación final
// ============================================

print('\n==========================================');
print('MongoDB Initialization Summary:');
print('==========================================');

// Listar collections creadas
var collections = db.getCollectionNames();
print('\nCollections created:');
collections.forEach(function(coll) {
    print('  - ' + coll);
});

// Listar índices de events
print('\nIndexes on events collection:');
var eventIndexes = db.events.getIndexes();
eventIndexes.forEach(function(idx) {
    print('  - ' + idx.name + ': ' + JSON.stringify(idx.key));
});

// Listar índices de notifications
print('\nIndexes on notifications collection:');
var notifIndexes = db.notifications.getIndexes();
notifIndexes.forEach(function(idx) {
    print('  - ' + idx.name + ': ' + JSON.stringify(idx.key));
});

print('\n==========================================');
print('✓ MongoDB initialization completed successfully!');
print('==========================================');

// ============================================
// Datos de prueba (Opcional)
// ============================================
// Insertar algunos eventos de ejemplo para testing
// Comentado por defecto - descomentar si quieres datos iniciales

/*
print('\n[Optional] Inserting sample data...');

// Evento de ejemplo: OrderCreatedEvent
db.events.insertOne({
    eventId: "550e8400-e29b-41d4-a716-446655440000",
    eventType: "OrderCreatedEvent",
    aggregateId: "order-123",
    aggregateType: "Order",
    correlationId: "corr-abc-123",
    causationId: null,
    occurredAt: new Date(),
    version: 1,
    payload: {
        customerId: "customer-456",
        totalAmount: 150000,
        currency: "COP",
        items: [
            {
                productId: "product-789",
                productName: "Laptop Dell XPS 13",
                quantity: 1,
                unitPrice: 150000
            }
        ]
    },
    metadata: {
        userId: "user-123",
        source: "order-service",
        version: "1.0.0",
        environment: "development"
    }
});

print('  ✓ Inserted sample OrderCreatedEvent');

// Notificación de ejemplo
db.notifications.insertOne({
    notificationId: "notif-550e8400-e29b-41d4-a716-446655440001",
    orderId: "order-123",
    customerId: "customer-456",
    type: "ORDER_CREATED",
    channel: "EMAIL",
    recipient: "customer@example.com",
    subject: "Orden Recibida - E-commerce",
    body: "Tu orden #123 ha sido recibida exitosamente.",
    status: "SENT",
    sentAt: new Date(),
    failureReason: null,
    metadata: {
        correlationId: "corr-abc-123",
        emailProvider: "sendgrid",
        messageId: "sendgrid-msg-abc123"
    },
    createdAt: new Date()
});

print('  ✓ Inserted sample notification');
print('✓ Sample data inserted successfully');
*/
