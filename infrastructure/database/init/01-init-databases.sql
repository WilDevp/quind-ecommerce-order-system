-- ============================================
-- Script de Inicialización de PostgreSQL
-- ============================================
--
-- Este script se ejecuta automáticamente cuando el container de PostgreSQL
-- inicia por primera vez (gracias al volumen /docker-entrypoint-initdb.d).
--
-- Propósito:
-- - Crear schemas para cada servicio (Order Service, Payment Service)
-- - Crear extensiones necesarias (uuid-ossp para generar UUIDs)
-- - Configurar permisos básicos
--
-- Orden de ejecución:
-- PostgreSQL ejecuta scripts en orden alfabético. Por eso este es 01-*.sql
-- Los scripts de tablas (02-*.sql, 03-*.sql) se ejecutan después.
--
-- Notas:
-- - Este script es idempotent (se puede ejecutar múltiples veces sin problemas)
-- - En producción, usa herramientas como Flyway o Liquibase para migraciones
-- ============================================

-- Crear extensión para generar UUIDs
-- uuid-ossp es la extensión estándar de PostgreSQL para UUIDs
-- Usamos uuid_generate_v4() en nuestras tablas como default para IDs
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Verificar que la extensión se instaló correctamente
-- Esto genera un UUID de prueba
SELECT uuid_generate_v4() AS test_uuid;

-- Crear schemas separados por servicio
-- Esto no es estrictamente necesario (podríamos usar el schema 'public' default),
-- pero ayuda a organizar tablas por bounded context.
--
-- Ventajas:
-- - Separación clara de concerns (Order tiene sus tablas, Payment las suyas)
-- - Permisos granulares (podemos dar acceso a order_schema pero no payment_schema)
-- - Facilita backup/restore por servicio
--
-- Desventaja:
-- - Queries cross-schema requieren prefijo (order_schema.orders)
--
-- Para este proyecto, usamos schema único 'public' para simplicidad,
-- pero dejo esto documentado para proyectos más grandes.

-- CREATE SCHEMA IF NOT EXISTS order_schema;
-- CREATE SCHEMA IF NOT EXISTS payment_schema;

-- Para este proyecto, usamos schema 'public' (default)
-- Verificamos que existe (siempre existe en PostgreSQL)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT schema_name
        FROM information_schema.schemata
        WHERE schema_name = 'public'
    ) THEN
        CREATE SCHEMA public;
    END IF;
END
$$;

-- Grant permisos al usuario de la aplicación
-- En producción, crea usuarios separados por servicio con permisos mínimos
-- Por ejemplo:
--   order_service_user: solo acceso a orders y order_items
--   payment_service_user: solo acceso a payments

-- Por ahora usamos el usuario único 'ecommerce_user' (definido en docker-compose.yml)
-- Esto es apropiado para desarrollo, no para producción

GRANT ALL PRIVILEGES ON DATABASE ecommerce TO ecommerce_user;
GRANT ALL PRIVILEGES ON SCHEMA public TO ecommerce_user;

-- Configuración de búsqueda de schema
-- Esto permite hacer SELECT * FROM orders en lugar de SELECT * FROM public.orders
ALTER DATABASE ecommerce SET search_path TO public;

-- Logging para verificar que el script corrió
DO $$
BEGIN
    RAISE NOTICE '✓ Database initialization completed successfully';
    RAISE NOTICE '  - Extension uuid-ossp installed';
    RAISE NOTICE '  - Schema public configured';
    RAISE NOTICE '  - Permissions granted to ecommerce_user';
END
$$;
