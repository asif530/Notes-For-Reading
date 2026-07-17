# 🐘 Hibernate — Core Concepts

## 📌 At a Glance

|                                                     |                                                                                                                            |
|-----------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------|
| **`EntityManager`**                                 | JPA's API for the persistence context — create, read, update, delete, query, all scoped to the current transaction/session |
| **[Persistence context](Persistance%20Context.md)** | The first-level cache — the set of entities `EntityManager` is currently tracking and dirty-checking                       |
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

For the full breakdown of what the persistence context actually is (identity map, dirty checking, write-behind, cascading), see [`Persistance Context.md`](Persistance%20Context.md).

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
