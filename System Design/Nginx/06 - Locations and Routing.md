# Module 6: Locations & Routing

*Part of the [[Nginx for Beginners]] series.*

---

## Recap: what does a `location` block do?

Back in Module 4 we saw the shape of a `location` block, and in Module 5 we used one plain example: `location / { ... }`. Now let's actually understand **how Nginx decides which `location` block should handle a given request** — this is one of the most important (and most misunderstood) parts of Nginx.

> 📘 **Term: Routing**
> The process of deciding *which piece of logic handles a given request*, based on things like its URL path. In Nginx, `location` blocks are the mechanism for routing requests within a single `server` block.

A `server` block can contain **many** `location` blocks, each matching a different pattern of URL path:

```nginx
server {
    listen 80;
    server_name example.com;
    root /var/www/example;

    location / {
        try_files $uri $uri/ =404;
    }

    location /images/ {
        root /var/www/assets;
    }

    location /api/ {
        proxy_pass http://localhost:3000;
    }
}
```

Here, a request to `/images/logo.png` is handled differently from a request to `/api/users` — even though both arrive at the same `server` block, on the same domain and port.

> 📘 **Term: `proxy_pass`**
> A directive that forwards the request to another server instead of serving a local file — the core building block of a reverse proxy. We'll cover this properly in Module 8; it's shown here just to illustrate that `location` blocks aren't only about serving files.

---

## The three main matching types

Nginx supports several ways to write a `location` pattern. The three you'll use constantly:

### 1. Prefix match (the default, most common type)

```nginx
location /images/ {
    ...
}
```

This matches any URL path that **starts with** `/images/` — so `/images/logo.png`, `/images/2024/summer.jpg`, and even `/images/` itself all match. This is what we used in Module 5 with `location /`, which matches literally everything (every path starts with `/`).

### 2. Exact match (`=`)

```nginx
location = /favicon.ico {
    ...
}
```

The `=` prefix means the path must match **exactly** — nothing more, nothing less. `/favicon.ico` matches; `/favicon.ico/anything` does not. Exact matches are useful for special, single-file cases (like a favicon or a health-check endpoint) and are also the *fastest* type for Nginx to evaluate, since there's no comparison needed once an exact hit is found.

### 3. Regex match (`~` and `~*`)

```nginx
location ~ \.php$ {
    ...
}

location ~* \.(jpg|jpeg|png|gif)$ {
    ...
}
```

> 📘 **Term: Regex (Regular Expression)**
> A pattern-matching language for describing *shapes* of text, rather than exact text. `\.php$` means "ends with `.php`"; `\.(jpg|jpeg|png|gif)$` means "ends with `.jpg`, `.jpeg`, `.png`, or `.gif`."

- `~` — case-*sensitive* regex match.
- `~*` — case-*insensitive* regex match (so `.JPG` and `.jpg` both match).

Regex matches are powerful for pattern-based rules — like "any file ending in an image extension" — without listing every possible path.

---

## How Nginx actually picks *which* location wins

This is the part that confuses almost everyone at first, because it's **not** simply "first match wins" or "most specific wins" in an obvious top-to-bottom way. Here's the real algorithm, simplified:

1. Nginx first checks all **exact matches** (`=`). If one matches perfectly, it's used immediately — search over.
2. If no exact match, Nginx looks through all **prefix matches** (plain `location /path/`) and remembers the **longest** one that matches.
3. Nginx then checks all **regex matches** (`~` / `~*`), **in the order they're written** in the file. The first one that matches wins — *even if a longer prefix match existed in step 2.*
4. If no regex matches, Nginx falls back to the longest prefix match found in step 2.

> ⚠️ **The key surprising bit:** regex matches, if any of them match at all, take priority over prefix matches — *regardless of how specific the prefix was*. This is a very common source of "why isn't my location block being used?!" confusion. If you need a prefix match to win over a regex, add the special `^~` modifier to that prefix, which tells Nginx "if this prefix is the longest match, stop here — don't even bother checking regexes."

```nginx
location ^~ /images/ {
    # This wins over any regex, as long as it's the longest matching prefix
}
```

> 📘 **Term: `^~` modifier**
> Added before a prefix pattern to tell Nginx: "if this is the longest matching prefix, use it immediately and skip regex checking entirely." Useful when you have a directory that should never fall through to a regex rule.

---

## A worked example

```nginx
server {
    location / {
        # matches everything as a fallback
    }

    location /static/ {
        # matches anything starting with /static/
    }

    location ~ \.css$ {
        # matches anything ending in .css
    }
}
```

- Request `/about.html` → no exact match, longest prefix is `/` → no regex matches `.css` → **`location /` wins.**
- Request `/static/logo.png` → longest prefix is `/static/` → no regex match → **`location /static/` wins.**
- Request `/static/theme.css` → longest prefix is `/static/`, **but** the regex `\.css$` also matches → **regex wins**, since regex checks (step 3) happen after prefix matching (step 2) and take priority.

This last case is exactly the kind of surprise the warning above was about — a very specific-looking prefix (`/static/`) still loses to a regex.

---

## Serving different folders for different paths

A very common real pattern — combining what we know from Module 5 with prefix matching:

```nginx
server {
    listen 80;
    server_name example.com;

    root /var/www/example;
    index index.html;

    location / {
        try_files $uri $uri/ =404;
    }

    location /downloads/ {
        root /var/www/files;
        autoindex on;
    }
}
```

> 📘 **Term: `autoindex`**
> When turned `on`, Nginx automatically generates a simple clickable file listing page for a directory that has no `index` file — handy for a plain file-download folder, but usually left `off` (the default) for normal websites, since you don't want visitors browsing your entire file structure.

A request to `/downloads/report.pdf` is served from `/var/www/files/downloads/report.pdf` — note that Nginx **appends** the matched location path onto the `root`, unless you use `alias` instead (a distinction worth knowing, but out of scope for this beginner module).

---

## Quick recap

- `location` blocks let one `server` handle different URL paths differently.
- **Prefix match** (`location /path/`) — starts-with matching, most common.
- **Exact match** (`location = /path`) — matches only that precise path, fastest.
- **Regex match** (`location ~ pattern` / `~* pattern`) — pattern-based, case-sensitive or not.
- Matching order: exact wins immediately → longest prefix is remembered → regexes are checked in file order and win if any match → otherwise the longest prefix is used.
- Use `^~` on a prefix to force it to skip regex checking when it's the longest match.

---

**Next up:** [[07 - Virtual Hosting|Module 7: Hosting Multiple Websites (Virtual Hosting)]] — we'll use `server_name` and multiple `server` blocks to run several independent websites off one Nginx installation.
