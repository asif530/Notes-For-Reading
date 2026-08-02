# Thread contention 
occurs when two or more threads attempt to access the same shared resource simultaneously, forcing the operating system or runtime to serialize their execution. 
This performance-killing bottleneck occurs because only one thread can manipulate or lock the resource at any given moment, forcing competing threads to stall, 
block, or spin while waiting for their turn.

# Common Causes of Thread ContentionHeavy Locking: 
Multiple threads frequently trying to acquire the exact same Mutex, Semaphore, or synchronized block.
Monolithic State: Using a single global variable, cache instance, or object instance across all worker threads.
Frequent Allocations: Threads constantly competing for the global heap memory manager during simultaneous allocation or deallocation cycles.
Centralized Queues: Single producer-consumer queues that handle data across an excessively large pool of worker threads.

# Symptoms & Performance Impact
Low CPU Utilization, Low Throughput: CPU usage stays abnormally low because worker threads spend most of their lifespans asleep or blocked.
High CPU Utilization, Low Throughput: Wasted processing power caused by threads continuously spin-locking or performing intensive context-switching.
The Convoy Effect: Threads pile up waiting for a shared lock, completely eliminating the speed benefits of parallel hardware.

# Best Strategies to Resolve It (https://oneuptime.com/blog/post/2026-01-24-fix-thread-contention/view)
Reduce Lock Scope: Keep synchronized code blocks as tight as humanly possible. Perform data parsing, logging, and validations completely outside of the lock.
Use Lock Striping (Sharding): Split a single massive data structure into independent pieces. For example, use a segmented cache system where threads only 
                            lock specific buckets instead of the entire map.
Read-Write Locks: Replace basic exclusive locks with Read-Write locks if your system handles far more data reads than writes. 
                  Multiple threads can read concurrently without blocking.
Go Lock-Free: Use lock-free concurrent data structures or low-level atomic operations (like AtomicInteger or Compare-And-Swap primitives) to mutate simple metrics without overhead.
Thread-Local Storage: Isolate mutable data entirely by giving every individual thread its own isolated copy of a variable or resource.
