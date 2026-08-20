# Module 9: Load Balancing Basics

*Part of the [[Nginx for Beginners]] series.*

---

## Why would you ever need more than one backend?

In Module 8, we forwarded traffic to a single app running on one port (`localhost:3000`). That's great — until your app becomes popular and one instance simply can't keep up with demand, or until that one instance crashes and takes your entire site down with it.

The common solution: run **multiple copies** of your application (remember the term **instance** from Module 1), and let Nginx spread incoming traffic across all of them.

> 📘 **Term: Load Balancing**
> Distributing incoming requests across multiple backend instances of the same application, instead of sending everything to just one. This improves both **performance** (no single instance is overwhelmed) and **reliability** (if one instance goes down, the others keep serving traffic).

This is genuinely one of Nginx's superpowers, and it builds directly on everything from Module 8 — we're just proxying to a *group* of backends instead of one.

---

## The `upstream` block

Nginx uses a dedicated block, `upstream`, to define a named group of backend servers:

```nginx
upstream myapp_backend {
    server localhost:3000;
    server localhost:3001;
    server localhost:3002;
}
```

> 📘 **Term: `upstream`**
> A block (defined at the same level as `server`, directly inside `http`) that groups multiple backend addresses under one name, so they can be referenced together as a single load-balanced target.

Here, we're imagining three copies of the same app running on ports `3000`, `3001`, and `3002` on the same machine (in a real production setup, these would more likely be separate machines entirely — the concept is identical either way).

Then, in your `server` block, you `proxy_pass` to the **upstream group's name** instead of a single address:

```nginx
server {
    listen 80;
    server_name myapp.local;

    location / {
        proxy_pass http://myapp_backend;

        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

Notice this is *exactly* the same `proxy_set_header` boilerplate from Module 8 — load balancing doesn't change any of that, it just changes what `proxy_pass` points at. Every incoming request now gets routed to **one** of the three servers in `myapp_backend`, according to a load-balancing method — which brings us to the next question: *how* does Nginx pick which one?

---

## Load balancing methods

### Round robin (the default — no configuration needed)

```nginx
upstream myapp_backend {
    server localhost:3000;
    server localhost:3001;
    server localhost:3002;
}
```

> 📘 **Term: Round Robin**
> A load balancing strategy that cycles through the list of servers in order, sending each new request to the *next* one in the rotation, looping back to the start once it reaches the end. Simple, predictable, and fair when all requests are roughly similar in cost.

With three servers, request 1 goes to `3000`, request 2 to `3001`, request 3 to `3002`, request 4 back to `3000`, and so on. This is the default behavior — you get it automatically just by listing multiple `server` entries in an `upstream` block, with no extra directive required.

### Least connections

```nginx
upstream myapp_backend {
    least_conn;
    server localhost:3000;
    server localhost:3001;
    server localhost:3002;
}
```

> 📘 **Term: Least Connections**
> A load balancing strategy that sends each new request to whichever backend currently has the *fewest active, still-in-progress* connections — rather than blindly rotating. Useful when some requests take much longer to process than others, since round robin alone could accidentally pile up several slow requests on the same unlucky server.

Think of it like choosing the shortest checkout line at a grocery store, rather than just alternating between registers regardless of how long each line actually is.

### IP hash

```nginx
upstream myapp_backend {
    ip_hash;
    server localhost:3000;
    server localhost:3001;
    server localhost:3002;
}
```

> 📘 **Term: IP Hash**
> A load balancing strategy that consistently sends requests from the *same client IP address* to the *same backend server* every time (as long as the group of servers doesn't change), instead of spreading each individual request around.

Why would you want that instead of spreading load evenly? Because some applications keep a bit of information in memory on whichever server handled a user's *first* request (this is called having **session state**), and if that same user's *next* request lands on a *different* server that doesn't have that memory, things can break — e.g., getting logged out unexpectedly.

> 📘 **Term: Session / Session State**
> Data an application remembers about a specific user across multiple requests — like "this user is logged in as Alice." If that data only lives in one server's memory, all of that user's requests need to consistently land on the *same* server, or the app needs a shared/external way to store session data (like a shared database) so any server can look it up. `ip_hash` is a simple, config-only fix for the first case.

---

## Marking a backend as backup or down

You can fine-tune how each individual server in the group behaves:

```nginx
upstream myapp_backend {
    server localhost:3000;
    server localhost:3001;
    server localhost:3002 backup;
    server localhost:3003 down;
}
```

- **`backup`** — this server only receives traffic if *all* the non-backup servers are unavailable. Useful for keeping a spare instance on standby.
- **`down`** — marks a server as permanently out of rotation, without deleting the line — handy for temporarily taking an instance out for maintenance while keeping the config easy to restore.

---

## Health checks (a brief mention)

Nginx's free/open-source version automatically stops sending traffic to a backend that fails to respond (after a configurable number of failed attempts), and will periodically retry it. Fully-featured **active health checks** — where Nginx proactively pings each backend on a schedule to check it's healthy *before* real traffic hits it — is a feature of the paid **NGINX Plus** product mentioned back in Module 1. For most beginner and small-scale setups, the free version's automatic failure detection is enough to get started.

---

## Quick recap

- **Load balancing** spreads traffic across multiple instances of the same app for performance and reliability.
- Define a group of backends with an `upstream` block, then `proxy_pass` to its name.
- **Round robin** (default) — cycles through servers in order.
- **Least connections** (`least_conn;`) — sends traffic to whichever server is least busy right now.
- **IP hash** (`ip_hash;`) — keeps the same client consistently on the same server, useful for session state.
- `backup` and `down` let you fine-tune individual servers within the group.

---

**Next up:** [[10 - HTTPS and SSL|Module 10: HTTPS & SSL/TLS with Nginx]] — we'll secure our site with a real certificate and force all traffic over HTTPS.
