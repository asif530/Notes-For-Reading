**Memory Leaks**
One of the biggest misconceptions about Java is:

"**Java has Garbage Collection, so memory leaks cannot happen.**"

This is false.

Java prevents manual memory management bugs (like dangling pointers, double free), but it cannot free objects that are still reachable, 
even if your application will never use them again.

_What is a Memory Leak?_
A memory leak occurs when:
    Objects that are no longer needed are still referenced somewhere, preventing the Garbage Collector from reclaiming them.

Eventually heap usage continuously grows.
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

So the leak is logical, not physical.


**Why GC Cannot Clean It?**

Garbage Collector only removes unreachable objects.

Example

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

Since User is reachable from a GC Root
GC says
    "Someone can still access this object."

Therefore

    NOT collected (By GC)
Even if application never uses it again.

Example:

```java
public class Cache {

    static List<String> users = new ArrayList<>();

    public static void add(String name){
        users.add(name);
    }

}
```

In code this loop is called:
```java

for(int i=0;i<10_000_000;i++){
    Cache.add("User-"+i);
} 

```
Heap
Static List
    ↓
  User1
  User2
  User3
   ...
  User10000000

Nothing gets removed.
Heap keeps increasing forever.

GC runs
    ↓
Cannot collect
    ↓
Memory leak

**Memory Leak vs High Memory Usage**

_High memory usage_

Application legitimately needs memory.

Example

Video Processing

100 MB (Application hast memory usage)
  ↓
2 GB (Memory usage increased)
  ↓
Finished
  ↓
200 MB (Memory usage drops)

Perfectly normal. No leak.

_Memory Leak_

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

Application isn't actually using all that memory anymore. Objects are accidentally retained.


**Common Causes**

1. Static Collections
2. Unclosed Resources
3. Listener / Observer Leaks
4. ThreadLocal Leak
5. Cache Without Eviction
6. Infinite Growing Collections
7. Long-lived Singleton Holding References
8. ExecutorService Not Shutdown
9. Improper Equals/HashCode
10. Hibernate/JPA Persistence Context
11. ClassLoader Leak
12. Lambda / Inner Class Capturing Large Objects

_Static Collections & leak_
public class UserCache {

    static Map<Long, User> cache = new HashMap<>();

}

Every request : cache.put(id,user);
Never: remove()

Eventually

1 million users
    ↓
5 million users
    ↓
20 million users

Heap explodes.

Production fix:

Caffeine
Redis
Guava Cache
Expiry
LRU


_Unclosed Resources_
FileInputStream
Socket
Database Connection
Thread

Example: 
Open a file
```java
FileInputStream in = new FileInputStream(file);
```

But Forgot to close it
```java
in.close();
```
These are usually resource leaks rather than heap leaks, but they often lead to increased memory usage and application instability because 
native resources remain allocated.
 Correct: Try with resources
```java
try(FileInputStream in = new FileInputStream(file)){
}
```

_Listener / Observer Leaks_

Spring Events
Kafka listeners
RabbitMQ callbacks

Example:
```java
eventBus.register(listener);
```

Forgot
```java
eventBus.unregister(listener);
```

What happens is:

EventBus
   ↓
Listener
   ↓
Huge Object Graph

GC cannot remove it.

**ThreadLocal Leak**
Extremely common in web applications.

```java
ThreadLocal<UserContext>
```
Request
```java
threadLocal.set(user);
```

Forgot
```java
threadLocal.remove();
```

Thread pool
Worker Thread
    ↓
ThreadLocalMap
    ↓
UserContext

Since thread never dies. Object never dies.

Correct
```java
try{

}
finally{
    threadLocal.remove();
}
```
_Cache without eviction_
Almost similar to static. (Example one)

    Map<Key,Object>

Acts like cache. Never removes anything.

Example

    Map<String, Product>

After 1 year Contains 40 million entries


**Infinite Growing Collections**

List ,Map ,Queue ,Set

Example: logs.add(log);

