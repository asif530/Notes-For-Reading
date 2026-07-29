# How Redis connection works in a non-blocking way (What is Redis and how does it work Internally => https://medium.com/@ayushsaxena823/what-is-redis-and-how-does-it-work-cfe2853eb9a9)
Workflow (Analogy)
Picture Redis as a single worker managing a mailroom with a hundred mailboxes, one mailbox per client connection. Instead of standing in front of mailbox #1 and staring at it until a letter arrives 
(that would be blocking, and wasteful if letters are arriving in mailboxes #47 and #82 instead), the worker does something smarter.
The worker walks up to a supervisor (the OS) and says "here's my list of 100 mailboxes, let me know the moment any of them get mail." That's the select()/poll() call. The worker then goes and does other useful things (or simply waits efficiently) 
instead of watching each box individually.
When mail actually arrives in, say, mailboxes #12 and #56, the supervisor taps the worker on the shoulder and says "these two have mail." The worker then goes directly to those two mailboxes, reads the letters, processes the requests inside them, 
and responds, without ever having wasted time checking empty mailboxes.

Concrete (Redis)
Say three clients, A, B, and C, connect to Redis. Client A sends a GET command, but the network is slow and the request takes a moment to arrive. Clients B and C haven't sent anything yet. 
Without multiplexing, Redis would need to check each client in sequence, potentially getting stuck waiting on B or C even though nothing is coming. 
With multiplexing, Redis registers all three sockets with select()/poll() and waits. The moment A's data physically arrives at the socket, select() returns and tells Redis "A is ready." 
Redis reads A's request, processes it in memory almost instantly (since Redis operations are fast), sends the response, and goes back to waiting on all three again. 
If B and C send data moments later, the same thing happens for them.
This is what lets a single Redis thread serve thousands of clients smoothly: it never wastes time blocked on a quiet connection, it only does work when the OS tells it there's actually something to do, and because the actual in-memory processing is so quick, 
one thread cycling through "ready" sockets one at a time is still fast enough to feel simultaneous.

# Redis as cache and no-sql database (https://medium.com/@AlexanderObregon/using-spring-boot-with-redis-for-caching-and-data-storage-53f3f8d971fb)


