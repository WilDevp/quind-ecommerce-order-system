# ADR-001: Adopción de Clean Architecture

## Estado
**DRAFT** - 2025-01-19

## Contexto

He estado dándole vueltas a cómo estructurar este sistema de órdenes e-commerce, y la verdad es que necesitamos algo que nos deje respirar a largo plazo. He visto demasiados proyectos que empiezan bien y a los seis meses ya nadie quiere tocar el código porque todo está amarrado con alambres.

Lo que necesitamos resolver aquí no es trivial:

**Primero, la mantenibilidad.** En mi experiencia, los sistemas que mezclan la lógica de negocio con detalles de infraestructura terminan siendo pesadillas de mantener. Un día necesitas cambiar algo en cómo procesas pagos y terminas tocando controllers, services, repositories... es un efecto dominó que nadie quiere.

**Segundo, el testing.** Sinceramente, si no podemos testear la lógica de negocio sin levantar todo Spring, bases de datos, Kafka y el ejército completo, ya perdimos. Los tests que tardan minutos en correr son tests que nadie va a ejecutar. Y tests que nadie ejecuta son tests que no existen.

**Tercero, la independencia de frameworks.** Esto me dolió aprender: Spring es genial, pero la lógica de negocio no debería DEPENDER de Spring. ¿Qué pasa si en dos años necesitamos migrar a otra versión? ¿O si queremos extraer una parte del sistema a una función serverless? Si la lógica de negocio conoce `@Autowired`, `@Transactional`, y todo el ecosistema Spring, estamos amarrados.

El problema real que estoy viendo es que los equipos típicamente hacen esto:

```java
@RestController
public class OrderController {
    @Autowired
    private OrderRepository repository; // Ya perdimos - negocio mezclado con persistencia

    @PostMapping("/orders")
    public Order createOrder(@RequestBody OrderDTO dto) {
        // Lógica de negocio mezclada con HTTP
        Order order = new Order();
        order.setStatus("PENDING");
        // ... validaciones mezcladas con detalles técnicos
        return repository.save(order);
    }
}
```

Esto funciona al principio. Pero cuando el sistema crece, cuando necesitas agregar validaciones complejas, cuando múltiples canales (web, mobile, batch jobs) necesitan crear órdenes... ahí se pone feo.

## Decisión

Decidimos ir con **Clean Architecture** (también la conocen como Hexagonal o Ports & Adapters - básicamente variaciones del mismo concepto).

¿Por qué? Porque después de haberla aplicado en proyectos anteriores, he visto que funciona. No es perfecta, tiene sus costos (ya llegaré a eso), pero los beneficios valen la pena para un sistema como este.

### Cómo vamos a estructurarlo

Pensemos en capas concéntricas, como una cebolla (sí, sé que suena cursi, pero la analogía funciona):

```
service/
├── domain/                    # El corazón - lo más importante
│   ├── model/                # Entidades puras: Order, Payment, Customer
│   ├── event/                # Eventos de dominio: OrderCreated, PaymentProcessed
│   ├── port/                 # Contratos/interfaces que el dominio necesita
│   └── exception/            # Excepciones de negocio: InsufficientStockException
│
├── application/              # Los casos de uso - lo que el sistema HACE
│   ├── command/              # CreateOrderCommand, CancelOrderCommand
│   ├── query/                # GetOrderByIdQuery, ListOrdersQuery
│   ├── service/              # Orquestadores de casos de uso
│   └── dto/                  # Objetos para transferir datos entre capas
│
└── infrastructure/           # Los detalles técnicos - lo que cambia
    ├── adapter/
    │   ├── in/               # Cómo nos hablan (REST, mensajes, etc.)
    │   │   └── web/
    │   └── out/              # Cómo hablamos con el mundo (BD, Kafka, APIs)
    │       ├── persistence/
    │       └── messaging/
    └── config/               # Configuración de Spring, beans, etc.
```

### Las reglas del juego

