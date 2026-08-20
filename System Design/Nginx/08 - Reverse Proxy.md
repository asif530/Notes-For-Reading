# Module 8: Nginx as a Reverse Proxy

*Part of the [[Nginx for Beginners]] series.*

---

## Recap: what is a reverse proxy again?

We first mentioned this back in Module 1, with the receptionist analogy — let's expand on it properly now, because this is arguably **the single most common real-world use of Nginx** in modern web systems.

> 📘 **Term: Reverse Proxy**
> A server that sits *in front of* one or more backend applications, receiving all incoming client requests itself and forwarding them along to the right backend, then relaying the backend's response back to the client. The client never talks to the backend directly — it only ever talks to the proxy.

Picture a company office: visitors don't wander the halls looking for the right person themselves — they check in at the **reception desk**, and the receptionist calls the right department and directs them there. The visitor never needs to know the internal office layout. Nginx-as-reverse-proxy plays exactly this role for network traffic.

> ⚠️ **Reverse proxy vs. forward proxy** — worth a quick mental note, since the word "proxy" alone is ambiguous: a **forward proxy** sits in front of *clients*, hiding who's making the request (common for corporate networks or VPNs). A **reverse proxy** sits in front of *servers*, hiding what's actually serving the response. Nginx is almost always used in the reverse role.

---

## Why put a proxy in front of your app at all?

It might seem simpler to just let your Node.js/Django/Spring Boot app talk directly to the internet. In practice, there are several strong reasons not to:

- **A single, consistent entry point** — one place to manage domains, TLS certificates, and routing, regardless of how many backend apps or languages you're running behind it.
- **TLS/SSL termination** — Nginx handles all the HTTPS encryption/decryption work, so your app itself only ever has to deal with plain, simple HTTP internally (full details in Module 10).
- **Load balancing** — spreading traffic across multiple copies of your app (Module 9).
- **Buffering slow clients** — Nginx can absorb a slow, unreliable client connection and only forward a request to your app once it's fully received, protecting your app from getting bogged down by slow uploads.
- **Security** — your actual application server never needs to be directly exposed to the internet at all; only Nginx is.
- **Serving static files efficiently** — Nginx can serve images/CSS/JS directly (as we did in Module 5) while forwarding only the dynamic parts to your app, so your app isn't wasting resources on simple file delivery.

---

## The core directive: `proxy_pass`

We saw a preview of this in Module 6. Here's a complete, working example, assuming you have some app (Node.js, Python, Java — doesn't matter which) running locally on port `3000`:

```nginx
server {
    listen 80;
    server_name myapp.local;

    location / {
        proxy_pass http://localhost:3000;
    }
}
```

> 📘 **Term: `proxy_pass`**
> The directive that actually performs the forwarding — it tells Nginx "don't try to find a file for this request, instead send it to this other address and relay back whatever comes back."

> 📘 **Term: Backend**
> The actual application server doing the real work behind a reverse proxy — as opposed to the proxy itself, which is sometimes called the "frontend" in this context (not to be confused with frontend as in "browser-side UI code," a different, unrelated use of the word).

With this config, a request to `http://myapp.local/dashboard` arrives at Nginx, and Nginx quietly forwards it to `http://localhost:3000/dashboard`, then sends the app's response straight back to the visitor — who never sees or knows about port `3000` at all.

---

## Why headers matter: `proxy_set_header`

Here's a subtlety that trips up almost every beginner: **once Nginx forwards a request, the backend app sees the request as coming from Nginx itself, not from the original visitor**, unless we explicitly tell Nginx to pass along the real details.

> 📘 **Term: HTTP Header**
> A small piece of metadata attached to an HTTP request or response, carrying extra information beyond the main content — like the domain being requested (`Host`, from Module 7), the visitor's browser type, or (as we're about to fix) the visitor's real IP address.

Without any extra configuration, your backend app's logs would show every single request as coming from `127.0.0.1` (Nginx itself) — completely useless if you ever need to know your *actual* visitors' IP addresses, e.g., for analytics, rate limiting, or abuse prevention.

The fix — a near-universal, "always include this" block:

```nginx
server {
    listen 80;
    server_name myapp.local;

    location / {
        proxy_pass http://localhost:3000;

        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

Let's break each of these down:

> 📘 **Term: `proxy_set_header`**
> Adds or overrides a header on the request *before* Nginx forwards it to the backend — this is how we pass along information the backend needs but wouldn't otherwise have.

- **`Host $host`** — passes along the original domain the visitor requested (from Module 7), so the backend app knows what domain it's being accessed as, rather than seeing `localhost:3000`.
- **`X-Real-IP $remote_addr`** — passes the visitor's actual IP address. `$remote_addr` is a built-in Nginx variable (recall `$uri` from Module 5) holding the IP of whoever connected directly to Nginx.
- **`X-Forwarded-For $proxy_add_x_forwarded_for`** — a standard, widely-recognized header convention for "here's the chain of IP addresses this request passed through," useful when there are multiple proxies in front of an app (common in cloud setups).
- **`X-Forwarded-Proto $scheme`** — tells the backend whether the *original* request was `http` or `https`, since internally, the connection from Nginx to the backend is often plain HTTP even when the visitor used HTTPS (this becomes very relevant once we cover TLS termination in Module 10).

> 📘 **Term: `X-Forwarded-*` headers**
> A family of conventional (though non-official-standard) HTTP headers used specifically to pass along information about the original client request through one or more proxy hops — almost every backend web framework knows how to read these to reconstruct the "real" details of a request.

Most backend frameworks (Express, Django, Spring Boot, etc.) have built-in support for reading these headers correctly, often needing a small "trust the proxy" configuration flag on the app side — worth checking your framework's docs when you set this up for real.

---

## A more realistic full example

Combining what we now know — routing different paths to different places (Module 6), plus proxying:

```nginx
server {
    listen 80;
    server_name myapp.local;

    # Serve static assets directly — no need to bother the backend app
    location /static/ {
        root /var/www/myapp;
    }

    # Forward everything else to the running application
    location / {
        proxy_pass http://localhost:3000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

This is an extremely common real-world shape: Nginx handles static files directly (fast, efficient, no need to trouble the app), and transparently proxies everything dynamic to the actual application.

---

## Quick recap

- A **reverse proxy** sits in front of your real application, forwarding requests to it and relaying back responses — the client never talks to the app directly.
- `proxy_pass http://localhost:PORT;` is the core directive that performs the forwarding.
- Without `proxy_set_header`, your backend loses visibility into the original request's real `Host` and the visitor's real IP.
- The standard header set (`Host`, `X-Real-IP`, `X-Forwarded-For`, `X-Forwarded-Proto`) should be considered close to mandatory boilerplate any time you use `proxy_pass`.

---

**Next up:** [[09 - Load Balancing|Module 9: Load Balancing Basics]] — instead of forwarding to just one backend, we'll spread traffic across several copies of the same app.
