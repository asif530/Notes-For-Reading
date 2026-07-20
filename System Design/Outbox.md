# Outbox Pattern

## What is the Outbox Pattern?

The **Outbox Pattern** is a **data consistency pattern** used to reliably publish events **after a database transaction succeeds**, 
without using distributed transactions (2PC). **It is a transactional pattern.** 

It solves:

> **“How do I update my database AND publish an event without losing data?”**

---

## The Problem It Solves

### ❌ Without Outbox

```
1. Save Order to DB   ✅
2. Publish event      ❌ (Kafka down)
```

→ **Data inconsistency**

* DB updated
* Event never sent
* Downstream services are unaware

---

## The Core Idea

> **Persist the event in the same database transaction as your business data.**

---

## How it works (Step-by-Step)

```
BEGIN TRANSACTION
  Insert Order
  Insert Outbox_Event
COMMIT
```

Then:

```
Outbox Poller / CDC
   |
   v
Message Broker (Kafka / RabbitMQ)
```

---

## Flow Diagram

```
[ Application ]
     |
     v
-----------------------------------------
|[ Business Table ]   [ Outbox Table ]   |
|     |                    |             |
|    |---- SAME TX -------|              |
-----------------------------------------
                           |
                           v
                    Outbox Processor
                          |
                          v
                  Kafka / RabbitMQ
```

---

## Outbox Table Example

```sql
CREATE TABLE outbox_event (
  id UUID PRIMARY KEY,
  aggregate_type VARCHAR(50),
  aggregate_id VARCHAR(50),
  event_type VARCHAR(50),
  payload JSONB,
  status VARCHAR(20),
  created_at TIMESTAMP
);
```

---

## Publishing Strategies

### 1️. Polling Publisher

* Background job polls `outbox_event`
* Publishes events
* Marks them as SENT

✅ Simple
❌ Polling overhead

---

### 2️. CDC (Change Data Capture) – **Preferred**

* Use **Debezium**
* Reads DB WAL / binlog
* Streams changes to Kafka

✅ Near real-time
✅ No polling
❌ More infra

---

## Guarantees

* **At-least-once delivery**
* Consumers must be **idempotent**

---

## Spring Boot Context (Important for You)

* Transactional boundary with `@Transactional`
* JPA + Kafka / RabbitMQ
* No XA / 2PC
* Common in **event-driven microservices**

---

## Advantages

✅ Strong consistency between DB and events
✅ No distributed transactions
✅ Failure-resilient

---

## Drawbacks

❌ Extra table
❌ Eventual consistency
❌ Idempotency required

---

## When to use Outbox?

✔ Event-driven architecture
✔ Kafka / RabbitMQ
✔ Microservices
✔ Avoiding 2PC

---

## Interview One-Liner

> “The Outbox Pattern ensures reliable event publishing by storing events in the same transaction as business data and asynchronously publishing them later.”

---

# 🔥 Sidecar vs Outbox (Different Concerns!)

| Aspect      | Sidecar                          | Outbox                    |
|-------------|----------------------------------|---------------------------|
| Category    | Deployment / Infra               | Data consistency          |
| Problem     | Cross-cutting concerns           | Reliable event publishing |
| Scope       | Network, security, observability | DB + messaging            |
| Consistency | N/A                              | Strong → eventual         |
| Used With   | Kubernetes, Service Mesh         | Kafka, RabbitMQ           |

---