Aquí viene lo importante - y lo que hace que esto funcione:

1. **El dominio es el rey.** No depende de NADA. Ni Spring, ni Kafka, ni PostgreSQL. Nada. Es Java puro. Esto al principio se siente raro, pero créanme, es liberador.

2. **La aplicación orquesta.** Los casos de uso viven aquí. Dependen del dominio, pero no conocen detalles de infraestructura. No saben si guardamos en Postgres o MongoDB. No les importa.

3. **La infraestructura implementa.** Aquí vive Spring, Kafka, JPA, todo eso. Esta capa implementa las interfaces (puertos) que el dominio definió.

La clave está en la **inversión de dependencias**. El dominio define: "Necesito un OrderRepository que haga X, Y, Z" (una interfaz), pero no le importa CÓMO se implementa. Luego, en infraestructura, creamos `JpaOrderRepository` que implementa esa interfaz usando Spring Data JPA.

```java
// En domain/port/
public interface OrderRepository {
    Order save(Order order);
    Optional<Order> findById(OrderId id);
}

// En infrastructure/adapter/out/persistence/
@Repository
public class JpaOrderRepository implements OrderRepository {
    @Autowired
    private OrderJpaRepository springRepo; // Esto SÍ conoce Spring

    @Override
    public Order save(Order order) {
        OrderEntity entity = mapper.toEntity(order);
        OrderEntity saved = springRepo.save(entity);
        return mapper.toDomain(saved);
    }
}
```

¿Ven la magia? El dominio no conoce Spring. Spring conoce al dominio.

## Consecuencias

Vamos a ser honestos sobre lo que esto implica.

### Lo bueno (y por qué vale la pena)

**Testing que realmente funciona.**
Esto para mí es el ganador más grande. Puedes testear la lógica de negocio con tests unitarios puros, sin `@SpringBootTest`, sin testcontainers, sin nada. Son instantáneos. Los ejecutas mil veces mientras desarrollas y no te quitan tiempo.

He visto equipos que odian escribir tests porque tardan una eternidad en correr. Con esta arquitectura, eso desaparece.

**Cambios localizados.**
Cuando necesitemos cambiar de PostgreSQL a MySQL (o cuando el proyecto de al lado te obligue a usar Oracle porque "política de empresa"), solo tocas la capa de infraestructura. Tu lógica de negocio ni se entera.

Lo mismo con Kafka. Si mañana alguien decide que quiere RabbitMQ, o SQS, o lo que sea, no reescribimos todo. Cambias el adaptador.

**Código que se entiende.**
Nuevos desarrolladores llegan al proyecto y en dos días ya entienden dónde va cada cosa. No hay sorpresas. La estructura es predecible.

Y cuando tienes múltiples microservicios (como tenemos nosotros: orders, payments, notifications), todos se ven igual. Eso es oro.

**Refactorización sin miedo.**
Como las dependencias apuntan hacia adentro, puedes mejorar la infraestructura sin miedo a romper el negocio. Y viceversa: puedes cambiar reglas de negocio sin tocar controllers o repositories.

### Lo no tan bueno (hay que reconocerlo)

**Más código, más archivos, más carpetas.**
No voy a mentir: esto crea más archivos. Necesitas interfaces, implementaciones, DTOs, mappers... En un CRUD simple de 5 tablas, puede sentirse como overkill.

Pero este sistema NO es un CRUD simple. Tenemos eventos, sagas, flujos complejos. Aquí vale la pena.

**Curva de aprendizaje.**
Si vienes de MVC tradicional (Controller → Service → Repository), esto es diferente. Te va a tomar unos días acostumbrarte. Vas a querer poner `@Autowired` en el dominio (no lo hagas). Vas a querer saltarte capas (tampoco lo hagas).

Requiere disciplina. Pero una vez que lo internalizas, es natural.

**Mapeo entre capas.**
Vas a estar mapeando: Domain Model → DTO → Entity → DTO → Request/Response. Sí, es boilerplate. MapStruct ayuda muchísimo aquí (lo vamos a usar), pero sigue siendo código extra.

