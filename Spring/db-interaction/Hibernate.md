# 🐘 Hibernate — Core Concepts

## 📌 At a Glance

|                                                     |                                                                                                                            |
|-----------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------|
| **`EntityManager`**                                 | JPA's API for the persistence context — create, read, update, delete, query, all scoped to the current transaction/session |
| **[Persistence context](Persistence%20Context.md)** | The first-level cache — the set of entities `EntityManager` is currently tracking and dirty-checking                       |
| **`persist()`**                                     | Moves an entity from *transient* → *managed*; schedules an `INSERT` (may be immediate or deferred)                         |
| **`flush()`**                                       | Pushes pending SQL for all managed/dirty entities to the DB — without ending the transaction                               |
| **`clear()`**                                       | Detaches every entity from the persistence context, emptying it                                                            |

## 📑 Contents

- [How Hibernate Handles Object Cycles](#object-cycles)
- [What Is entityManager?](#entitymanager)
- [What Happens When persist() Is Called?](#persist)
- [flush() and clear()](#flush-clear)

---

<a id="object-cycles"></a>
## 🔄 How Hibernate Handles Object Cycles

Hibernate expects and natively supports bidirectional (cyclic) entity relationships — it doesn't fight them the way Spring's dependency injection does. Detection happens via the `Session`'s first-level cache and `ActionQueue`, 
and resolution uses the `mappedBy` ownership attribute plus SQL order splitting for non-nullable FK cycles. The real danger isn't Hibernate itself — it's serializing those cyclic entities straight to JSON.

Full breakdown: [`Cyclic Dependency.md`](../Bean/Cyclic%20Dependency.md#hibernate-entity-persistence) — **2. Hibernate: Entity Persistence & Mapping**.

---

<a id="entitymanager"></a>
## ❓ What Is entityManager?

`EntityManager` is JPA's interface for interacting with the **persistence context** — the set of entity instances currently being managed (tracked, dirty-checked, and synchronized with the database) for the current unit of work. 
Under Hibernate, it's backed by (or is a thin wrapper around) a `Session`.

Through it you can:
- Persist new entities (`persist()`), fetch existing ones (`find()`), remove them (`remove()`), and merge detached ones back in (`merge()`).
- Run queries — JPQL, Criteria API, or native SQL.
- Participate in the current transaction boundary (typically managed by Spring via `@Transactional`, or manually).

In a Spring application, you rarely `new` one up yourself — it's injected (`@PersistenceContext`) or hidden behind Spring Data JPA repositories, and it's scoped to the current transaction: one logical persistence context per unit of work.

For the full breakdown of what the persistence context actually is (identity map, dirty checking, write-behind, cascading), see [`Persistence Context.md`](Persistence%20Context.md).

---

<a id="persist"></a>
## ⚙️ What Happens When entityManager.persist() Is Called?

`persist(entity)` moves the entity through the JPA entity lifecycle:

```
Transient (new, not tracked)
    ↓ persist()
Managed (tracked by the persistence context)
    ↓ flush() / commit()
Stored in the database
```

Specifically:

1. **The entity becomes managed.** It's added to the persistence context (first-level cache), and Hibernate starts dirty-checking it — any further field changes you make will be picked up automatically at the next flush.
2. **The `INSERT` isn't always immediate.** Hibernate can defer the actual SQL until flush time ("write-behind"), batching it with other pending changes — *unless* the ID generation strategy requires the database to hand back a generated key right away 
     (e.g. `GenerationType.IDENTITY` forces an immediate insert; `SEQUENCE`/`TABLE` strategies typically don't).
3. **Calling it on the wrong entity state throws.** `persist()` expects a transient entity — calling it on an already-detached entity is invalid and will throw (use `merge()` for detached entities instead).

If you `persist()` a large batch without periodically flushing and clearing, every one of those entities stays resident in the persistence context — see [`Memory Leaks.md`](../../Java/JVM/Memory%20Leaks.md#hibernate-jpa-persistence-context) for that failure mode.

---

<a id="flush-clear"></a>
## 🧹 flush() and clear() — What Do They Do?

|                                        | `flush()`                                                                                                                                         | `clear()`                                                                          |
|----------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------|
| **Does**                               | Synchronizes the persistence context with the DB — sends all pending `INSERT`/`UPDATE`/`DELETE` SQL now                                           | Detaches every entity currently in the persistence context, emptying it            |
| **Ends the transaction?**              | No                                                                                                                                                | No                                                                                 |
| **Undoes already-flushed DB changes?** | —                                                                                                                                                 | No — data already sent to the DB stays there                                       |
| **Typical use case**                   | Force the DB to reflect pending changes before a subsequent query in the same transaction depends on them, or surface constraint violations early | Prevent the first-level cache from growing unbounded during large batch operations |

Both are used together for **batch processing**, so the persistence context doesn't accumulate every entity you've touched and blow up the heap:

```java
for (int i = 0; i < entities.size(); i++) {
    em.persist(entities.get(i));

    if (i % BATCH_SIZE == 0) {
        em.flush(); // push pending SQL to the DB
        em.clear(); // detach everything, empty the first-level cache
    }
}
```

Without this pattern, this is exactly the leak scenario in [`Memory Leaks.md`](../../Java/JVM/Memory%20Leaks.md#hibernate-jpa-persistence-context) — **10. Hibernate/JPA Persistence Context**.


***Difference between flush and commit
===
**Flush** and **Commit** are two completely different operations that happen **at different stages of a database transaction**.

Here is the easiest way to separate them:

* **Flush** synchronizes your Java memory state with the **Database Network Buffer** (sends the SQL).
* **Commit** synchronizes the database buffer with the **Database Permanent Storage** (makes it permanent).

---

### The Dynamic Duo: Flush vs. Commit

To see how they differ, let's look at their distinct responsibilities:

| Feature              | Flush (`entityManager.flush()`)                                                                         | Commit (`transaction.commit()`)                                                                                |
|----------------------|---------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------|
| **What it does**     | Translates entity changes into SQL (`INSERT`, `UPDATE`, `DELETE`) and sends them to the database.       | Tells the database to permanently save the current transaction data and release all locks.                     |
| **Database State**   | The database holds the changes in a **temporary transaction state**. Locks are held.                    | The changes are **written to disk** and made visible to all other users. Locks are released.                   |
| **Can you Undo?**    | **Yes.** Since the transaction is still open, you can still call a `rollback()` to wipe everything out. | **No.** Once committed, the changes are permanent. You would have to write a new transaction to alter it.      |
| **Who triggers it?** | Often done **automatically** by Hibernate right before a query executes, or manually by you.            | Triggers **at the very end** of a logical unit of work (e.g., when a Spring `@Transactional` method finishes). |

---

### The Blueprint of a Single Transaction

When you run a standard transactional method in Java, Hibernate executes these steps in a very specific order. Notice exactly when the database gets the SQL versus when it gets the final lock release:

```
[Start Transaction]
       │
       ▼
 1. Modify Java Objects (Changes sit in Hibernate's First-Level Cache)
       │
       ▼
 2. FLUSH occurs (Hibernate performs dirty checking, generates SQL, sends to DB)
       │  • Database executes SQL rows
       │  • Database acquires Write Locks
       │  • Other transactions CANNOT see these changes yet (under Read Committed)
       │
       ▼
 3. COMMIT occurs (The application says: "We are officially done!")
       │  • Database saves data permanently to disk
       │  • Database releases all Write Locks
       │
       ▼
[End Transaction]

```

### Why does Hibernate separate them?

Why not just send the SQL statements to the database the exact millisecond you update a Java object? **Performance and Optimization.**

By delaying the `flush` until the absolute last necessary moment, Hibernate can optimize your database interactions behind the scenes via **Transactional Write-Behind**. (A feature of persistence context)

1. **Action De-duplication:** If you change a user's name to "Alice", then "Bob", then "Charlie" all within the same method, Hibernate doesn't spam the database with three separate `UPDATE` network calls. 
                              It waits for the flush and sends exactly *one* `UPDATE` statement containing "Charlie".
2. **Batching:** If you are saving 100 new rows, Hibernate can hold them in memory and send them to the database in a single, highly optimized batch network request during the flush phase, rather than 100 individual network round-trips.

> **Rule of Thumb:** **Flush** is Hibernate talking to the database network layer. **Commit** is the database finishing up its internal paperwork and locking the safe. Every Commit triggers a Flush first, but a Flush does not trigger a Commit.



### Flush and Commit in perspective of application and database

Flush and commit splits the network traffic and responsibilities between Java application and the database engine.

To lock this concept down visually, here is exactly how those two commands travel from your application layer across the network network to the database server:

```
APPLICATION LAYER                              DATABASE LAYER
(Your Java/Hibernate Code)                     (Postgres, MySQL, Oracle, etc.)

┌────────────────────────┐                     ┌────────────────────────┐
│ Persistence Context    │                     │ Transaction Buffer     │
│ (Java Memory Cache)    │                     │ (Uncommitted State)    │
│                        │                     │                        │
│ User.setName("Bob");   │                     │ [Row Lock Acquired]    │
└───────────┬────────────┘                     └───────────┬────────────┘
            │                                              ▲
            │  1. FLUSH COMMAND                            │
            │  (Sends SQL: "UPDATE users SET name='Bob'")  │
            └──────────────────────────────────────────────┘
            
            At this point: The database executes the SQL in its temporary 
            buffer and holds a lock on that row. No other user can see it yet.
            
            ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ 

┌────────────────────────┐                     ┌────────────────────────┐
│ Transaction Finished   │                     │ Permanent Storage      │
│ (Method returns)       │                     │ (Written to Disk)      │
└───────────┬────────────┘                     └───────────▲────────────┘
            │                                              │
            │  2. COMMIT COMMAND                           │
            │  (Sends: "COMMIT TRANSACTION")               │
            └──────────────────────────────────────────────┘
            
            At this point: The database writes the buffer to disk, 
            releases the row lock, and makes "Bob" visible to the world.

```

### The Key Takeaways of this Architecture:

1. **Flush is about Data Translation:** It converts Java objects into database-friendly SQL statements and sends them over the wire. It tells the database: *"Hey, prepare this data and hold a lock on it for me."*
2. **Commit is about Finality:** It sends a simple, lightweight signal to the database engine. It tells the database: *"Everything I sent you during the flush phase is good to go. Make it permanent and let everyone else see it."*

> **Disclaimer:** "Database Network Buffer" / "Transaction Buffer" used above are simplified teaching terms, not real database internals. There's no literal separate "network buffer" stage inside the engine. What actually happens on flush is that the DB applies the SQL to its own buffer pool / in-memory page cache and records it in the write-ahead log (WAL), while a row-level exclusive lock is held on the affected rows. Commit then forces that WAL entry to be durably fsynced to disk and releases the locks. The "buffer" framing here is just a mental model for *uncommitted-but-executed* vs. *durable-and-visible* — not a named component you'd find in Postgres/MySQL/Oracle documentation.