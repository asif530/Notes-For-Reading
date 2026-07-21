# Hexagonal Architecture (Ports and Adapters)

Hexagonal Architecture is one of the most misunderstood architectural patterns. Many developers think it is about package structure or using interfaces everywhere. It isn't.

The core idea is:

> **Business logic should not depend on frameworks, databases, messaging systems, or external services. Everything outside the business should depend on the business.**

It is also known as **Ports and Adapters Architecture**, introduced by Alistair Cockburn.

---

# The Problem It Solves

Consider a typical Spring Boot application.

```text
Controller
    ↓
Service
    ↓
Repository
    ↓
PostgreSQL
```

Now suppose your service also calls RabbitMQ.

```text
Controller
      ↓
Service
  ↙       ↘
Repository  RabbitMQ
```

Later it grows.

```text
Controller
      ↓
Service
  ↙   ↓    ↘
DB  Kafka  Redis
      ↓
REST API
```

Eventually your business logic becomes tightly coupled to Spring Data JPA, RabbitMQ, REST clients, Redis, etc.

Changing infrastructure becomes difficult.

---

# The Main Idea

Instead of putting infrastructure at the center,

put **business logic** at the center.

Everything else becomes replaceable.

```text
          Database

REST API          RabbitMQ

       Business Logic

Email         Redis

CLI            Scheduler
```

The application should work regardless of how users interact with it or where data is stored.

---

# Why "Hexagonal"?

The hexagon has no special meaning.

It simply illustrates that the application can have **multiple input and output sides**, rather than the traditional layered stack.

```text
          REST

     CLI       Kafka

 DB     Business     Email

    Scheduler   gRPC
```

Every side is equal.

---

# Core Terminology

There are four concepts to understand:

1. Domain
2. Ports
3. Adapters
4. Infrastructure

---

# 1. Domain

The domain contains business rules.

It knows nothing about:

* Spring
* JPA
* PostgreSQL
* RabbitMQ
* REST
* Docker

Example:

```java
public class Order {

    public void cancel() {
        // business rule
    }
}
```

Notice there are no Spring annotations.

No JPA annotations.

No framework dependency.

---

# 2. Ports

A Port is simply an interface.

It defines what the business needs.

There are two kinds.

## Incoming Port

"What operations can someone perform on my application?"

Example

```java
public interface CreateOrderUseCase {

    Order create(CreateOrderCommand command);

}
```

The business exposes this interface.

Controllers call it.

---

## Outgoing Port

"What external capability does my business require?"

Example

```java
public interface OrderRepository {

    Order save(Order order);

}
```

Notice

The business does NOT say

```java
JpaRepository
```

It simply says

"I need something that can save orders."

---

# 3. Adapters

Adapters implement ports.

They translate between the outside world and the domain.

There are two types.

---

## Primary (Driving) Adapter

These drive the application.

Examples

* REST Controller
* GraphQL
* gRPC
* Scheduler
* CLI

Example

```text
HTTP Request

↓

REST Controller

↓

CreateOrderUseCase
```

The controller is merely an adapter.

---

## Secondary (Driven) Adapter

These are used by the business.

Examples

* PostgreSQL
* RabbitMQ
* Redis
* External REST API

Example

```text
CreateOrderService

↓

OrderRepository

↓

JpaOrderRepository
```

The service knows only the interface.

---

# 4. Infrastructure

Infrastructure implements the adapters.

Example

```java
@Repository
public class JpaOrderRepository
implements OrderRepository {

}
```

Notice

Spring lives here.

JPA lives here.

Hibernate lives here.

The domain never imports them.

---

# Complete Flow

Suppose someone creates an order.

```text
Client

↓

REST Controller

↓

CreateOrderUseCase

↓

CreateOrderService

↓

OrderRepository (Port)

↓

JpaOrderRepository (Adapter)

↓

PostgreSQL
```

The business never knows PostgreSQL exists.

---

# Package Structure

A common project layout is:

