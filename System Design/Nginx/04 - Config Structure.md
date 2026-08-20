# Module 4: Understanding the Config File Structure

*Part of the [[Nginx for Beginners]] series.*

---

## Why this matters

We now know **where** the config files live (Module 3). This module is about learning to actually **read and write** what's inside them. Once this clicks, every Nginx config file you ever look at — no matter how big or scary — will just look like a nested set of familiar shapes.

---

## The building block: directives

The smallest unit of Nginx configuration is a **directive** — a single instruction, ending in a semicolon:

```nginx
listen 80;
```

> 📘 **Term: Directive**
> A single configuration line telling Nginx to do or set something. Directives always end with a semicolon (`;`) — forgetting it is one of the most common beginner mistakes and will cause Nginx to refuse to start.

Most directives follow the pattern: `directive_name value1 value2 ...;`

```nginx
root /var/www/html;
index index.html index.htm;
server_name example.com;
```

Here, `root`, `index`, and `server_name` are directive names, and everything after them (up to the `;`) is the value(s) being assigned.

---

## The building block: blocks (a.k.a. "contexts")

Directives on their own aren't very powerful — the real structure comes from **blocks**, which group directives together and give them scope, using curly braces `{ }`.

```nginx
server {
    listen 80;
    server_name example.com;
}
```

> 📘 **Term: Block / Context**
> A section of configuration wrapped in `{ }` that groups related directives together and defines *where* they apply. Nginx people use "block" and "context" interchangeably — both just mean "a `{ }` section."

Blocks can **nest inside each other**, and this nesting is the whole key to understanding Nginx configs. There are three levels you'll deal with constantly:

```nginx
http {                          # applies to all web traffic handling

    server {                    # one specific "website"
        listen 80;
        server_name example.com;

        location / {             # one specific URL path pattern
            root /var/www/html;
        }

        location /images/ {
            root /var/www/assets;
        }
    }

    server {                    # a second, different website
        listen 80;
        server_name another-site.com;
    }
}
```

Let's break these three levels down individually.

---

## The `http` block

Everything related to handling **web (HTTP) traffic** lives inside a single `http { }` block. This is typically the outermost block you'll actively edit — in fact, this is exactly what `nginx.conf` sets up for you, then `include`s everything else *inside* it (remember Module 3?).

> 📘 **Term: HTTP**
> HyperText Transfer Protocol — the standard set of rules browsers and servers use to request and send web content. When we say "HTTP traffic," we simply mean normal web requests.

You generally won't touch the `http` block directly very often — but it's worth knowing it's the "container" that holds every `server` block you define.

---

## The `server` block

A `server { }` block represents **one website** (or, more precisely, one combination of IP/port/domain that Nginx should respond to). This is where you'll spend most of your time.

```nginx
server {
    listen 80;
    server_name example.com;
    root /var/www/example;
}
```

Common directives you'll see directly inside a `server` block:

- **`listen`** — which port to listen on (e.g., `80` for regular HTTP, `443` for HTTPS).
- **`server_name`** — which domain name(s) this block should respond to.
- **`root`** — the default folder to serve files from for this site.

We'll build a complete, real `server` block hands-on in the very next module.

---

## The `location` block

A `location { }` block lives *inside* a `server` block, and matches a specific **URL path pattern**, letting you handle different parts of a website differently.

```nginx
location /images/ {
    root /var/www/assets;
}
```

This says: *"for any request whose path starts with `/images/`, serve files from `/var/www/assets` instead of the site's default root."*

We'll go much deeper on the different ways `location` can match paths (exact match, prefix match, regex match) in Module 6 — for now, just recognize the shape: `location <pattern> { ... }`.

---

## Directive inheritance: a subtle but important idea

Directives set in an **outer block** are inherited by the blocks nested inside it, unless the inner block overrides them.

```nginx
http {
    gzip on;                     # applies to every server below

    server {
        listen 80;
        server_name example.com;
        # gzip is still "on" here — inherited from the http block
    }
}
```

This lets you set something once at a high level (like "compress all responses") instead of repeating it in every single `server` block. We'll see this pattern again with gzip specifically in Module 11.

---

## Comments

Anything after a `#` on a line is a **comment** — Nginx ignores it completely. Use comments freely to leave notes for your future self (or teammates) about *why* a config does something.

```nginx
# Redirect legacy blog URLs to the new domain
location /blog/ {
    return 301 https://newsite.com/blog/;
}
```

---

## Checking your syntax before applying changes: `nginx -t`

This is, without exaggeration, one of the most valuable habits you can build. Before reloading Nginx with a config change, always test it first:

```bash
sudo nginx -t
```

> 📘 **Term: `nginx -t`**
> A command that tells Nginx to *test* its configuration files for syntax errors, without actually applying or reloading anything. It reports exactly which file and line number has a problem, if any.

A successful check looks like:

```
nginx: the configuration file /etc/nginx/nginx.conf syntax is ok
nginx: configuration file /etc/nginx/nginx.conf test is successful
```

If there's a mistake (like a missing semicolon), you'll get a clear error pointing at the exact file and line — far better than reloading blindly and taking your site down. Get in the habit: **edit → `nginx -t` → reload**, every single time.

---

## Quick recap

- **Directives** are single settings, always ending in `;`.
- **Blocks** (`{ }`) group directives and create nested "contexts": `http` → `server` → `location`.
- `http` = all web traffic, `server` = one website, `location` = one URL path pattern within that site.
- Inner blocks **inherit** directives from outer blocks, unless they override them.
- `#` starts a comment.
- Always run `sudo nginx -t` to check for syntax errors *before* reloading.

---

**Next up:** [[05 - First Server Block|Module 5: Your First Server Block]] — time to put this structure to use and serve a real, custom website.
