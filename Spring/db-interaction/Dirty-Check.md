> **Terminology — two unrelated concepts share the word "dirty":**
> - **Dirty Checking** is a **persistence-layer** concept (Hibernate/JPA). It's the mechanism where the ORM diffs a managed entity's current field values against its original snapshot to figure out what to `UPDATE`.
> - **Dirty Read** and **Dirty Write** are **database** concepts, defined by SQL transaction isolation levels — they describe one transaction seeing (or clobbering) another transaction's *uncommitted* data at the DB level.
>
> This doc walks through where these two layers intersect: what an uncommitted, unflushed change in Hibernate's persistence context looks like to another transaction, and where DB isolation levels actually start to matter.

**What happens when a transaction is in dirty checking an object which is modified but not flushed and another transaction fires a read onto that object ?**
===
When Transaction A modifies a managed Java object in memory but **has not flushed** it, the change exists **only** within Transaction A's local **Persistence Context** (the first-level cache). 
The database itself knows absolutely nothing about this change yet.

What happens when Transaction B fires a read onto that object depends entirely on **how** Transaction B is trying to read it.

---

### Case 1: Transaction B queries the Database directly from a new transaction

Because Transaction A hasn't flushed, the database still holds the old data.

* **The Result:** Transaction B will **always read the old, unmodified data** from the database rows.

* **Why Database Isolation Levels Don't Matter (Yet):**
Database concepts like "Dirty Reads" or `READ_UNCOMMITTED`are only apply if Transaction A has flushed the SQL to the database network buffer but hasn't *committed* yet. 
Flush call -> uncommitted -> Data in network buffer -> Dirty Reads / READ_UNCOMMITTED
Because the change hasn't even left Transaction A's memory, database settings have zero effect. 
Transaction B hits the database tables and gets the original state.

> **Disclaimer:** "Database network buffer" above is a simplified teaching term, not a real database internal. There's no literal separate "network buffer" stage — once flushed, the SQL is applied to the DB engine's own buffer pool / page cache and recorded in the write-ahead log (WAL), with a row-level exclusive lock held on the affected rows until commit or rollback. The "buffer" framing is just shorthand for *executed-but-uncommitted*, not a named component in Postgres/MySQL/Oracle documentation.

---

### Case 2: Transaction B is sharing the *same* Persistence Context

In most standard web applications (like Spring Boot using the default "Transaction-per-Request" pattern), each transaction gets its own isolated Extended or Transaction-scoped Persistence Context.

However, if Transaction B is actually just a nested method or routine sharing the exact same `EntityManager` / `Session` as Transaction A, they share the same memory space.

* **The Result:** Transaction B will **see the modified, unflushed object immediately.**
* **Why:** When any query is executed within the same persistence context, Hibernate checks its first-level cache before hitting the database. 
Since the modified object is living right there in the cache, Hibernate simply hands Transaction B the reference to that modified Java object.

---

### What if Transaction A *flushes* but doesn't *commit*?

To paint the full picture, if Transaction A calls `entityManager.flush()`, Hibernate pushes the `UPDATE` SQL statement to the database, but the transaction remains open. 
Now, the database holds the uncommitted change, and the **Database Isolation Level** takes over:

| Isolation Level of Transaction B                                  | What Transaction B Sees | Explanation                                                                                                                                           |
|-------------------------------------------------------------------|-------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------|
| **`READ_COMMITTED`** *(Default for Postgres, Oracle, SQL Server)* | **Old Data**            | The database hides Transaction A's changes until a `COMMIT` happens.                                                                                  |
| **`REPEATABLE_READ` / `SERIALIZABLE**`                            | **Old Data**            | Stricter isolation guarantees Transaction B sees the data exactly as it was when B started.                                                           |
| **`READ_UNCOMMITTED`**                                            | **Modified Data**       | **A Dirty Read occurs**. Transaction B reads the uncommitted data from the DB. If Transaction A rolls back, Transaction B is stuck with "ghost" data. |


> **Summary:** If it hasn't been flushed, the change is entirely trapped in Transaction A's memory cache. No external transaction can see it.