```text
src
└── main
    └── java
        └── com.example.order

            domain
            ├── model
            ├── service
            └── port
                ├── in
                └── out

            application
            └── service

            adapter
            ├── in
            │     ├── rest
            │     ├── grpc
            │     └── scheduler
            │
            └── out
                  ├── persistence
                  ├── rabbitmq
                  └── external

            config
```

Many projects merge `domain` and `application`, but the important principle is keeping business logic independent of infrastructure.

---

# Why Are Ports Useful?

Suppose today you use PostgreSQL.

Tomorrow you migrate to MongoDB.

Without Hexagonal:

```text
Service

↓

Spring Data JPA
```

You must modify business code.

With Hexagonal:

```text
Service

↓

OrderRepository
```

Only this changes:

```text
JpaOrderRepository

↓

MongoOrderRepository
```

The business remains unchanged.

---

# Another Example

Today

```text
REST Controller

↓

Business
```

Tomorrow

```text
Kafka Consumer

↓

Business
```

No business code changes.

Both simply invoke the same incoming port.

---

# Relation to Spring Boot

Spring Boot is **not** your architecture.

Spring Boot is just an implementation framework.

In Hexagonal Architecture:

```text
Spring Boot

↓

Adapter

↓

Business
```

The framework stays outside.

The domain remains framework-independent.

---

# Benefits

* Business logic is isolated.
* Infrastructure is replaceable.
* Easier unit testing.
* Better separation of concerns.
* Supports multiple entry points (REST, CLI, messaging, schedulers).
* Supports multiple persistence technologies.
* Easier long-term maintenance.

---

# Drawbacks

* More interfaces.
* More classes.
* Higher initial complexity.
* Can feel like over-engineering for very small projects.

---

# Common Misconceptions

## Misconception 1

"Hexagonal Architecture means using interfaces everywhere."

False.

Only create interfaces where they represent a meaningful boundary.

---

## Misconception 2

"Every service needs Hexagonal Architecture."

False.

A CRUD application may not benefit enough to justify the additional complexity.

---

## Misconception 3

"Hexagonal Architecture is just package structure."

False.

Package names are irrelevant.

The dependency direction is what matters.

---

# Dependency Rule

This is the single most important rule.

Dependencies always point **toward the business**.

Correct:

```text
Controller
      ↓
Use Case
      ↓
Repository Port
      ↑
Jpa Repository
```

Incorrect:

```text
Business

↓

Spring Data Repository
```

The business should never depend directly on infrastructure.

---

# Interview Summary

**Hexagonal Architecture (Ports and Adapters)** organizes an application so that the **business logic is at the center**, independent of frameworks and infrastructure. The business exposes **incoming ports** (use cases) and depends on **outgoing ports** (required capabilities). Infrastructure components such as REST controllers, databases, message brokers, and external APIs are implemented as **adapters**. This makes the system easier to test, maintain, and evolve because infrastructure can change without affecting the core business logic.


# In larger systems these sometimes move to application.port.out — an equally valid variant. What must never happen is an output port living inside an adapter package;
that would let the adapter own the contract instead of the core. 

---


Mental Model
This is **the** point where almost everyone gets confused. The names *Port In*, *Port Out*, *Adapter In*, *Adapter Out* sound similar because they are named from the **application's perspective**, not from the user's or the network's perspective.

Once you change your perspective, it becomes much simpler.

---

# Think of your application as a castle

Imagine your **business logic** is inside a castle.

Everything outside the castle wants to either:

* **Come into the castle**
* **Go outside the castle**

```
                Outside World

 REST      CLI      Scheduler

        ↓      ↓      ↓

      [ Castle Gate ]

        Business Logic

      [ Castle Gate ]

 Database RabbitMQ Redis
```

The gates are called **Ports**.

The people standing at the gates are called **Adapters**.

---

# Step 1: What is a Port?

A **Port is just an interface.**

It defines communication.

It contains **no implementation**.

Think:

> "What does the business expose?"

