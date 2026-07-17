# 🧩 Java Memory Leaks

*What they are, why the Garbage Collector can't stop them, the common culprits, and how to detect them.*

## 📌 At a Glance

|                                         |                                                                                                                                                                                                                                                                           |
|-----------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Can Java leak memory despite of GC?** | Yes — GC only frees *unreachable* objects, not *unneeded* ones.                                                                                                                                                                                                           |
| **Root cause**                          | An object is still referenced somewhere (a `static` field, cache, listener, `ThreadLocal`, …) even though the app will never use it again.                                                                                                                                |
| **Symptom**                             | Heap usage climbs after every Full GC and never drops back to baseline.                                                                                                                                                                                                   |
| **12 common causes**                    | Static collections · Unclosed resources · Listener/observer leaks · ThreadLocal · Unbounded cache · Infinite-growing collections · Long-lived singleton · Executor never shut down · Broken equals/hashCode · JPA persistence context · ClassLoader leak · Lambda capture |
| **How to detect**                       | Heap dumps (MAT / VisualVM), `jmap` / `jcmd`, JFR + JMC, GC logs, heap-usage monitoring (Loki, Grafana)                                                                                                                                                                   |
| **Worst case**                          | Rising GC frequency → longer pauses → falling throughput → `OutOfMemoryError` / crash/ restart loop                                                                                                                                                                       |

## 📑 Contents

