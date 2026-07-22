Clean Architecture is an architectural style proposed by **Robert C. Martin (Uncle Bob)**.

Its primary goal is:

> **Keep business rules independent of frameworks, databases, UI, messaging systems, and other external technologies.**

If tomorrow you replace Spring Boot, PostgreSQL, RabbitMQ, or REST with something else, your business logic should require little or no change.


# Why was Clean Architecture created?

Traditional applications often evolve like this:
    Controller -> Service -> Repository -> Database
Initially this looks fine.

Over time the Service begins to depend on:

* Spring Data JPA
* Redis
* RabbitMQ
* Elasticsearch
* AWS SDK
* SMTP
* External REST APIs

Eventually the business logic becomes tightly coupled to infrastructure.
Changing infrastructure becomes expensive because business code knows too much about implementation details.

Clean Architecture addresses this problem by **reversing the dependency direction**.

---

# The Core Principle

The single most important rule is:

> **Source code dependencies always point inward.**
Everything depends on the business. The business depends on nothing outside itself.

---

# The Famous Circle Diagram

```text
+------------------------------------------------------+
| Frameworks & Drivers                                 |
| (Spring Boot, PostgreSQL, RabbitMQ, REST, Redis)     |
|                                                      |
|  +-----------------------------------------------+   |
|  | Interface Adapters                            |   |
|  | Controllers, Presenters, Repositories         |   |
|  | DTO Mappers                                   |   |
|  |                                               |   |
|  |  +----------------------------------------+   |   |
|  |  | Use Cases                              |   |   |
|  |  | Application Business Rules             |   |   |
|  |  |                                        |   |   |
|  |  |  +---------------------------------+   |   |   |
|  |  |  | Enterprise Business Rules       |   |   |   |
|  |  |  | Domain Entities                 |   |   |   |
|  |  |  +---------------------------------+   |   |   |
|  |  +----------------------------------------+   |   |
|  +-----------------------------------------------+   |
+------------------------------------------------------+
```
The center contains the most important code. The outer layers contain implementation details.

---

# Layer 1 — Entities

The innermost layer.

Contains:

* Domain models
* Business rules
* Domain behavior

Example:

```java
public class Product {

    private Long id;
    private String name;
    private BigDecimal price;

    public void applyDiscount(BigDecimal percentage) {
        ...
    }
}
```

Notice:

* No Spring
* No JPA
* No Jackson
* No framework annotations

Entities should survive technology changes.

---

# Layer 2 — Use Cases

This layer contains the application's business workflows.

Examples:

* Create Product
* Update Product
* Place Order
* Cancel Order

Example:

Create Product
    ↓   
Validate Product
    ↓
Save Product
    ↓
Publish Event

A Use Case coordinates business operations. It does **not** know whether persistence is PostgreSQL or MongoDB.

---

# Layer 3 — Interface Adapters

Adapters translate between the application and the outside world.

Examples:

Incoming:

* REST Controller
* GraphQL
* gRPC
* CLI

Outgoing:

* JPA Repository
* RabbitMQ Publisher
* Redis Cache

These adapters convert external formats into forms the use cases understand.

---

# Layer 4 — Frameworks & Drivers

This is the outermost layer.

Contains:

* Spring Boot
* Hibernate
* PostgreSQL
* RabbitMQ
* Redis
* Docker
* REST

These are implementation details.

The application should be able to replace them without changing the core business logic.

---

# Dependency Rule
Correct: Controller -> Use Case -> Repository Interface <- Jpa Repository

Notice: The JPA implementation depends on the interface. The business does not depend on JPA.

Incorrect: Use Case -> JpaRepository

Now business logic is coupled to Spring Data.

# Example Flow
A user creates a Product.
    Client -> REST Controller -> CreateProductUseCase -> ProductRepository -> JpaProductRepository -> PostgreSQL

The controller translates HTTP.
The use case executes business logic.
The repository interface expresses what the business needs.
The JPA adapter performs the database work.

---

# Package Structure

One possible structure:

```text
com.example.product

domain
├── entity
├── value-object
|
application
|   ├── usecase
|   ├── service
|   ├── port
|
adapter
|    ├── in
|    │   └── rest
|    └── out
|      ├── persistence
|      ├── messaging
|      └── cache
|
config
```
Strictly maintain maintaining the dependency rule, Packages matter less than .

---

# Relationship with Hexagonal Architecture
These two architectures are extremely similar.

Both emphasize:

* Business at the center
* Infrastructure on the outside
* Dependency inversion
* Testability

Hexagonal speaks in terms of **Ports and Adapters**.
Clean Architecture speaks in terms of **Use Cases and Interface Adapters**.

The underlying principle is the same.

---

# Hexagonal vs Clean

| Hexagonal              | Clean                         |
| ---------------------- | ----------------------------- |
| Business at the center | Business at the center        |
| Incoming Port          | Use Case Interface            |
| Outgoing Port          | Gateway/Repository Interface  |
| Incoming Adapter       | Controller                    |
| Outgoing Adapter       | Repository, Messaging Adapter |
| Infrastructure outside | Frameworks & Drivers outside  |

Many real-world projects combine ideas from both.

---

# Advantages

* Business logic is framework-independent.
* Infrastructure can change with minimal impact.
* Excellent unit testability.
* Clear separation of concerns.
* Supports multiple interfaces (REST, Kafka, CLI, gRPC).
* Easier maintenance for large systems.

---

# Disadvantages

* More classes and interfaces.
* Higher learning curve.
* Can feel verbose for small CRUD applications.
* Easy to over-engineer if applied indiscriminately.

---

# When to Use Clean Architecture

Good fit:

* Complex business domains
* Long-lived enterprise systems
* Multiple external integrations
* Large teams
* Applications expected to evolve significantly

Often unnecessary:

* Small CRUD applications
* Internal admin tools
* Short-lived prototypes
* Simple APIs with minimal business logic

---

# Clean Architecture vs Layered Architecture

| Layered                                  | Clean                              |
| ---------------------------------------- | ---------------------------------- |
| Simple and straightforward               | More structured and decoupled      |
| Business often depends on infrastructure | Infrastructure depends on business |
| Easy to start                            | Better for long-term evolution     |
| Suitable for simple CRUD                 | Better for complex domains         |
| Lower initial complexity                 | Higher initial complexity          |

---

# The One Sentence to Remember

> **Clean Architecture organizes software so that business rules remain at the center of the system, while frameworks, databases, messaging systems, 
and user interfaces remain replaceable implementation details that depend on the business—not the other way around.**