or

> "What does the business require?"

---

# There are only TWO Ports

## Incoming Port

Question:

> **"What operations can somebody perform on my application?"**

Example:

```java
public interface CreateProductUseCase {

    Product create(CreateProductCommand command);

}
```

Notice

This is something **the business offers**.

Controllers call this interface.

---

## Outgoing Port

Question:

> **"What does my business need from the outside world?"**

Example

```java
public interface ProductRepository {

    Product save(Product product);

}
```

The business says

"I don't care if it's PostgreSQL."

"I just need someone who can save products."

---

# Step 2: What is an Adapter?

Adapters implement communication.

They connect the outside world to the ports.

---

## Incoming Adapter

Incoming Adapter receives something from outside.

Examples

```
REST Controller

Kafka Consumer

GraphQL

CLI

Scheduler
```

Example

```
HTTP Request

↓

REST Controller

↓

CreateProductUseCase
```

The REST controller is adapting HTTP into a Java method call.

---

## Outgoing Adapter

Outgoing Adapter talks to external systems.

Examples

```
JPA

Redis

RabbitMQ

Stripe

SMTP

AWS S3
```

Example

```
CreateProductService

↓

ProductRepository

↓

JpaProductRepository
```

JpaProductRepository is adapting your interface into SQL.

---

# Why is it called "Incoming"?

Because something is entering the business.

```
Client

↓

Controller

↓

Business
```

Traffic is coming **into** the application.

---

# Why is it called "Outgoing"?

Because the business wants something external.

```
Business

↓

Repository

↓

Database
```

Traffic leaves the business.

---

# Here's the trick

Don't think

> "Incoming to REST"

Think

> "Incoming to Business."

Everything is named relative to the **business logic**.

---

# Complete Example

Suppose someone creates a product.

```
Browser

↓

POST /products

↓

ProductController

↓

CreateProductUseCase

↓

CreateProductService

↓

ProductRepository

↓

JpaProductRepository

↓

PostgreSQL
```

Now let's label everything.

---

### Browser

Outside world.

---

### ProductController

Incoming Adapter.

Why?

Because it converts HTTP into Java.

---

### CreateProductUseCase

Incoming Port.

Why?

Because it defines what operations the business exposes.

---

### CreateProductService

Application / Business.

Contains use case implementation.

---

### ProductRepository

Outgoing Port.

Why?

Because the business needs persistence.

---

### JpaProductRepository

Outgoing Adapter.

Why?

Because it implements ProductRepository using JPA.

---

### PostgreSQL

Infrastructure.

---

# Another Example with RabbitMQ

```
Business

↓

EventPublisher

↓

RabbitMQPublisher

↓

RabbitMQ
```

Label them.

```
EventPublisher
```

Outgoing Port.

```
RabbitMQPublisher
```

Outgoing Adapter.

---

# Redis Example

```
Business

↓

ProductCache

↓

RedisProductCache

↓

Redis
```

Again

```
ProductCache
```

Outgoing Port.

```
RedisProductCache
```

Outgoing Adapter.

---

# One Rule Solves Everything

Ask this question:

## "Who owns this interface?"

If the business owns it

↓

It's a **Port**.

If infrastructure implements it

↓

It's an **Adapter**.

---

# Another Rule

Ask

> "Who depends on whom?"

Correct dependency

```
Controller

↓

Use Case

↓

Repository Interface

↑

Jpa Repository
```

Notice

Business never depends on JPA.

JPA depends on the interface.

---

# Memory Trick

## Incoming Port

```
Business says

"You may ask me to do these things."
```

Examples

```
Create Product

Delete Product

Update Product
```

---

## Outgoing Port

```
Business says

"I need these capabilities."
```

Examples

```
Save Product

Publish Event

Cache Product

Send Email
```

---

## Incoming Adapter

```
Someone knocks on the door.

Controller opens it.
```

Examples

```
REST

gRPC

Kafka Consumer

CLI
```

---

## Outgoing Adapter

