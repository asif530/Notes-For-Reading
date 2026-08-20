# Module 12: Security Basics

*Part of the [[Nginx for Beginners]] series.*

---

## Why this matters

Nginx usually sits at the very front door of your entire system — every request from the outside world passes through it first (recall the "receptionist" framing from Module 8). That makes it a natural, convenient place to stop obviously bad behavior *before* it ever reaches your application or database, rather than making every backend app reinvent the same protections.

This module covers three beginner-friendly, high-value security basics: **rate limiting**, **access restriction**, and **hiding version information**.

---

## Rate limiting: stopping abuse

Imagine someone hammering your login page thousands of times per second, trying to guess passwords, or a buggy script accidentally spamming your API. **Rate limiting** puts a speed limit on how often a given client can make requests.

> 📘 **Term: Rate Limiting**
> Restricting how many requests a client is allowed to make within a given time window, and rejecting (or delaying) requests beyond that limit. A common defense against brute-force attacks, scraping, and simple traffic spikes from a single misbehaving source.

Like gzip and proxy caching in earlier modules, rate limiting is configured in two steps: first **define** the limit as a named zone (in the `http` block), then **apply** it wherever you want it enforced.

```nginx
http {
    limit_req_zone $binary_remote_addr zone=mylimit:10m rate=5r/s;
    ...
}
```

> 📘 **Term: `limit_req_zone`**
> Defines a named rate-limiting rule: how requests are grouped (here, `$binary_remote_addr` — by client IP address, in a compact binary form), how much memory to track them in (`10m`), and the allowed rate (`5r/s` = 5 requests per second, per IP).

Then apply it inside the relevant `location` block:

```nginx
location /login {
    limit_req zone=mylimit burst=10 nodelay;
    proxy_pass http://myapp_backend;
}
```

> 📘 **Term: `limit_req`**
> Applies a previously-defined rate limit zone to a specific location. The `burst` parameter allows short bursts above the steady rate (here, up to 10 extra requests queued momentarily) before rejecting requests, since real traffic is rarely perfectly smooth. `nodelay` tells Nginx to process burst requests immediately rather than artificially slowing them down, while still enforcing the overall limit.

Requests that exceed the limit get an automatic `503 Service Unavailable` response, protecting your backend from ever seeing that flood at all. Notice this is a great example of applying a strict limit only to a *sensitive* path like `/login`, while leaving the rest of the site (say, `/`, from Module 6's routing) unrestricted.

---

## Blocking IPs and restricting access

Sometimes you know exactly *who* shouldn't be allowed in — a known bad actor's IP, or conversely, you want to lock a path down to only *specific* trusted IPs (like an internal admin panel that should never be reachable from the public internet).

```nginx
location /admin/ {
    allow 203.0.113.10;
    allow 192.168.1.0/24;
    deny all;

    proxy_pass http://myapp_backend;
}
```

> 📘 **Term: `allow` / `deny`**
> Directives that explicitly permit or block access based on client IP address. Rules are checked **in order**, top to bottom, and the first matching rule wins — which is why `deny all;` is almost always placed *last*, acting as a catch-all "block everyone else" once the explicitly trusted IPs have already been allowed through above it.

> 📘 **Term: CIDR Notation**
> A compact way of writing a *range* of IP addresses, like `192.168.1.0/24`, instead of listing every individual address. The number after the slash indicates how many addresses are included — `/24` here covers 256 consecutive addresses (`192.168.1.0` through `192.168.1.255`), commonly used to describe "everyone on this local network."

This pattern — restrict a sensitive `location` to trusted IPs only — is a simple, effective first line of defense for admin panels, internal dashboards, or monitoring endpoints you never intended to be public in the first place.

You can also block based on other request details (like a suspicious `User-Agent` header) using `map` and conditional logic, but `allow`/`deny` by IP covers the vast majority of beginner needs.

---

## Hiding the Nginx version banner

By default, Nginx happily tells anyone who asks exactly which version it's running — visible in the `Server` response header, and on default error pages.

```bash
curl -I http://mysite.local
```

```
Server: nginx/1.18.0
```

> 📘 **Term: Information Disclosure**
> A (typically minor) security weakness where a system reveals more internal detail than necessary — here, the exact software version — which can help an attacker look up known vulnerabilities for that specific version instead of probing blindly.

Turning this off is a single line, set in the `http` block:

```nginx
http {
    server_tokens off;
    ...
}
```

> 📘 **Term: `server_tokens`**
> Controls whether Nginx includes its version number in response headers and default error pages. Setting it `off` doesn't hide the fact that you're running *some* web server (that's essentially unavoidable), but it does stop broadcasting the *exact version*, removing one small, free piece of reconnaissance information from potential attackers.

It's worth being honest about scope here: this alone won't stop a determined attacker (there are other ways to fingerprint software), and it's not a substitute for actually keeping Nginx updated. But it's a free, zero-downside line to add, and a very commonly recommended baseline hardening step.

---

## Putting it together

```nginx
http {
    server_tokens off;
    limit_req_zone $binary_remote_addr zone=mylimit:10m rate=5r/s;

    server {
        listen 80;
        server_name myapp.local;

        location /login {
            limit_req zone=mylimit burst=10 nodelay;
            proxy_pass http://myapp_backend;
        }

        location /admin/ {
            allow 192.168.1.0/24;
            deny all;
            proxy_pass http://myapp_backend;
        }

        location / {
            proxy_pass http://myapp_backend;
        }
    }
}
```

---

## Quick recap

- **Rate limiting** (`limit_req_zone` + `limit_req`) caps how many requests a client can make per second, protecting sensitive endpoints like login pages from abuse.
- **`allow` / `deny`** restrict a `location` to specific trusted IP addresses (or ranges, via **CIDR notation**), checked top-to-bottom.
- **`server_tokens off;`** stops Nginx from broadcasting its exact version number — a small, free hardening step.
- None of these replace keeping Nginx updated and properly configured overall — they're complementary layers, not a complete security strategy on their own.

---

**Next up:** [[13 - Logs and Troubleshooting|Module 13: Logs & Troubleshooting]] — we'll learn to read Nginx's logs like a detective and diagnose common errors.
