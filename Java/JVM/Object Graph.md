An **object graph** is a visual or conceptual map that shows a collection of objects in an application and how they reference (or point to) one another.

Instead of looking at your code as a flat list or isolated chunks, an object graph treats your objects as **nodes** and their relationships/properties as **edges** connecting them.

---

### A Simple Example

Think of a standard e-commerce feature. If you have an `Order` object, it doesn't exist in a vacuum. It likely points to a `Customer`, a list of `LineItem` objects, and a `ShippingAddress`.

```
   [ Customer ] 
        ▲
        │ (belongs to)
        │
    [ Order ] ──(contains)──► [ LineItem ] ──► [ Product ]
        │
        └──(ships to)──► [ ShippingAddress ]

```

In this setup, the entire connected web of blocks is the **object graph**.

---

### Why Object Graphs Matter

You interact with object graphs constantly, especially in backend development. They are the backbone of several core programming concepts:

* **Dependency Injection (DI):** Frameworks like **Spring** build an object graph (the ApplicationContext) at startup. It figures out that `OrderController` needs `OrderService`, which needs `OrderRepository`, and wires them together in the correct order.
* **Garbage Collection (GC):** The JVM utilizes the object graph to manage memory. The garbage collector starts at a "root" node (like the main running thread) and traverses the graph. If an object on the heap can no longer be reached by following any arrows in the graph, it is considered dead code and gets swept away.
* **Object-Relational Mapping (ORM):** Tools like **Hibernate** translate flat database rows into a living object graph in your application memory. Managing this graph correctly prevents classic pitfalls like loading too much data at once (the $N+1$ query problem).
* **Serialization:** Converting an object into JSON, XML, or a byte stream requires traversing its graph to make sure all nested data is captured in the correct sequence.

### The Challenge: Circular References

One of the trickiest parts of managing an object graph is handling **cycles**. For example, if `Object A` references `Object B`, and `Object B` references `Object A`, you have a circular loop. Without guardrails, a framework trying to serialize or inject these objects can get stuck in an infinite loop, leading to stack overflow errors.

---

### Detecting Cycles

The standard fix is a **graph traversal (DFS or BFS) with a "visited" set**. As you walk the graph, you record every node you've already entered; if traversal reaches a node that's already in that set, you've found a cycle.

```java
Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());

boolean hasCycle(Object node) {
    if (!visited.add(node)) {
        return true; // already seen this exact instance -> cycle
    }
    for (Object neighbor : getReferences(node)) {
        if (hasCycle(neighbor)) return true;
    }
    return false;
}
```

Note the `IdentityHashMap`-backed set: identity (`==`), not `equals()`/`hashCode()`, is used to track "already visited." That matters because in the JPA example below, `equals()`/`hashCode()` may themselves be part of the recursive problem — using them to detect the cycle would just relocate the infinite loop.

---

### The Bidirectional JPA + Lombok Trap

This is the most common real-world bug caused by an unmanaged object graph:

```java
@Entity
@Data // Lombok: generates equals(), hashCode(), toString() using ALL fields
public class Order {
    @OneToMany(mappedBy = "order")
    private List<LineItem> items;
}

@Entity
@Data
public class LineItem {
    @ManyToOne
    private Order order;
}
```

`Order` and `LineItem` reference each other. Lombok's `@Data` generates `toString()`/`equals()`/`hashCode()` that include *every* field — so `Order.toString()` calls `LineItem.toString()`, which calls `Order.toString()` again, forever, until `StackOverflowError`. The exact same trap hits Jackson when serializing this pair to JSON.

**Fixes:**
- Exclude the back-reference from generated methods: `@ToString.Exclude` / `@EqualsAndHashCode.Exclude` on the `order` field in `LineItem`.
- For JSON: `@JsonManagedReference` (forward side) / `@JsonBackReference` (back side), or just map to DTOs instead of serializing entities directly.

For how Hibernate itself detects and resolves the entity-side cycle (`mappedBy` ownership, SQL order splitting) before this even reaches Jackson, see [`Cyclic Dependency.md`](../../Spring/Bean/Cyclic%20Dependency.md#hibernate-entity-persistence) — **2. Hibernate: Entity Persistence & Mapping**.

---

### Circular Dependencies in Spring DI

The same cycle problem shows up in the `ApplicationContext`'s bean graph:

```java
@Component
class ServiceA {
    ServiceA(ServiceB b) {} // constructor injection
}

@Component
class ServiceB {
    ServiceB(ServiceA a) {}
}
```

- **Constructor injection** fails fast at startup with `BeanCurrentlyInCreationException` — Spring can't fully construct `ServiceA` without a finished `ServiceB`, and vice versa; neither can ever be "finished."
- **Setter/field injection** can survive it. Spring exposes an early, not-yet-fully-populated reference to the bean (via its three-level singleton cache) so the other bean can grab a handle to it, then comes back and finishes wiring the first bean's properties afterward.

This isn't really a "feature" to lean on — a circular bean dependency is usually a design smell. The conventional fix is to break the cycle by extracting the shared behavior into a third bean that both depend on, rather than relying on setter injection to paper over it.

For the full detection mechanism and the `@Lazy` escape hatch, see [`Cyclic Dependency.md`](../../Spring/Bean/Cyclic%20Dependency.md#spring-bean-creation) — **1. Spring Boot: Bean Creation Phase**.

---

### How GC Traverses the Graph

The GC bullet above is really its own deep topic — full walkthrough of GC Roots, reachability analysis (marking), and the mark-sweep-compact cycle lives in [`Garbage Collector.md`](Garbage%20Collector.md#step-a-marking-reachability-analysis) (see **Step A: Marking (Reachability Analysis)**). Short version: the GC treats the heap as an object graph, starts at GC Roots (thread stacks, static variables, JNI references), and anything not reachable by walking that graph is dead.

---

### Deep Copy vs. Shallow Copy

Cloning an object graph raises the same reachability question from the other direction — how much of the graph actually gets duplicated?

- **Shallow copy**: only the top node is duplicated. Its fields still point at the *same* nested objects as the original (this is what `Object.clone()` does by default — primitives are copied by value, object references are copied by reference). Mutating a nested object through the copy affects the original too.
- **Deep copy**: every reachable node is duplicated, so the copy shares no nested references with the original. Typical implementations are recursive copy constructors or a serialize/deserialize round-trip. A naive recursive deep copy needs the same trick as cycle detection — an identity map from original → copy — otherwise a cyclic or shared-reference graph gets copied infinitely or loses its shared structure.

---

### Object Graph vs. Call Graph vs. Dependency Graph

These three are easy to conflate in an interview:

|                      | What the nodes are                                     | What the edges mean                                 |
|----------------------|--------------------------------------------------------|-----------------------------------------------------|
| **Object graph**     | Runtime object instances                               | "This object holds a reference to that object"      |
| **Call graph**       | Methods/functions                                      | "This method calls that method" (static or dynamic) |
| **Dependency graph** | Classes, modules, or beans (blueprints, not instances) | "This needs that to exist/compile/be instantiated"  |

A dependency graph (e.g. Spring's bean definitions, or a Maven module tree) is the *blueprint*; the object graph is what you get once that blueprint is actually instantiated at runtime.