```
Business needs help outside.

Infrastructure performs it.
```

Examples

```
JPA

Redis

RabbitMQ

SMTP

S3
```

---

# The Simplest Mental Model

```
                    Outside World

 REST
 CLI
 Scheduler
 Kafka Consumer
        │
        ▼
=============================
 Incoming Adapters
=============================
        │
        ▼
=============================
 Incoming Ports
 (Use Cases)
=============================
        │
        ▼
=============================
 Business / Application
=============================
        │
        ▼
=============================
 Outgoing Ports
=============================
        │
        ▼
=============================
 Outgoing Adapters
=============================
        │
        ▼
 PostgreSQL
 Redis
 RabbitMQ
 SMTP
 S3
```

Everything above the business **brings requests in**.

Everything below the business **fulfills requests by communicating outward**.

---

## The sentence I use to remember Hexagonal Architecture

> **Incoming Adapters translate external requests into calls to Incoming Ports. The business executes the use case. When it needs something external, it calls an Outgoing Port, whose implementation is provided by an Outgoing Adapter.**

Once that sentence clicks, the entire terminology becomes much easier to navigate.

---------------

# **A requests wants in to perform a business. So There will be an incoming adapter, an incoming port to access business . 
# Business needs to perform operation. It defines them through port-out. So an outgoing adapter has an implementation of outgoing port.**

Yes. Your understanding is **correct**. In fact, you're 90% there. I would just refine the wording slightly because **the business doesn't "define operations through Port-Out"; it defines the external capabilities it needs.**

Here's a simpler mental model.

---

# Imagine the Business as a Person

The business is sitting in a room.

There are only **two directions**.

## 1. Someone wants something from the business

```
User
   │
   ▼
Incoming Adapter
   │
   ▼
Incoming Port
   │
   ▼
Business
```

The user says:

> "Create a Product."

The REST Controller (Incoming Adapter) translates HTTP into Java.

The Incoming Port says:

> "Yes, the business supports creating a product."

The Business performs the work.

---

## 2. The business needs help from the outside

```
Business
   │
   ▼
Outgoing Port
   │
   ▼
Outgoing Adapter
   │
   ▼
Database / Redis / RabbitMQ
```

The business says:

> "I need someone to save this Product."

It doesn't know **how**.

It simply calls the Outgoing Port.

The Outgoing Adapter knows how to talk to PostgreSQL, Redis, RabbitMQ, etc.

---

# The Simplest Story

Think of it as a restaurant.

```
Customer
   │
   ▼
Waiter
   │
   ▼
Kitchen
   │
   ▼
Supplier
```

Now map it.

| Restaurant Example | Hexagonal Architecture |
| ------------------ | ---------------------- |
| Customer           | Client                 |
| Waiter             | Incoming Adapter       |
| Menu               | Incoming Port          |
| Kitchen            | Business               |
| Purchase Request   | Outgoing Port          |
| Supplier           | Outgoing Adapter       |

The **menu** defines what the kitchen offers—that's an **Incoming Port**.

The **purchase request** defines what the kitchen needs—that's an **Outgoing Port**.

The kitchen doesn't know which supplier will fulfill the request.

---

# Even Simpler Rule

Forget "Port" and "Adapter" for a moment.

Just ask two questions:

### Question 1

**Who is asking the business to do something?**

```
Outside
   │
   ▼
Adapter In
   │
   ▼
Port In
   │
   ▼
Business
```

---

### Question 2

**Who is helping the business do something?**

```
Business
   │
   ▼
Port Out
   │
   ▼
Adapter Out
   │
   ▼
Outside
```

---

# One Sentence to Remember

> **Incoming = Someone wants something from my business. Outgoing = My business needs something from someone else.**

That's all Hexagonal Architecture is trying to model.

---

# My preferred naming (less confusing)

I actually think the official names cause unnecessary confusion. If I were teaching a team, I'd first introduce these conceptual names:

