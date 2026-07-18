**Dirty Read & Dirty Write**
==
Both are **concurrency phenomena** that happen when multiple database transactions try to touch the exact same data at the same time.

**Dirty Writes** are actually considered far worse than Dirty Reads. Most databases completely block Dirty Writes by default because they completely break data integrity. (See [How Databases Actually Block Dirty Writes](#how-databases-block-it) for the locking mechanism.)

Here is the difference between the two.

---

### 1. What is a Dirty Read?

A **Dirty Read** occurs when Transaction B reads data that has been modified by Transaction A, but Transaction A **has not committed** the change yet.

If Transaction A decides to roll back (cancel) its changes later, the data Transaction B read becomes "dirty"—it technically never existed in the database's permanent history.

#### The Classic Example: An Account Balance Check

1. **Transaction A** updates a user's balance from $100 to $500 (but doesn't commit yet).
2. **Transaction B** reads the balance and sees $500.
3. **Transaction A** encounters an error and rolls back. The balance reverts to $100 in the database.
4. **Transaction B** just made a business decision based on a $500 balance that is now gone.

---

### 2. What is a Dirty Write?

Dirty write never occurs in any mainstream db. It is completely blocked by exclusive row lock. (See [How Databases Actually Block Dirty Writes](#how-databases-block-it).)
A **Dirty Write** occurs when Transaction B **overwrites** a value that was modified by Transaction A, while Transaction A **is still uncommitted**.

In other words, Transaction B updates a row based on uncommitted, temporary data, completely blowing away Transaction A's uncommited changes.

#### The Classic Example: The Double Car Buyer

Imagine a database table tracking a car sale with two columns: `buyer_name` and `status`. The car is currently unowned (`buyer_name = null`, `status = 'AVAILABLE'`).

1. **Transaction A** updates the car to buy it: `buyer_name = 'Alice'`, `status = 'PENDING'`. *(Uncommitted)*
2. **Transaction B** comes in simultaneously and overwrites it: `buyer_name = 'Bob'`, `status = 'SOLD'`. *(Uncommitted)*
3. **Transaction A** finishes its business logic and hits **COMMIT**.
4. **Transaction B** encounters an error and hits **ROLLBACK**.

#### The Catastrophe:

When Transaction B rolls back, the database restores the row to its *pre-Transaction B* state. But what was that state? To the database, the original state before B touched it was Alice's uncommitted data. 
If it rolls back to Alice's data, Bob's transaction failed **but changed the database state**. If the database rolls back to the *absolute* original state (before Alice), Alice's committed purchase disappears completely.

Data integrity is completely ruined, and the database state becomes corrupted.

<details>
<summary><strong>Detailed explanation of The Double Car Buyer problem & How database handles it</strong></summary>

Let’s look at this like a physical notebook where two people are trying to write on the exact same line with a pencil and an eraser.

Here is the setup: The notebook page currently says **"Car is Empty."**

Both Alice and Bob want to buy this car at the exact same time.

---

### Step-by-Step Breakdown of the Chaos

**Step 1: Alice writes her name.**

* Alice writes **"Alice"** in the notebook.
* *Crucial point:* She hasn’t closed the notebook yet (Uncommitted). She is still checking her bank account to make sure she has the money.

**Step 2: Bob commits a "Dirty Write".**

* While Alice is looking at her phone, Bob reaches over and erases "Alice." He writes **"Bob"** on the exact same line.
* Bob also hasn't closed the notebook yet (Uncommitted).

**Step 3: Alice finishes her transaction (Commit).**

* Alice looks down, finishes her bank check, and says, *"Okay, I'm done!"* She hits **Commit**.
* In her mind, she just finalized a transaction for a line that she *thinks* has her name on it. But in reality, Bob's name is physically written there right now.

**Step 4: Bob's transaction fails (Rollback).**

* Bob looks at his phone and realizes he doesn't have enough money. He needs to undo (**Rollback**) his action.
* To rollback, Bob must erase his name and restore the notebook to how it looked *right before he touched it*.

---

### The Impossible Dilemma (Why it breaks)

Now Bob is holding the eraser. He needs to put the notebook back to its "previous state." But what *is* the previous state? The database engine faces a logical paradox:

* **Choice A: Bob restores it to Alice's uncommitted data.**
  If Bob puts "Alice" back on the page, then **Bob’s failed transaction just modified the database.** Bob's transaction was supposed to be completely erased as if it never happened, yet his action of rolling back 
  is what physically writes Alice's data to the page.
* **Choice B: Bob restores it to the absolute original state ("Car is Empty").**
  If the database resets the line to "Car is Empty," then **Alice's successful purchase vanishes.** Alice successfully committed her transaction in Step 3! 
  Her money is gone, but her name is deleted from the book because Bob wiped it out during his rollback.

---

### The Takeaway

Either way you slice it, **the system fails**. A successful transaction (Alice) gets deleted, or a failed transaction (Bob) causes data to be written.

This is why databases **never allow this to happen**.

To prevent this catastrophe, the moment Alice writes her name in Step 1, the database puts a physical lock on that row. 
If Bob tries to write his name in Step 2, the database forces him to sit and wait until Alice either completely finishes (Commits) or walks away (Rollbacks). Only then is Bob allowed to touch the data.

---

In a purely theoretical world where a database allowed a Dirty Write to happen, **it absolutely would crash, corrupt the data, or throw a massive internal error** 
at Step 3 or Step 4 because the internal transaction logs would no longer make sense.

In reality **The database doesn't crash because it is physically impossible to ever reach Step 3.** The database prevents the paradox by stopping Bob at Step 2.

Here is exactly how the database engine prevents this behind the scenes using **Write Locks** (Exclusive Locks).
Let's look at what *actually* happens inside the database engine during that timeline:
---
### The Real Timeline (How the Database Stops the Paradox)

Every modern relational database (like PostgreSQL, MySQL, Oracle, or SQL Server) uses a strict rule: **If you modify a row, you automatically own an exclusive lock on that row until you Commit or Rollback.**

Let's re-run the scenario with the database's guardrails turned on:

* **Step 1: Alice modifies the row.**
  Alice updates the row to "Alice". The database immediately places an **Exclusive Lock (Write Lock)** on that specific row for Alice.
* **Step 2: Bob tries to perform a Dirty Write.**
  Bob sends an `UPDATE` statement to change the row to "Bob". The database looks at the row, sees Alice's Exclusive Lock, and tells Bob: **"Stop. You are not allowed to touch this. Sit here and wait."**
* *What Bob experiences:* Bob's application thread freezes. It literally pauses and waits on the line of code executing the update statement.
---

### The Two Ways This Safely Ends

Because the database blocked Bob at Step 2, the "notebook" safely contains **Alice's name** while Bob is stuck waiting in line. Only two things can happen now:

#### Scenario A: Alice Commits
1. Alice completes her work and hits **Commit**.
2. The database permanently saves "Alice" to the disk and **releases Alice's lock**.
3. Now that the lock is released, Bob is allowed to wake up and execute his write.
4. Bob overwrites "Alice" with "Bob". If Bob later rolls back, the database safely restores it to the last committed state: **Alice**. (No data is lost, no paradox occurs).

#### Scenario B: Alice Rolls Back
1. Alice hits an error and **Rollbacks**.
2. The database undoes her change, reverts the row to "Car is Empty", and **releases her lock**.
3. Bob wakes up, executes his write, and changes "Car is Empty" to "Bob". If Bob later rolls back, the database safely restores it to **Car is Empty**.

---

### Summary

The paradox you noticed is completely real—which is why database engineers designed engines to ensure it can never happen.

A database will allow two different transactions to *read* the same row at the same time, but it will **never, under any isolation level, allow two transactions to *write* to the same row at the same time.
The second writer is always forced to wait in line until the first writer finishes.

</details>

<a id="how-databases-block-it"></a>
### 3. How Databases Actually Block Dirty Writes

The mechanism is a **row-level exclusive lock (write lock)**, taken at write time — and it applies regardless of the configured isolation level.

1. **The lock is acquired on write, not read.** When Transaction A issues `UPDATE ... WHERE id = 5`, the lock manager takes an **exclusive (X) lock** on that row *before* applying the change, and holds it until A `COMMIT`s or `ROLLBACK`s.
2. **A concurrent writer blocks — it doesn't error.** If Transaction B tries to `UPDATE` the same row while A still holds the lock, B doesn't fail immediately; it **waits** in the lock manager's queue until A's lock is released.
3. **What B sees once unblocked depends on what A did:**
   - **A commits** → B's write proceeds against the row *as A left it* ("first committer wins").
   - **A rolls back** → A's changes are undone, so B's write proceeds against the original pre-A data, exactly as if A never happened.

Either way, the "Double Car Buyer" catastrophe above can't occur — B is never allowed to blindly overwrite A's in-flight, uncommitted change.

**Why this is independent of isolation level:** SQL isolation levels (`READ_UNCOMMITTED`, `READ_COMMITTED`, etc.) only govern *read* visibility — whether B can *see* A's uncommitted data. They don't touch write locking. Writer-vs-writer contention is serialized via row locks in essentially every mainstream RDBMS (Postgres, MySQL/InnoDB, Oracle, SQL Server), even under `READ_UNCOMMITTED`.

**MVCC nuance (Postgres, InnoDB):** MVCC's purpose is that readers never block writers and writers never block readers — each transaction sees a consistent snapshot. That solves reader/writer conflicts (dirty reads), not writer/writer conflicts. Two writers touching the same row still fall back to exclusive row locking exactly as described above.

**The JPA/Hibernate-side alternative — optimistic locking:** instead of relying on DB row locks, a `@Version` column lets Hibernate detect (rather than block) a lost write. Reads don't block anything, but the `UPDATE` includes `WHERE version = ?`; if another transaction already committed and bumped the version, the statement affects 0 rows and Hibernate throws `OptimisticLockException`. This avoids holding DB locks across a whole request, at the cost of rejecting the conflicting transaction instead of queueing it.

---

### Summary: How Databases Handle Them

| Phenomenon      | What happens?                      | How bad is it?                                                                   | Protection                                                                                                                                   |
|-----------------|------------------------------------|----------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------|
| **Dirty Read**  | Reading *uncommitted* updates.     | **Moderate.** Causes bad UI readouts or incorrect application calculations.      | Prevented by using the **`READ_COMMITTED`** isolation level (the default for Postgres, Oracle, and SQL Server).                              |
| **Dirty Write** | Overwriting *uncommitted* updates. | **Catastrophic.** Violates ACID properties, breaks rollbacks, and corrupts data. | **Prevented automatically** by almost every modern relational database engine via row-level write locks, regardless of your isolation level. |

In Hibernate/JPA terms, you rarely have to worry about Dirty Writes because the underlying database will force Transaction B to wait (block) on a row lock until Transaction A either commits or rolls back.