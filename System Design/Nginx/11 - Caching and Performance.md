# Module 11: Caching & Performance

*Part of the [[Nginx for Beginners]] series.*

---

## The idea behind caching

Imagine a librarian who, every single time someone asks for a popular book's summary, walks to the archive, re-reads the entire book, and writes a fresh summary from scratch — even though she wrote the exact same summary ten minutes ago for the last visitor. Obviously wasteful. A smarter librarian writes the summary **once**, keeps a copy at the front desk, and just hands out that copy to everyone who asks — only redoing the work if the book actually changes.

That's caching.

> 📘 **Term: Caching**
> Storing a copy of the result of some work (a rendered page, a database query, a whole file) so that future requests for the same thing can be answered instantly from the stored copy, instead of redoing the expensive work every single time.

Nginx can act as this "front desk" in two related but distinct ways: caching responses from a **backend app** (proxy caching), and simply serving **static files** efficiently with the right caching instructions for browsers. Let's cover both.

---

## Proxy caching: caching a backend's responses

Recall from Module 8 that when Nginx proxies to a backend app, every single request normally gets forwarded and freshly processed by that app — even if a thousand visitors are all requesting the exact same unchanged page. `proxy_cache` lets Nginx store the backend's response and reuse it for subsequent identical requests, without bothering the backend at all.

First, define **where** cached data gets stored — this goes in the `http` block, alongside where `upstream` blocks live (Module 9):

```nginx
http {
    proxy_cache_path /var/cache/nginx levels=1:2 keys_zone=my_cache:10m max_size=1g inactive=60m;
    ...
}
```

> 📘 **Term: `proxy_cache_path`**
> Declares a location on disk for storing cached responses, along with settings like how much memory to use for tracking cache keys and how long unused entries stick around before being cleaned up. Think of this as "setting up the front desk shelf" before we start putting anything on it.

Breaking down the key parts: `keys_zone=my_cache:10m` names this cache zone `my_cache` and reserves `10m` of memory for tracking *which* entries exist (not the entries themselves, which live on disk); `max_size=1g` caps total disk usage at 1GB; `inactive=60m` removes entries that haven't been requested in 60 minutes.

Then, **use** that cache zone inside a `location` block:

```nginx
server {
    listen 80;
    server_name myapp.local;

    location / {
        proxy_pass http://myapp_backend;
        proxy_cache my_cache;
        proxy_cache_valid 200 10m;

        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

> 📘 **Term: `proxy_cache_valid`**
> Specifies how long a cached response should be considered "fresh" for a given status code — here, `200 10m` means successful (`200 OK`) responses stay valid in the cache for 10 minutes before Nginx will go back to the backend for a new copy.

With this in place, the *first* visitor to a page triggers a real request to the backend, and Nginx stores that response. Every visitor after that, for the next 10 minutes, gets served instantly straight from Nginx's cache — the backend app never even finds out about them.

> ⚠️ **Careful with dynamic, personalized content.** Proxy caching is fantastic for content that's the same for everyone (a blog post, a product listing) — but dangerous for anything personalized (a logged-in user's dashboard, account details), since you risk serving User A's cached page to User B. Nginx has ways to key the cache by things like cookies or exclude certain paths entirely, but as a beginner rule of thumb: only cache genuinely public, non-personalized responses until you're comfortable with the finer controls.

---

## Static file caching: telling browsers to remember files themselves

There's a second, simpler, and extremely common form of "caching" that doesn't even involve Nginx storing anything itself — it's about telling the **visitor's own browser** to hang onto a copy of a file (like a logo image or a CSS stylesheet) so it doesn't even need to ask your server again next time.

```nginx
location ~* \.(jpg|jpeg|png|gif|css|js|ico)$ {
    root /var/www/myapp;
    expires 30d;
    add_header Cache-Control "public, immutable";
}
```

(Notice the regex `location` pattern here — exactly the kind we learned in Module 6, matching any request ending in one of these common static file extensions.)

> 📘 **Term: `expires`**
> Sets how long a browser should consider a file "fresh" and safe to reuse from its own local cache without even asking the server again. `expires 30d;` tells browsers "you can reuse this file for 30 days without checking back with me."

> 📘 **Term: `Cache-Control` header**
> A response header giving browsers (and any other caches sitting in between, like a CDN) instructions on how a piece of content should be cached. `public` means it can be cached by anyone, including shared caches, not just the individual visitor's browser. `immutable` tells the browser this exact file will never change at this URL — a common trick is to include a version or hash in filenames (e.g., `style.a3f9c1.css`) so that when the file *does* change, it simply gets a new URL, and old cached copies become irrelevant rather than stale.

This is hugely effective for performance: once a visitor has loaded your site's logo or stylesheet once, their browser won't even *ask* your server for it again for the next 30 days, on every subsequent page load or visit.

---

## Compression with gzip

A completely different, but equally important, performance technique: shrinking response sizes before sending them over the network.

> 📘 **Term: gzip**
> A widely-supported compression format. Nginx can compress a response (like an HTML, CSS, or JSON file) before sending it, and virtually all browsers automatically understand it and decompress it on arrival — the visitor doesn't do anything differently, but the data traveling over the network is significantly smaller, meaning pages load faster, especially on slower connections.

Recall the concept of **directive inheritance** from Module 4 — gzip settings are a textbook example of something you set once, high up in the `http` block, so every site benefits automatically:

```nginx
http {
    gzip on;
    gzip_types text/css application/javascript application/json text/html;
    gzip_min_length 256;
    ...
}
```

- **`gzip on;`** — turns compression on.
- **`gzip_types`** — which content types are worth compressing. Notably, common image formats (JPEG, PNG) and videos are usually *already* compressed internally, so re-compressing them wastes CPU for little to no benefit — that's why this list focuses on text-based formats like CSS, JavaScript, JSON, and HTML, which compress extremely well.
- **`gzip_min_length`** — don't bother compressing very small responses; the overhead isn't worth it for tiny files.

---

## Putting it together

A realistic combined snippet, layering everything from this module onto what we built in Module 8:

```nginx
http {
    gzip on;
    gzip_types text/css application/javascript application/json text/html;
    proxy_cache_path /var/cache/nginx levels=1:2 keys_zone=my_cache:10m max_size=1g inactive=60m;

    server {
        listen 80;
        server_name myapp.local;

        location ~* \.(jpg|jpeg|png|gif|css|js|ico)$ {
            root /var/www/myapp;
            expires 30d;
            add_header Cache-Control "public, immutable";
        }

        location / {
            proxy_pass http://myapp_backend;
            proxy_cache my_cache;
            proxy_cache_valid 200 10m;

            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
        }
    }
}
```

---

## Quick recap

- **Caching** avoids redoing expensive work by reusing a previously-computed result.
- **`proxy_cache`** stores backend responses so repeat requests skip the backend entirely — powerful, but risky for personalized content.
- **`expires` / `Cache-Control`** tell *browsers* to reuse static files without even asking the server again.
- **gzip** shrinks text-based responses in transit, speeding up page loads — usually configured once at the `http` level thanks to inheritance.

---

**Next up:** [[12 - Security Basics|Module 12: Security Basics]] — now that things are fast, let's make sure they're also safe from abuse.