| Official Name        | Easier Mental Name                                                   |
|----------------------|----------------------------------------------------------------------|
| Incoming Port        | **Business API** (what the business offers)                          |
| Incoming Adapter     | **Request Translator** (HTTP, gRPC, CLI → Business API)              |
| Business/Application | **Business Logic**                                                   |
| Outgoing Port        | **Business Dependency** (what the business needs)                    |
| Outgoing Adapter     | **Infrastructure Implementation** (JPA, Redis, RabbitMQ, SMTP, etc.) |

Once this mental model is established, the official terminology becomes much more intuitive.

------



# What "owning the contract" means

An interface's package location signals who gets to define and change it. 
In Java, the package a class lives in is typically also the module/layer that "owns" its design decisions — the team or layer responsible for that package 
decides what the interface looks like, and everyone else has to conform to it.

So the question "where does ProductRepositoryPort live?" is really the question "who decides what this interface looks like: the business logic, or the database technology?"

The two scenarios

Scenario A — port lives in domain (or application):

domain.port.out.ProductRepositoryPort   ← interface, defined by the core                                                                                                          
adapter.out.persistence.ProductPersistenceAdapter implements ProductRepositoryPort

The core says: "I need something that can save(Product) and findById(id)." It defines that contract in terms it cares about — Product, id, plain Java. 
The persistence adapter then has to conform to that shape, no matter what database sits behind it. If Postgres has quirks, optimistic locking, 
JPA-specific exceptions — none of that leaks into the interface, because the adapter doesn't get to design the interface, it only implements it.

Scenario B — port lives in the adapter package:  

adapter.out.persistence.ProductRepositoryPort   ← interface, defined by the persistence layer                                                                                     
application.service.ProductService depends on it   

Now flip the question: who decided what ProductRepositoryPort looks like? The persistence package did — because that's where the file lives, 
that's the team/layer that owns it, and that's what gets edited when persistence needs change. The interface will drift toward persistence's convenience over time:
maybe it starts returning Optional<ProductJpaEntity> instead of Optional<Product> because "that's what the JPA repository already gives us,
" or it grows a flush() method because "the adapter needs it," or
an exception type specific to JPA creeps into the signature.

ProductService (in application) now has to import adapter.out.persistence.ProductRepositoryPort — an import pointing outward, from the core to the infrastructure. 
That's the dependency arrow reversed. Even though it's "just an interface," the interface's shape is dictated by the adapter, so the core is still constrained 
by infrastructure concerns — dependency inversion hasn't actually happened, only the implementation is swapped out; 
the contract itself is infrastructure-flavored.


Why this isn't just pedantry

It shows up concretely:

- Testing — if the port's method signatures leak JPA/Redis/AMQP types (because the adapter package "owns" the interface and reaches for what's convenient), your unit test for    
  ProductService now needs those types on the classpath, or awkward conversions to avoid them. The whole point of ports (see 03-dependency-rules.md) was to test business           
  orchestration with zero framework dependencies.
- Swappability — if you replace Postgres with MongoDB, and the port lived in adapter.out.persistence, the new Mongo adapter would either have to keep implementing an interface   
  that still lives in the old adapter's package (weird), or you'd delete the interface along with the old adapter and have to redefine it — meaning application code changes too,   
  since its import path (adapter.out.persistence.ProductRepositoryPort) no longer exists. The core should never need to change because you swapped a database.
- Who breaks whom — with the port in the core, an adapter change that violates the contract is a compile error in the adapter. With the port in the adapter, 
  a "contract change" is just a normal edit to that adapter's own file — nothing stops it from silently reshaping the interface to fit whatever the database driver
  wants, and every core class that depends on it absorbs that shift without any signal that a boundary was crossed.

The one-line version

The package a port lives in declares who is allowed to change it without asking permission. Put it in domain/application and the core dictates terms to 
infrastructure. Put it in adapter.* and infrastructure dictates terms to the core — which is exactly the coupling Hexagonal Architecture exists to eliminate. 