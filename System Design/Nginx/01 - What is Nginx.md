# Module 1: What is Nginx, really?

*Part of the [[Nginx for Beginners]] series.*

---

## Let's start with the absolute basics: what is a "web server"?

Imagine you own a small shop. Customers (let's call them **clients**) walk in and ask for things: "Do you have this book?", "Show me your menu", "Give me today's newspaper." You, the shopkeeper, look at what they're asking for, go grab it, and hand it over.

A **web server** does exactly this, except the "customers" are web browsers (or apps), and the "requests" are things like *"give me the homepage"* or *"give me this image."*

> 📘 **Term: Web Server**
> A program that listens for requests coming over the internet (or a network) and responds by sending back data — usually a web page, an image, a file, or a piece of information from an app.

So when you type `google.com` into your browser, your browser sends a request to a server somewhere, and that server sends back the HTML that makes up the Google homepage. **Nginx** is one of the most popular programs used to run that "shop."

---

## So what exactly is Nginx?

**Nginx** (pronounced "**engine-x**", not "en-jinx" — this trips up almost everyone at first!) is a piece of software that can act as:

1. A **web server** — serving files (HTML, images, videos) directly to visitors.
2. A **reverse proxy** — standing in front of other applications and forwarding traffic to them (we'll dig into this deeply in a later module).
3. A **load balancer** — spreading incoming traffic across multiple backend servers so no single one gets overwhelmed.
4. An **API gateway** — a single entry point that routes API requests to the right backend service.

> 📘 **Term: Reverse Proxy**
> A server that sits *in front of* your actual application and forwards client requests to it — like a receptionist who takes your request and passes it to the right department, instead of you walking straight to that department yourself. We'll explore this fully in Module 8.

> 📘 **Term: Load Balancer**
> A component that distributes incoming traffic across multiple servers, so that one server isn't doing all the work while others sit idle. Covered in depth in Module 9.

It was originally created in 2004 by **Igor Sysoev**, a Russian software engineer, specifically to solve something called the **"C10k problem"** — the challenge of handling **10,000 concurrent connections** efficiently on a single server. At the time, most web servers struggled badly with that kind of load. Nginx handled it gracefully, and that's a big reason it became so popular.

---

## Nginx vs Apache — why does everyone compare them?

**Apache HTTP Server** is the other big, famous web server — actually older and historically more widely used than Nginx. People constantly compare the two, so it's worth understanding the core difference early on.

> 📘 **Term: Concurrent Connections**
> The number of client requests a server is handling *at the same time*. A server that handles thousands of concurrent connections well is considered highly scalable.

The key architectural difference:

- **Apache** traditionally creates a **new process or thread for every incoming connection**. This works fine for moderate traffic, but as thousands of users connect at once, the server has to juggle thousands of processes/threads — which eats up a lot of memory and CPU.

- **Nginx** uses an **event-driven, asynchronous architecture**. Instead of spinning up a new process per connection, a small number of "worker" processes handle *many* connections at once, efficiently switching between them without the heavy overhead.

> 📘 **Term: Event-Driven Architecture**
> A design where a program handles many tasks by reacting to events (like "a new request just arrived" or "data just finished sending") using a single, efficient loop — rather than dedicating a separate process or thread to each individual task.

**In plain English:** think of Apache like a restaurant that assigns one dedicated waiter to each table, even if that table is just sitting there deciding what to order. Nginx is like one super-efficient waiter who circles the whole restaurant, quickly checking in on each table exactly when it needs something, instead of being stuck standing at one table the whole time.

This is *the* single biggest reason Nginx tends to use less memory and handle high traffic better, especially when a lot of connections are just idle or slow (like someone on a bad mobile connection slowly loading a page).

|                   | Apache                                                  | Nginx                                                 |
|-------------------|---------------------------------------------------------|-------------------------------------------------------|
| Model             | Process/thread-per-connection                           | Event-driven, async                                   |
| Best at           | Dynamic content processing (via modules like `mod_php`) | Serving static files, reverse proxy, high concurrency |
| Config style      | `.htaccess` per-directory overrides                     | Centralized config files                              |
| Memory under load | Grows with connections                                  | Stays relatively flat                                 |

> 📘 **Term: Static vs Dynamic Content**
> **Static content** is a file that doesn't change — like an image, a CSS file, or a plain HTML page — the server just hands it over as-is. **Dynamic content** is generated on the fly, usually by running code (e.g., a Python or Java app building an HTML page based on a database query) before sending a response.

Neither is strictly "better" in every case — but Nginx's strengths line up really well with how modern web applications are built (lots of microservices, APIs, and static assets), which is a big reason it has become so dominant.

---

## Where is Nginx actually used in the real world?

You'll run into Nginx constantly, often without realizing it. Common real-world roles:

- **Serving static websites** — plain HTML/CSS/JS sites, or the built output of a React/Vue/Angular app.
- **Reverse proxy in front of an application server** — e.g., a Node.js, Django, or Spring Boot app runs on some internal port, and Nginx sits in front of it, handling incoming internet traffic and forwarding it along.
- **Load balancer** — spreading traffic across multiple copies ("instances") of the same backend app for scalability and reliability.
- **API Gateway** — a single door that routes `/users/*` requests to one microservice and `/orders/*` requests to another.
- **TLS/SSL termination** — handling all the encryption/decryption work for HTTPS, so backend apps don't have to (more in Module 10).
- **Caching layer** — storing copies of frequently-requested content so it can be served instantly without redoing expensive work (more in Module 11).

> 📘 **Term: Instance**
> One running copy of an application. If you run three copies of your app to handle more traffic, you have three instances.

You'll find Nginx (or the closely related **NGINX Plus**, its commercial version) powering huge chunks of the internet — it's consistently one of the most-used web server technologies in surveys of live websites worldwide, alongside Apache and newer options like Caddy.

---

## Quick recap

- A **web server** listens for requests and sends back responses.
- **Nginx** is a high-performance web server that can *also* act as a reverse proxy, load balancer, and API gateway.
- Its **event-driven architecture** lets it handle huge numbers of connections efficiently, which is why it beats older servers like **Apache** in high-concurrency situations.
- In practice, Nginx usually sits at the "front door" of a web system — serving static files directly, and forwarding everything else to the right backend app.

---

**Next up:** [[02 - Installing Nginx|Module 2: Installing Nginx]] — we'll get Nginx actually running on your machine and confirm it's alive.
