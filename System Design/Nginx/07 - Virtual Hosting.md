# Module 7: Hosting Multiple Websites (Virtual Hosting)

*Part of the [[Nginx for Beginners]] series.*

---

## The idea

So far we've configured **one** website on our Nginx instance (Module 5). But one of the most common real-world needs is running **several independent websites** off a single server — maybe you're hosting a personal blog, a portfolio site, and a small client project, all on one cheap VPS. Nginx makes this easy through a concept called **virtual hosting**.

> 📘 **Term: Virtual Hosting**
> Running multiple websites on a single physical (or virtual) server, where the server figures out which site to serve based on information in the incoming request — either the domain name requested or the port it arrived on.

There are two main flavors: **domain-based** and **port-based**. Domain-based is by far the more common approach for real websites, so we'll focus there, then cover port-based briefly.

---

## Domain-based virtual hosting

This is exactly the mechanism we touched on briefly in Module 5 with `server_name`. The idea: multiple `server` blocks all `listen` on the *same* port (usually `80` or `443`), but each has a **different `server_name`**. Nginx looks at the `Host` header of the incoming request and routes it to the matching block.

> 📘 **Term: Host Header**
> A piece of metadata sent with every HTTP request, telling the server which domain name the client actually typed/clicked — e.g., `Host: blog.example.com`. This is what makes it possible for many different websites to share the same IP address and port; without it, the server would have no way to know which site the visitor meant.

```nginx
server {
    listen 80;
    server_name blog.example.com;

    root /var/www/blog;
    index index.html;
}

server {
    listen 80;
    server_name portfolio.example.com;

    root /var/www/portfolio;
    index index.html;
}

server {
    listen 80;
    server_name client-project.com;

    root /var/www/client-project;
    index index.html;
}
```

Following our convention from Module 3, each of these would typically live in its **own file** under `sites-available/` (e.g., `blog.conf`, `portfolio.conf`, `client-project.conf`), each individually enabled with a symlink into `sites-enabled/`.

Behind the scenes, all three domains would point (via real DNS, or your local `/etc/hosts` file for testing, as we did in Module 5) to the **same IP address** — Nginx does the work of sorting incoming traffic into the right bucket once it arrives.

---

## What happens if no `server_name` matches?

You'll often see a **default/catch-all server block**, used to handle any request whose `Host` header doesn't match any configured `server_name` (for example, a bot scanning random IP addresses directly, or a stale DNS record).

```nginx
server {
    listen 80 default_server;
    server_name _;
    return 444;
}
```

> 📘 **Term: `default_server`**
> A flag added to `listen` marking this `server` block as the fallback for a given IP/port combination, used when no other block's `server_name` matches the request. Without an explicit default, Nginx just uses whichever `server` block happens to be defined first.

> 📘 **Term: `return 444`**
> `444` is a special, non-standard status code specific to Nginx that means "just close the connection immediately, don't send any response at all." It's a common, low-effort way to silently drop unwanted or junk traffic that isn't addressed to any of your real sites.

The `server_name _;` is just a conventional placeholder meaning "match anything" — it's not special regex, just a name that's very unlikely to ever be a real domain someone requests.

---

## Port-based virtual hosting

Less common for public websites, but useful for internal tools, admin panels, or quick local testing — instead of differentiating by domain, each site listens on a **different port**:

```nginx
server {
    listen 8081;
    root /var/www/site-one;
}

server {
    listen 8082;
    root /var/www/site-two;
}
```

Now, `http://yourserver:8081` and `http://yourserver:8082` serve two completely different sites, without needing separate domain names at all — the port number itself does the routing. This is handy for things like internal dashboards where registering a real domain isn't worth the effort.

---

## Enabling and disabling sites, hands-on

Let's actually add a second site alongside the one we built in Module 5, reusing the exact workflow from Module 3.

**1. Create the site folder and page:**

```bash
sudo mkdir -p /var/www/second-site
echo "<h1>This is my second site!</h1>" | sudo tee /var/www/second-site/index.html
```

> 📘 **Term: `tee`**
> A command that takes input and writes it both to a file *and* to the screen. Combined with `sudo`, it's a common trick for writing to a file that requires admin permissions, since `sudo echo ... > file` doesn't actually work the way people expect (the redirect `>` happens outside the `sudo` context).

**2. Create the config in `sites-available`:**

```bash
sudo nano /etc/nginx/sites-available/second-site.conf
```

```nginx
server {
    listen 80;
    server_name second-site.local;

    root /var/www/second-site;
    index index.html;
}
```

**3. Add it to your hosts file** (as we did in Module 5):

```
127.0.0.1   second-site.local
```

**4. Enable it:**

```bash
sudo ln -s /etc/nginx/sites-available/second-site.conf /etc/nginx/sites-enabled/
```

**5. Test and reload:**

```bash
sudo nginx -t
sudo systemctl reload nginx
```

Now `mysite.local` (from Module 5) and `second-site.local` both work independently, side by side, on the very same Nginx installation and the very same port `80` — Nginx routes purely based on the `Host` header.

**To disable a site later**, remember from Module 3: just remove the symlink, the original config stays safely untouched:

```bash
sudo rm /etc/nginx/sites-enabled/second-site.conf
sudo systemctl reload nginx
```

---

## Quick recap

- **Virtual hosting** = multiple websites on one server.
- **Domain-based** (the common approach): multiple `server` blocks share a port, differentiated by `server_name`, using the request's **Host header** to route.
- Add a `default_server` catch-all block to gracefully handle unmatched requests.
- **Port-based**: differentiate sites by port number instead of domain — useful for internal tools.
- Adding a new site = create folder + config in `sites-available` → symlink into `sites-enabled` → `nginx -t` → `reload`.

---

**Next up:** [[08 - Reverse Proxy|Module 8: Nginx as a Reverse Proxy]] — instead of serving static files, we'll point Nginx at a real running application and forward traffic to it.
