# What is CQRS?

**CQRS (Command Query Responsibility Segregation)** means:

> **Separate the part of your application that changes data (Commands) from the part that reads data (Queries).**

Instead of one model doing everything,
    Read, Write, Update, Delete, Business Rules, Searching, Reporting, Analytics

You split it into two independent models.

```
           +-----------------+
           |     Client      |
           +-----------------+
              /          \
             /            \
      Command API      Query API
            |              |
     Write Model      Read Model
            |              |
      Write Database   Read Database
```
Notice:
* Commands never return data (except success/failure)
* Queries never modify data

---

# What problem does it solve?

Imagine you're building Amazon. Initially everything is simple.
    Customer -> Application -> PostgreSQL

The same database handles
* Add Product * Buy Product * Update Inventory * Search Products
* Recommendation * Order History * Dashboard * Analytics

Everything uses the same tables.

---

Then business grows. Now:100 developers, 10 million users , Thousands of orders/sec , Millions of searches/sec.

Suddenly...
Searching products becomes slow because purchases are updating inventory continuously.
Analytics runs huge JOINs.
Dashboard executes aggregations.
Orders lock rows.
Inventory updates happen frequently.
Everyone is fighting over the same tables.

The database is overloaded.

---

The problem is:

> **Reading and writing have completely different requirements.**

Example

Writing an order Needs

* ACID
* validation
* transactions
* consistency

Reading products Needs

* Full-text search
* Sorting
* Pagination
* Caching
* Fast response

These are completely different workloads. Yet we're forcing both onto one model.

CQRS says: **Why to use same model for different purpose ?**

Let's separate them.

---

# Story Mode

Imagine a huge library. There is only one librarian. Whenever someone comes...

Person A: "I want to borrow this book.". The librarian updates records.
Person B: "I want books written by Tolkien." The librarian searches.
Person C: "I want statistics of books borrowed this year." The librarian calculates.
Person D: "I want all books about AI.". Search again.
Person E: "I'm returning a book.". Update again.

The poor librarian keeps switching between

```
Updating
Searching
Reporting
Searching
Updating
```
Eventually. Everyone waits.
The library manager has an idea. Hire two different teams.

Team 1 -> Handles borrowing/returning. Their only job: Update records.
Team 2 -> Handles visitors. Their only job: Search books.
Borrowing is fast. Searching is fast. Nobody blocks anyone.
This is CQRS.

---

# Command Side

Command means > "Something changes."
Examples
```
Create User
Place Order
Delete Product
Update Inventory
Cancel Booking
```

Commands contain business logic.
```
Validate
Check permissions
Start transaction
Modify data
Commit
```

After success. Database changes.
---

Example
```
POST /orders
    ↓
Check inventory
    ↓
Reserve items
    ↓
Charge payment
    ↓
Save Order
    ↓
Publish OrderCreated Event
```
Notice: No data returned except maybe Order ID
---
# Query Side
Queries answer questions.

```
GET /products
GET /orders
GET /dashboard
GET /sales
GET /profile
```

They never modify anything.

Instead
```
SELECT
Search
Sort
Aggregate
Filter
Cache
```
Optimized only for speed.

# Why separate databases?
Suppose Order table

```
Order
Customer
Address
Items
Payment
Shipment
Coupons
Invoices
Discounts
Taxes
```
Very normalized. Perfect for writes.

But UI only needs

```
Order ID
Status
Customer Name
Total Price
```

Why perform five JOINs every time?

Instead Create another table

```
OrderView
OrderID
CustomerName
Total
Status
```

Already denormalized. Reads become much faster.

---

# Example Architecture

Traditional : Application -> PostgreSQL
Everything shares one model.

CQRS:
```
               Client

          /             \

     Command API     Query API

         |               |

   Business Logic   Read Service

         |               |

     PostgreSQL      Elasticsearch

```

Notice
Writes go to PostgreSQL.
Reads come from Elasticsearch.
This is extremely common.

---
# Wait...

# How does Elasticsearch know new data was written?

Usually through **events**.

```
Create Order
        ↓
PostgreSQL updated
        ↓
Publish OrderCreated Event
        ↓
Consumer receives event
        ↓
Update Elasticsearch
```
Now PostgreSQL is the source of truth.
Elasticsearch is only a read model.


# Is CQRS Event Driven?

This is the confused part. **CQRS itself is NOT an event-driven architecture.**
It is an **architectural pattern** whose goal is to separate read and write responsibilities.

However... CQRS is **very commonly combined** with Event-Driven Architecture (EDA).
```
Command -> Database Updated -> Publish Event -> Read Model Updated
```
The event is simply a synchronization mechanism. You can implement CQRS without events by updating both models synchronously, 
but events make the two sides loosely coupled and scalable.

---

# Where does CQRS fit?
CQRS is **not** a transaction pattern and **not** inherently an event pattern.
See [ChoosingArchitecture.md](ChoosingArchitecture.md)

---

# Benefits
✅ Read and write models optimized independently.
✅ Scale reads separately from writes.
```
10 Write Servers
100 Read Servers
```
Possible with CQRS.
Different databases can be used.
```
Writes -> PostgreSQL
Reads -> Redis -> Elasticsearch / MongoDb
```

Each chosen for its strengths.
Business logic stays focused.
Commands only modify state.
Queries only retrieve state.

---

# Downsides

Everything becomes more complex.

You now maintain

* Two models
* Two APIs
* Data synchronization
* Event handling (if used)
* Monitoring
* Retry mechanisms
Read data may be slightly stale.

```
Write -> Event Published -> (2 seconds later) -> Read model updated
```
For those two seconds, a query might not reflect the latest write. This is called **eventual consistency**, and it's an intentional trade-off in many CQRS systems.

---
# Real-world example
A common e-commerce architecture looks like this:
It is using CQRS + Event Sourcing + Event-Driven Architecture.
```
Customer Places Order
          |
          v
    Order Service (Command)
          |
      PostgreSQL
          |
    OrderCreated Event
          |
   ----------------------
   |                    |
Inventory Service   Search Index Updater
   |                    |
PostgreSQL         Elasticsearch
                         |
                    Customer Search
```
The command side guarantees correctness (inventory, payment, transactions). The query side is optimized for searching and displaying orders quickly.
---

## Mental model to remember

Think of CQRS as assigning two specialists instead of one generalist:

* **Command side**: "I ensure the business state changes correctly."
* **Query side**: "I answer questions as quickly as possible."

They cooperate, but each is optimized for a different job.

---

Given your learning path, the next logical topic is **Event Sourcing**. It is frequently used together with CQRS, but they solve different problems. Understanding how they complement each other will also make concepts like Kafka, Saga, and distributed systems much easier to grasp.
