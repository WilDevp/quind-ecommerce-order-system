# Sistema de Gestión de Órdenes E-commerce

Sistema de gestión de órdenes para e-commerce utilizando arquitectura orientada a eventos, programación reactiva y clean architecture.

## Descripción

Sistema distribuido basado en microservicios diseñado para manejar el procesamiento de órdenes de compra en un entorno e-commerce de alto tráfico. Implementa Event-Driven Architecture con Kafka, programación reactiva con Spring WebFlux, y sigue los principios de Clean Architecture y Domain-Driven Design.

## Arquitectura

El sistema está compuesto por tres microservicios principales:

- **Order Service** (puerto 8080): Gestión de órdenes y orquestación del flujo de negocio
- **Payment Service** (puerto 8081): Procesamiento de pagos
- **Notification Service** (puerto 8082): Envío de notificaciones a clientes

## Stack Tecnológico

- **Java 17**
- **Spring Boot 3.x**
- **Spring WebFlux** (Programación Reactiva)
- **Apache Kafka** (Message Broker)
- **PostgreSQL** con R2DBC (Base de datos transaccional)
- **MongoDB Reactive** (Event Store y auditoría)
- **Docker & Docker Compose**
- **Gradle** (Build tool)

## Patrones Implementados

- Clean Architecture / Hexagonal Architecture
- Domain-Driven Design (DDD)
- CQRS (Command Query Responsibility Segregation)
- Event Sourcing
- Saga Pattern (Transacciones distribuidas)
- Repository Pattern

## Prerequisitos

- Java 17 o superior
- Docker y Docker Compose
- Gradle 8.x o superior
- IntelliJ IDEA (recomendado)

## Inicio Rápido

_(En construcción)_

```bash
# Clonar el repositorio
git clone git@github.com:WilDevp/quind-ecommerce-order-system.git
cd quind-ecommerce-order-system

# Levantar infraestructura
docker-compose up -d

# Compilar y ejecutar tests
./gradlew clean build

# Ejecutar servicios
./gradlew bootRun
```

## Documentación

La documentación completa del proyecto se encuentra en la carpeta `docs/`:

- [Architecture Decision Records (ADRs)](docs/ADRs/)
- [Diagramas de Arquitectura](docs/architecture/)
- [Especificaciones de APIs](docs/api/)

## Estado del Proyecto

🚧 **En Desarrollo** - Prueba Técnica para Líder Técnico Java

## Autor

**Wilmar Garcia**

## Licencia

Este proyecto es parte de una prueba técnica.