El trade-off es: prefieres escribir mappers o prefieres que tu lógica de negocio esté contaminada con anotaciones JPA y Jackson? Yo prefiero los mappers.

**Puede ser over-engineering para cosas triviales.**
Si estás haciendo un endpoint que solo lee una tabla y la devuelve, toda esta infraestructura se siente pesada. Es verdad.

Pero estamos construyendo un sistema event-driven con múltiples servicios, sagas, compensaciones... No es trivial. Aquí lo necesitamos.

## Alternativas que consideré (y por qué no)

### Arquitectura en capas tradicional (Layered)

La típica: `Controller → Service → Repository → Database`

La descartamos porque he visto cómo envejece este patrón. Después de unos meses:
- Los controllers empiezan a tener lógica de negocio
- Los services se vuelven God Objects con 3000 líneas
- Todo depende de todo, cambiar algo da miedo
- Los tests requieren levantar Spring completo

No. Pasemos de eso.

### Onion Architecture

Es casi lo mismo que Clean Architecture, honestamente. Conceptualmente son hermanas.

No la elegimos porque Clean Architecture tiene mejor documentación, más ejemplos en la comunidad Java/Spring, y es el término que más gente conoce. Pero si dijeras "vamos con Onion", no pelearía. Son casi idénticas.

### MVC tradicional con Spring Boot

```java
@Controller → @Service → @Repository
```

Es lo más fácil, lo que todo el mundo conoce. Pero tiene los mismos problemas que Layered Architecture:
- La lógica de negocio se contamina con `@Transactional`, `@Cacheable`, `@Async`...
- Los tests son lentos (necesitas Spring Context)
- Cambiar tecnología es doloroso
- No hay separación clara entre lo que importa (negocio) y los detalles (frameworks)

Para un proyecto pequeño que va a vivir 6 meses, tal vez vale la pena. Para esto que estamos construyendo, no.

## Referencias

Estos son los materiales que consulté y que recomiendo si quieres profundizar:

- [The Clean Architecture - Robert C. Martin](https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html) - El artículo original de Uncle Bob. Léelo.
- [Hexagonal Architecture - Alistair Cockburn](https://alistair.cockburn.us/hexagonal-architecture/) - El origen de Ports & Adapters
- [Get Your Hands Dirty on Clean Architecture - Tom Hombergs](https://reflectoring.io/book/) - Libro práctico con ejemplos en Spring Boot. Muy recomendado.
- [DDD, Hexagonal, Onion, Clean, CQRS - Herberto Graça](https://herbertograca.com/2017/11/16/explicit-architecture-01-ddd-hexagonal-onion-clean-cqrs-how-i-put-it-all-together/) - Explica cómo todos estos patrones se relacionan

## Notas de implementación

Algunas decisiones prácticas de cómo vamos a implementar esto:

**MapStruct para los mappers.** Escribir mappers a mano es tedioso y propenso a errores. MapStruct genera el código en tiempo de compilación. Rápido y confiable.

**Estructura de paquetes consistente.** Los tres microservicios (orders, payments, notifications) van a seguir la MISMA estructura. Cuando saltes de uno a otro, todo va a estar donde lo esperas.

**Interfaces en `domain.port`.** Todos los puertos (contratos que el dominio necesita) viven en `domain.port.in` (para casos de uso) y `domain.port.out` (para dependencias externas como repos).

**Adaptadores por tipo.** Los adaptadores de entrada (`adapter.in`) agrupan REST controllers, consumers de Kafka, scheduled jobs. Los de salida (`adapter.out`) agrupan repositorios, producers de Kafka, clientes HTTP externos.

Una cosa más: al principio va a sentirse raro tener tanto código para algo "simple". Resiste la tentación de saltarte las capas o mezclar responsabilidades. Dale unas semanas. Una vez que te acostumbras, no quieres volver atrás.
