# socket 
is basically an endpoint for network communication, think of it as a virtual "plug" through which data flows between a client (like your laptop) and a server (like Redis). 
When your app connects to Redis, a socket is created for that specific connection, and each connected client gets its own socket.

# system call 
is a request your program makes to the operating system to do something it can't do on its own, like reading from a network connection, writing to disk, or opening a file. The OS handles the low-level work and reports back to the program.

# Blocking 
means that when a program makes a request (like "read data from this socket"), it stops and waits right there until that request is fulfilled, doing nothing else in the meantime. 
If Redis tried to read from one client's socket in a blocking way, it would be stuck waiting for that one client, even if a hundred other clients had data ready to send.

# I/O multiplexing 
is a technique that avoids this problem. Instead of waiting on one socket at a time, the program asks the OS to watch a whole group of sockets simultaneously, and the OS only notifies the program once one or more of them actually have data ready.
**select()** and **poll()** are specific system calls (functions provided by the OS) that implement this watching behavior. You hand them a list of sockets you care about, and they return only when something interesting happens on one of them.