Instead use one of these:
Database
Kafka
File
Rolling Queue

**Long-lived Singleton Holding References**
  
```java
@Component
List<Request>
```
Singleton bean. In application when used to contain all the request. 

So any request comes, adds every request. Never clears.

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

Classic leak.

**ExecutorService Not Shutdown**

ExecutorService pool Never shutdown().
Threads stay alive.
Each thread owns
    a) Stack
    b) ThreadLocal
    c) Buffers

Memory never released until JVM exits.

**Improper Equals/HashCode**

Implements hashcode/equals which doesnot match later on
```java
    Map.put(new User(...))
```

Later

```java
remove(new User(...))
```
Fails because equals/hashCode are wrong. Objects remain forever. Memory leaks

**Hibernate/JPA Persistence Context**

Fetched Large batch. called  _entityManager.persist()_. Everything stays in first-level cache.
Need flush() ,clear() Periodically.

Otherwise every entity remains in heap.

**ClassLoader Leak**

Mostly Tomcat,Jetty ,Spring Boot DevTools ,Application Servers are involved.
A classloader remains referenced after redeployment, keeping all loaded classes and static fields alive. Over multiple redeployments, this can exhaust heap or metaspace.

12. Lambda / Inner Class Capturing Large Objects
```java
HugeObject obj = ...

Runnable r = () -> {
System.out.println(obj);
};
```    

Runnable stored forever. HugeObject also survives.

**What Happens If Memory Leak Isn't Fixed?**

Suppose 
Heap -Xmx2G (Set by environment variable)

Initially
Heap 200 MB
After one day 600 MB
After two days 1.3 GB
After three days 1.8 GB
Eventually 2 GB
Heap full.

Stage 1 — More Frequent Garbage Collection
GC notices memory pressure.
Instead of Every 10 sec Now Every 2 sec .CPU increases. Application slows.

Stage 2 — Longer GC Pauses
GC has to scan more objects. Pause time increases. Latency increases.

Stage 3 — Throughput Drops
CPU: 70% -> 85% -> 95%
Much of that CPU is spent doing GC instead of serving requests.

Stage 4 — Full GC
Full GC runs repeatedly. Stop The World. Application freezes briefly.

Stage 5 — OutOfMemoryError
Finally _java.lang.OutOfMemoryError_ Java heap space memory out.
or
GC overhead limit exceeded

Stage 6 — Application Crash
If uncaught JVM exits

Kubernetes -> Restart
or
Systemd -> Restart

Problem repeats.

**How to Detect Memory Leaks**

Several tools and techniques help identify leaks:

Heap dump analysis using tools like Eclipse MAT or VisualVM to find objects that are retaining the most memory.
JDK tools such as jmap (capture heap dumps) and jcmd (inspect JVM state).
Java Flight Recorder (JFR) and Java Mission Control (JMC) for profiling allocations and object lifetimes.
GC logs to observe heap growth and increasing GC frequency.
Monitoring tools (Micrometer + Prometheus + Grafana) to watch JVM heap usage over time.

A classic sign is the used heap after each major GC gradually increasing instead of returning to a stable baseline.
After Full GC: 100 MB -> 180 MB -> 350 MB -> 600 MB -> 900 MB

If the application workload is similar but the post-GC heap keeps growing, it's a strong indication of a memory leak.

Interview Summary
A memory leak in Java occurs when objects that are no longer needed remain reachable, so the Garbage Collector cannot reclaim them.
Garbage collection only removes unreachable objects; it cannot determine whether a reachable object is still logically useful.
Common causes include static collections, unbounded caches, ThreadLocal misuse, listener registration without deregistration, singleton objects retaining data, JPA persistence contexts, classloader leaks, and improperly managed thread pools.
If left unresolved, memory leaks increase heap usage, trigger more frequent and longer GC cycles, reduce application throughput, and eventually cause OutOfMemoryError or severe service degradation.
The most effective way to diagnose leaks is to compare heap dumps over time and identify objects that continuously accumulate and remain strongly reachable.