- [The Myth](#the-myth)
- [What Is a Memory Leak?](#what-is-a-memory-leak)
- [Why GC Can't Clean It](#why-gc-cant-clean-it)
- [Memory Leak vs. High Memory Usage](#leak-vs-high-usage)
- [Common Causes](#common-causes)
- [What Happens If It Isn't Fixed](#what-happens-if-unfixed)
- [How to Detect Memory Leaks](#how-to-detect)
- [Interview Summary](#interview-summary)

---

<a id="the-myth"></a>
## 💭 The Myth

> "**Java has Garbage Collection, so memory leaks cannot happen.**"
>
> **This is false.**

Java prevents manual memory management bugs (like dangling pointers, double free), but it cannot free objects that are still reachable, even if your application will never use them again.

---

<a id="what-is-a-memory-leak"></a>
## ❓ What Is a Memory Leak?

**Short version:** objects nobody needs anymore are still referenced somewhere, so the Garbage Collector can never reclaim them — and heap usage keeps growing.

<details>
<summary>📎 Show the reachability flow</summary>

```
Need Object
    ↓
Create Object
    ↓
Finish using it
    ↓
Reference still exists ❌
    ↓
GC thinks object is still alive
    ↓
Memory cannot be reclaimed
```

</details>

So the leak is **logical**, not physical.

---

<a id="why-gc-cant-clean-it"></a>
## 🔍 Why GC Can't Clean It

**Short version:** GC only removes *unreachable* objects. If there's a live chain from a GC Root all the way to your object, GC leaves it alone — no matter how useless it actually is.

<details>
<summary>📎 Show the GC-root reachability example</summary>

```
GC Root
    │
    ▼
Static Variable
    │
    ▼
HashMap
    │
    ▼
User Object
```

Since `User` is reachable from a GC Root, GC says: "Someone can still access this object." Therefore it is **not collected**, even if the application never uses it again.

</details>

<details>
<summary>📎 Show the static-list leak example (code)</summary>

```java
public class Cache {

    static List<String> users = new ArrayList<>();

    public static void add(String name) {
        users.add(name);
    }

}
```

In code this loop is called:

```java
for (int i = 0; i < 10_000_000; i++) {
    Cache.add("User-" + i);
}
```

```
Heap
Static List
    ↓
  User1
  User2
  User3
   ...
  User10000000
```

Nothing gets removed. Heap keeps increasing forever.

```
GC runs
    ↓
Cannot collect
    ↓
Memory leak
```

</details>

---

<a id="leak-vs-high-usage"></a>
## ⚖️ Memory Leak vs. High Memory Usage

**Short version:** high memory usage rises for a real reason and drops back down once the work is done; a memory leak rises and never comes back down, because the memory isn't actually freed anymore.

|                     | High Memory Usage                             | Memory Leak                                               |
|---------------------|-----------------------------------------------|-----------------------------------------------------------|
| **Trigger**         | Legitimate workload (e.g. video processing)   | Objects accidentally retained                             |
| **Shape over time** | Rises, then **drops back** once work finishes | Rises and **never comes back down**                       |
| **Verdict**         | Perfectly normal. No leak.                    | Application isn't actually using all that memory anymore. |

<details>
<summary>📎 Show both memory-over-time traces</summary>

**High Memory Usage** — example: video processing

```
100 MB  (Application's baseline memory usage)
  ↓
2 GB    (Memory usage increases during processing)
  ↓
Finished
  ↓
200 MB  (Memory usage drops)
```

**Memory Leak**

```
100 MB
  ↓
200 MB
  ↓
500 MB
  ↓
900 MB
  ↓
1.5 GB
  ↓
2 GB
  ↓
Never decreases
```

</details>

---

<a id="common-causes"></a>
## 🛠️ Common Causes

Click any cause to expand its example.

<details>
<summary><strong>1. Static Collections</strong> — a static Map/List that only grows and is never cleared, so GC can never reclaim its entries.</summary>

```java
public class UserCache {

    static Map<Long, User> cache = new HashMap<>();

}
```

Every request: `cache.put(id, user);` — never `remove()`.

```
1 million users
    ↓
5 million users
    ↓
20 million users
```

Heap explodes.

**Production fix:**
- Caffeine
- Redis
- Guava Cache
- Expiry
- LRU

</details>

<details>
<summary><strong>2. Unclosed Resources</strong> — files, sockets, DB connections, or threads opened but never closed, leaking native resources.</summary>

- FileInputStream
- Socket
- Database Connection
- Thread

Example — open a file:

```java
FileInputStream in = new FileInputStream(file);
```

But forgot to close it:

```java
in.close();
```

These are usually resource leaks rather than heap leaks, but they often lead to increased memory usage and application instability because native resources remain allocated.

Correct — try-with-resources:

```java
try (FileInputStream in = new FileInputStream(file)) {
}
```

</details>

<details>
<summary><strong>3. Listener / Observer Leaks</strong> — a registered listener that's never unregistered, keeping its whole object graph alive.</summary>

- Spring Events
- Kafka listeners
- RabbitMQ callbacks

Example:

```java
eventBus.register(listener);
```

Forgot:

```java
eventBus.unregister(listener);
```

What happens:

```
EventBus
   ↓
Listener
   ↓
Huge Object Graph
```

GC cannot remove it.

</details>

<details>
<summary><strong>4. ThreadLocal Leak</strong> — per-thread state set but never removed; pooled threads never die, so neither does the data.</summary>

Extremely common in web applications.

```java
ThreadLocal<UserContext> threadLocal;
```

Request:

```java
threadLocal.set(user);
```

Forgot:

```java
threadLocal.remove();
```

```
Thread pool
Worker Thread
    ↓
ThreadLocalMap
    ↓
UserContext
```

Since the thread never dies (pooled), the object never dies.

Correct:

```java
try {

} finally {
    threadLocal.remove();
}
```

</details>

<details>
<summary><strong>5. Cache Without Eviction</strong> — a cache map with no expiry or LRU policy, growing unbounded over time.</summary>

Almost identical to Static Collections above.

```java
Map<String, Product> cache;
```

Acts like a cache but never removes anything. After a year it contains 40 million entries.

</details>

<details>
<summary><strong>6. Infinite Growing Collections</strong> — lists/queues (like logs) that are appended to forever instead of being off-loaded.</summary>

`List`, `Map`, `Queue`, `Set`

Example: `logs.add(log);`

Instead use one of these:
- Database
- Kafka
- File
- Rolling Queue

</details>

<details>
<summary><strong>7. Long-lived Singleton Holding References</strong> — a singleton bean that accumulates every request it ever sees.</summary>

```java
@Component
class RequestHistory {
    List<Request> requests;
}
```

Singleton bean used to contain all requests. Any request that comes in gets added, and it's never cleared.

```
Singleton
    ↓
List
    ↓
Request1
    ↓
Request2
    ↓
   .....
    ↓
Request5000000
```

Classic leak.

</details>

<details>
<summary><strong>8. ExecutorService Not Shutdown</strong> — a thread pool that's never shut down, so its threads (and their stacks/buffers) live forever.</summary>

`ExecutorService` pool never calls `shutdown()`. Threads stay alive. Each thread owns:
- Stack
- ThreadLocal
- Buffers

Memory never released until JVM exits.

</details>

<details>
<summary><strong>9. Improper Equals/HashCode</strong> — broken equals/hashCode means remove() can't find the key you inserted, so it's stuck forever.</summary>

Implements hashCode/equals in a way that doesn't match later on:

```java
map.put(new User(...), value);
```

Later:

```java
map.remove(new User(...));
```

Fails because equals/hashCode are wrong (or a field used in `hashCode()` changed after insertion). Objects remain forever — memory leak.

</details>

<a id="hibernate-jpa-persistence-context"></a>
<details>
<summary><strong>10. Hibernate/JPA Persistence Context</strong> — entities pile up in the first-level cache when a batch isn't flushed/cleared.</summary>

Fetched a large batch, called `entityManager.persist()`. Everything stays in the first-level cache (persistence context). Need `flush()` and `clear()` periodically. Otherwise every entity remains in heap.

For what `persist()`, `flush()`, and `clear()` actually do, see [`Hibernate.md`](../../Spring/db-interaction/Hibernate.md#flush-clear).

</details>

<details>
<summary><strong>11. ClassLoader Leak</strong> — a classloader stays referenced after redeploy, keeping every class it loaded alive.</summary>

Mostly involves Tomcat, Jetty, Spring Boot DevTools, and application servers. A classloader remains referenced after redeployment, keeping all loaded classes and static fields alive. Over multiple redeployments, this can exhaust heap or metaspace.

</details>

<details>
<summary><strong>12. Lambda / Inner Class Capturing Large Objects</strong> — a lambda captures a large object; as long as the lambda lives, so does the object.</summary>

```java
HugeObject obj = ...

Runnable r = () -> {
    System.out.println(obj);
};
```

If `r` is stored somewhere long-lived, it is retained forever — and `obj` survives with it.

</details>

---

<a id="what-happens-if-unfixed"></a>
## 📉 What Happens If a Memory Leak Isn't Fixed?

Suppose heap is capped with `-Xmx2G`.

| Stage | Effect |
|---|---|
| 1. More Frequent GC | GC notices memory pressure — runs every 2 sec instead of every 10 sec. CPU increases, app slows. |
| 2. Longer GC Pauses | GC has to scan more objects. Pause time increases. Latency increases. |
| 3. Throughput Drops | CPU: 70% → 85% → 95%. Most of it spent on GC, not serving requests. |
| 4. Full GC | Full GC runs repeatedly. Stop-the-world. App freezes briefly. |
| 5. OutOfMemoryError | `java.lang.OutOfMemoryError: Java heap space`, or `GC overhead limit exceeded`. |
| 6. Application Crash | If uncaught, JVM exits. Kubernetes/systemd restarts it. Problem repeats. |

<details>
<summary>📎 Show the illustrative heap-growth timeline</summary>

```
Initially       200 MB
After one day   600 MB
After two days  1.3 GB
After three days 1.8 GB
Eventually      2 GB (heap full)
```

*(This staged progression is illustrative — a common real-world pattern, not a formally defined JVM specification.)*

</details>

---

<a id="how-to-detect"></a>
## 🧪 How to Detect Memory Leaks

| Tool / Technique | Purpose |
|---|---|
| Eclipse MAT / VisualVM | Heap dump analysis — find objects retaining the most memory |
| `jmap` / `jcmd` | Capture heap dumps / inspect JVM state |
| JFR + JMC | Profile allocations and object lifetimes |
| GC logs | Observe heap growth and increasing GC frequency |
| Micrometer + Prometheus + Grafana | Monitor JVM heap usage over time |

A classic sign is the used heap after each major GC gradually increasing instead of returning to a stable baseline:

```
After Full GC: 100 MB -> 180 MB -> 350 MB -> 600 MB -> 900 MB
```

If the application workload is similar but the post-GC heap keeps growing, it's a strong indication of a memory leak.

---

<a id="interview-summary"></a>
## 🎯 Interview Summary

> A memory leak in Java occurs when objects that are no longer needed remain reachable, so the Garbage Collector cannot reclaim them. Garbage collection only removes unreachable objects; it cannot determine whether a reachable object is still logically useful. Common causes include static collections, unbounded caches, ThreadLocal misuse, listener registration without deregistration, singleton objects retaining data, JPA persistence contexts, classloader leaks, and improperly managed thread pools. If left unresolved, memory leaks increase heap usage, trigger more frequent and longer GC cycles, reduce application throughput, and eventually cause `OutOfMemoryError` or severe service degradation. The most effective way to diagnose leaks is to compare heap dumps over time and identify objects that continuously accumulate and remain strongly reachable.
