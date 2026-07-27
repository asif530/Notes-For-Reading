# Race condition (https://www.geeksforgeeks.org/java/race-conditions/)
A thread race condition is a concurrency bug that occurs when two or more threads access a shared resource simultaneously, and the final outcome depends unpredictably on the timing or order of their execution. Because the operating system switches between threads at any time, operations that appear as a single step in high-level code can be split apart, leading to data corruption or erratic behavior.

Why Race Conditions Occur

A race condition happens in Java when:
1. Multiple Threads Access Shared Mutable Data

If a variable, object, file, or database is shared among threads and at least one thread modifies it, there’s a chance of conflict.

Example:

    counter++; // Not atomic → Read, Increment, Write steps

2. Lack of Proper Synchronization

If shared data is accessed without synchronization mechanisms (synchronized, Lock, volatile, etc.), threads can interleave their operations unpredictably.

Example:

Thread A reads a variable, Thread B updates it before Thread A writes, leading to overwritten or inconsistent values.
3. Non-Atomic Operations

   Even seemingly simple operations like x++ or list.add() are not atomic in Java. They involve multiple low-level steps.
   Without synchronization, another thread can interrupt these steps.
```java
class Geeks{
    int count=0;

    public void increment() {
        count++;
    }
}

public class RaceConditionExample {
    public static void main(String[] args) throws InterruptedException {
        Geeks counter = new Geeks();

        Thread t1 = new Thread(() -> {
            for (int i = 0; i < 1000; i++) {
                counter.increment();
            }
        });

        Thread t2 = new Thread(() -> {
            for (int i = 0; i < 1000; i++) {
                counter.increment();
            }
        });

        t1.start();
        t2.start();
        t1.join();
        t2.join();

        System.out.println("Final Count: " + counter.count);
    }
}
```

Explanation:

    Geeks has a shared variable count and an increment() method that increases it by 1.
    The count++ operation is not atomic, so multiple threads can interfere with each other’s updates.
    Two threads (t1 and t2) are created, each calling increment() 1000 times on the same Geeks object.
    Both threads are started, and join() is used so the main thread waits until they finish.
    The expected output is 2000, but due to race conditions the final count may be less than 2000.


Types of Race Conditions

Race conditions are generally classified into these main types:
1. Read-Modify-Write Race

Read-Modify-Write Race occurs when multiple threads read a variable, modify its value, and write it back without synchronization.
2. Check-Then-Act Race

Check-Then-Act Race occurs when a thread checks a condition and acts on it, but the condition changes before the action.

Example:

    if (list.contains(item)) {

        list.add(item); // Another thread could add it in between

    }

3. Initialization Race

   It happens when multiple threads try to initialize a shared resource at the same time.
   Example: Lazy initialization of a singleton without proper synchronization.

4. Data Visibility Race

   One thread updates a variable, but another thread sees a stale value due to caching or reordering.
   Fixed using volatile or synchronization.

5. Order Violation Race

   When the correct functioning of the program depends on operations happening in a specific order, but threads run in a different order.

How to Prevent Race Conditions
1. Use Synchronization

Wrap critical sections in synchronized blocks or methods so only one thread can access them at a time.

Example:

    public synchronized void increment() {

        count++;

    }


2. Use Locks (java.util.concurrent.locks.Lock)

Provides more flexibility than synchronized, with features like try-lock and timed locking.

Example:

    Lock lock = new ReentrantLock();

    lock.lock();

    try {

        count++;

    } finally {

        lock.unlock();

    }

3. Use Atomic Variables

Classes like AtomicInteger, AtomicLong, etc., perform thread-safe atomic operations without explicit synchronization.

Example:

    AtomicInteger count = new AtomicInteger();

    count.incrementAndGet();


4. Use volatile for Visibility

   Ensures that changes to a variable are immediately visible to other threads, avoiding stale data issues.
   Note: volatile alone does not make compound actions atomic.

Example of Handle Race Condition
```java
class Geeks{
    private int count = 0;

    // synchronized ensures only one thread executes this at a time
    public synchronized void increment() {
        count++;
    }

    public synchronized int getCount() {
        return count;
    }
}

public class RaceConditionHandled {
    public static void main(String[] args) throws InterruptedException {
        Geeks counter = new Geeks();

        Thread t1 = new Thread(() -> {
            for (int i = 0; i < 1000; i++) {
                counter.increment();
            }
        });

        Thread t2 = new Thread(() -> {
            for (int i = 0; i < 1000; i++) {
                counter.increment();
            }
        });

        t1.start();
        t2.start();

        t1.join();
        t2.join();

        System.out.println("Final Count (Handled): " + counter.getCount());
    }
}
```

Explanation:

    The Geeks class has a private count variable with increment() and getCount() methods marked synchronized, ensuring only one thread can execute them at a time.
    The increment() method increases count by 1 in a thread-safe way.
    In main(), two threads (t1 and t2) are created, each calling increment() 1000 times on the same Geeks object.
    Both threads are started, and join() is used so the main thread waits until they finish execution.
    The final count is printed and will always be 2000, because synchronization prevents race conditions.
