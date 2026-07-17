# 🔄 Cyclic Dependency: Spring DI vs. Hibernate

While both Spring Boot (via the Spring Framework) and Hibernate deal with circular references in an object graph, they handle them at completely different lifecycle stages. Spring deals with circular dependencies during **object creation** (dependency injection), while Hibernate deals with them during **runtime data persistence and serialization**.

Here is how each framework detects and manages these loops.

## 📌 At a Glance

|                     | Spring DI                                                                                      | Hibernate                                                                               |
|---------------------|------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------|
| **When it happens** | Bean creation (dependency injection)                                                           | Runtime data persistence & serialization                                                |
| **Detected via**    | Thread-local "currently in creation" set                                                       | `Session` first-level cache + `ActionQueue`, tracked by object identity (`==`)          |
| **Default stance**  | Constructor injection **fails fast** — `BeanCurrentlyInCreationException`                      | Bidirectional graphs are **expected and natively supported**                            |
| **Resolution**      | Setter/field injection via the three-level cache, `@Lazy` proxy, or refactor into a third bean | `mappedBy` ownership + SQL order splitting                                              |
| **Hidden danger**   | None — fails loudly at startup                                                                 | JSON serialization via Jackson → `StackOverflowError` if entities are returned directly |

## 📑 Contents

- [Spring Boot: Bean Creation Phase](#spring-bean-creation)
- [Hibernate: Entity Persistence & Mapping](#hibernate-entity-persistence)

---

<a id="spring-bean-creation"></a>
## 1. Spring Boot: Bean Creation Phase

Spring builds an object graph of your application's components (beans). A circular reference happens when `ServiceA` requires `ServiceB`, and `ServiceB` requires `ServiceA`.

### How Spring Detects It

Spring tracks beans currently under creation using a thread-local "currently in creation" set. If it tries to create `ServiceA`, adds it to the set, sees it needs `ServiceB`, tries to create `ServiceB`, sees it needs `ServiceA`, and looks up `ServiceA` only to find it's *already* in the "currently in creation" set, Spring blows the whistle. It detects a loop and throws a `BeanCurrentlyInCreationException`.

### How Spring Handles It

Depending on how you inject your dependencies, Spring's behavior changes:

**Constructor Injection (fails by default)**
If both services use constructor injection, Spring cannot instantiate either because it needs a fully formed instance of one to create the other. In modern Spring Boot (starting with Spring Boot 2.6), **this will crash your application at startup**.

**Setter or Field Injection (the three-level cache solution)**
Spring can bypass loops using its internal **three-level cache** mechanism:

1. It creates a raw, uninitialized instance of `ServiceA` (via its no-arg constructor).
2. It exposes a factory for this partial instance in its "singleton factories" cache.
3. When injecting into `ServiceB`, Spring injects this partial, un-injected reference to `ServiceA`.
4. `ServiceB` finishes initializing, and then `ServiceA` finishes initializing. The loop is safely resolved.

### The Escape Hatches

If you encounter a hard constructor loop, you can resolve it using:

- **`@Lazy`:** Placing `@Lazy` on one of the constructor parameters tells Spring to inject a lightweight **dynamic proxy** instead of the real object. The actual bean resolution is deferred until a method is called at runtime, breaking the startup cycle.
- **Refactoring:** Usually, a cycle means your code violates the Single Responsibility Principle. Extracting the shared logic into a third `ServiceC` is almost always the cleanest fix.

---

<a id="hibernate-entity-persistence"></a>
## 2. Hibernate: Entity Persistence & Mapping

Hibernate maps a relational database schema onto an object graph. A circular reference here occurs when entities point to each other bidirectionally (e.g., a `@OneToMany` parent `Order` has a list of `LineItem` children, and each `@ManyToOne` `LineItem` points back to its parent `Order`).

### How Hibernate Detects It

Hibernate uses an internal **First-Level Cache (the `Session`)** and an **ActionQueue** to keep track of every entity state change. When traversing the graph to save, update, or flush changes, Hibernate keeps track of the Java identities (`==`) of the objects it has already visited in the current session.

### How Hibernate Handles It

Unlike Spring Boot, Hibernate **expects and natively supports bidirectional circular graphs** in your domain model, handling them through specific mechanisms:

- **The `mappedBy` Attribute:** In a bidirectional relationship, you must tell Hibernate which side "owns" the relationship using `mappedBy`. This ensures Hibernate knows exactly which table column drives the relationship, preventing it from trying to execute two separate, conflicting `INSERT` or `UPDATE` SQL statements for the same link.
- **SQL Order Splitting:** If `Parent` requires `Child` and `Child` requires `Parent` at the database level (non-nullable foreign keys on both sides), Hibernate will split the operations. It will issue an `INSERT` statement for one entity with a `NULL` foreign key, insert the second entity, and then issue an `UPDATE` statement to wire the first one back.

<a id="json-serialization-loop"></a>
### The Hidden Danger: JSON Serialization Loop

While Hibernate can save circular graphs perfectly fine, passing these entities directly to a controller to return as JSON will crash your app. Jackson (Spring Boot's default JSON serializer) will recursively traverse the graph (`Order` -> `LineItem` -> `Order` -> `LineItem`...) until it throws a `StackOverflowError`.

To fix the serialization loop, you use Jackson annotations:

- **`@JsonManagedReference` & `@JsonBackReference`:** `@JsonManagedReference` goes on the parent side (and gets serialized normally). `@JsonBackReference` goes on the child side, stopping Jackson from traversing back up to the parent.
- **`@JsonIdentityInfo`:** Instead of stopping the serialization, this tells Jackson to serialize the full object the first time it sees it, and replace it with just its ID (e.g., `"id": 4`) if it encounters it again later in the graph.
- **Using DTOs (Data Transfer Objects):** The most robust industry practice is to completely avoid passing managed Hibernate entities to the web layer. Mapping your entities to flat DTOs completely flattens the graph before it ever hits Jackson.
