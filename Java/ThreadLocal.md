# 🧵 ThreadLocal

*Interview-ready notes: what it is, how it works internally, the classic pitfall, and follow-up depth.*

## 📌 At a Glance

|                                   |                                                                                                      |
|-----------------------------------|------------------------------------------------------------------------------------------------------|
| **What is it**                    | A separate class, `java.lang.ThreadLocal<T>` — not part of `Thread` itself                           |
| **Where the data actually lives** | Inside each `Thread`'s own `ThreadLocalMap`                                                          |
| **Package / origin**              | `java.lang` — added in Java 1.2, predates `java.util.concurrent`                                     |
| **Purpose**                       | Thread confinement — gives each thread its own private copy of a variable, no synchronization needed |
| **Classic pitfall**               | Memory leak in pooled threads if `.remove()` is forgotten                                            |
| **Fix**                           | Always release it in a `try` / `finally`                                                             |

## 📑 Contents

- [Is It Part of Thread?](#is-it-part-of-thread)
- [Where It Fits in "The Book"](#where-it-fits)
- [The 30-Second Interview Answer](#interview-answer)
- [How It Works Internally](#internals)
- [The Classic Pitfall: Memory Leaks in Thread Pools](#pitfall)
- [Design & Trade-off Questions](#trade-offs)

---

<a id="is-it-part-of-thread"></a>
## ❓ Is It Part of Thread?

`ThreadLocal` is its own public class — `java.lang.ThreadLocal<T>` — not a subclass or inner part of `Thread`. But the two are tightly coupled under the hood:

- **API-wise:** a separate concept. You use it as `ThreadLocal<UserContext> ctx = new ThreadLocal<>();` and call `.get()` / `.set()` / `.remove()` on it.
- **Storage-wise:** each `Thread` object actually holds the data. `Thread` has a package-private field `threadLocals` of type `ThreadLocal.ThreadLocalMap` — a small hash map local to that thread. When you call `threadLocal.set(value)`, it looks up the current thread's map and stores `this ThreadLocal instance → value` there. That's exactly why forgetting `.remove()` leaks: the value lives inside the thread's map, and pooled threads never die, so the entry never dies either.

**So:** conceptually separate, physically embedded in `Thread`.

---

<a id="where-it-fits"></a>
## 📖 Where It Fits in "The Book"

- **Package:** `java.lang` (core JDK, not `java.util.concurrent`) — it was added in Java 1.2, predates the concurrency utilities package.
- **Topic:** it's a **concurrency / thread-confinement tool**, not a collections or I/O concept. It's usually covered in concurrency material.
- **Analogy:** it's like giving each thread its own private copy of a variable instead of sharing state.

---

<a id="interview-answer"></a>
## 🎯 The 30-Second Interview Answer

> `ThreadLocal` gives each thread its own independent copy of a variable. Instead of one shared value that multiple threads have to synchronize around, every thread reads and writes its own copy — so there's no contention and no synchronization needed.
>
> It's used for thread confinement: things like per-request user context, per-thread `SimpleDateFormat` instances, or transaction/session state in web frameworks, where the value must follow the current thread but shouldn't leak into others.

---

<a id="internals"></a>
## 🔍 How It Works Internally

> Internally, each `Thread` object has its own `ThreadLocalMap` — a small hash map that lives on the thread, not on the `ThreadLocal` object.
>
> When you call `threadLocal.set(value)`, it stores `this ThreadLocal instance → value` inside the current thread's map. `get()` looks it up the same way.
>
> So the `ThreadLocal` object itself is really just a key; the actual storage belongs to the thread.

---

<a id="pitfall"></a>
## ⚠️ The Classic Pitfall: Memory Leaks in Thread Pools

The classic issue is memory leaks in thread-pooled environments — Tomcat, Spring MVC controllers, executor services. Pooled threads are long-lived and get reused, so if you `set()` a value and never call `remove()`, that value stays in the thread's map forever, even after the request that created it has finished.

Over time this silently grows heap usage, and because the `Thread` itself is a GC root, nothing you do to the original object matters — it's still reachable through the thread.

**The fix is always wrapping usage in try/finally:**

```java
try {
    threadLocal.set(user);
    // do work
} finally {
    threadLocal.remove();
}
```

Frameworks like Spring do this internally for things like `RequestContextHolder`.

---

<a id="trade-offs"></a>
## 🛠️ Design & Trade-off Questions

<details>
<summary><strong>Why not just pass the value as a parameter?</strong></summary>

Sometimes you can't — deep call chains where threading it through every method signature is impractical (e.g. logging MDC, security context). `ThreadLocal` avoids that "parameter drilling."

</details>

<details>
<summary><strong>What is InheritableThreadLocal?</strong></summary>

A variant where child threads inherit the parent's value at creation time. Useful, but doesn't work well with thread pools, since pool threads are created once, not per task.

</details>

<details>
<summary><strong>What's the performance cost?</strong></summary>

Lookups aren't free — each `get()` / `set()` hashes into the thread's map — but it's still far cheaper than synchronizing shared state.

</details>
