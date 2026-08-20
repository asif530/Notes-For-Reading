# Module 5: Your First Server Block

*Part of the [[Nginx for Beginners]] series.*

---

## The goal

We've learned the theory — now let's actually build something. By the end of this module, you'll have your **own** custom HTML page served by Nginx, replacing the default "Welcome to nginx!" page, using the `sites-available`/`sites-enabled` pattern from Module 3 and the block structure from Module 4.

---

## Step 1: Create a folder and a page to serve

Let's create a dedicated folder for our site, separate from the default `/var/www/html`:

```bash
sudo mkdir -p /var/www/mysite
```

> 📘 **Term: `mkdir -p`**
> Creates a directory. The `-p` flag also creates any missing parent folders along the way, and doesn't complain if the folder already exists.

Now create a simple HTML file inside it:

```bash
sudo nano /var/www/mysite/index.html
```

> 📘 **Term: nano**
> A simple, beginner-friendly command-line text editor. Type to edit, `Ctrl+O` then `Enter` to save, `Ctrl+X` to exit. (If you're comfortable with `vim` or another editor, feel free to use that instead — nothing here is nano-specific.)

Paste in something simple:

```html
<!DOCTYPE html>
<html>
<head><title>My First Nginx Site</title></head>
<body>
    <h1>Hello from my own server block!</h1>
</body>
</html>
```

Save and exit.

---

## Step 2: Create the server block config file

Following the `sites-available` convention from Module 3, create a new config file for this site:

```bash
sudo nano /etc/nginx/sites-available/mysite.conf
```

Write the following `server` block:

```nginx
server {
    listen 80;
    server_name mysite.local;

    root /var/www/mysite;
    index index.html;

    location / {
        try_files $uri $uri/ =404;
    }
}
```

Let's walk through every single line — this is the core shape you'll reuse for essentially every site you ever configure in Nginx.

### `listen 80;`

> 📘 **Term: `listen`**
> Tells this `server` block which network **port** to accept connections on. Port `80` is the standard, default port for plain HTTP traffic — it's why you don't need to type `:80` when visiting a normal `http://` website, browsers assume it. (Port `443` is the equivalent default for HTTPS, which we'll use in Module 10.)

> 📘 **Term: Port**
> A number that identifies a specific "channel" for network communication on a machine. A single server can run many different services, each listening on its own port number, so traffic gets routed to the right program.

### `server_name mysite.local;`

> 📘 **Term: `server_name`**
> Tells Nginx which domain name(s) this particular `server` block is responsible for. When a request arrives, Nginx looks at the `Host` header (the domain the browser asked for) and matches it against each block's `server_name` to decide which one should handle it. This becomes essential once you're hosting multiple sites — covered fully in Module 7.

We're using `mysite.local` here as a placeholder domain purely for local testing (we'll point it at our machine in Step 4).

### `root /var/www/mysite;`

As covered in Module 3, this sets the **document root** — the folder Nginx serves files from for this site.

### `index index.html;`

> 📘 **Term: `index`**
> Specifies which filename to serve automatically when a request doesn't ask for a specific file — e.g., visiting just `/` instead of `/index.html`. You can list multiple candidates in priority order, e.g., `index index.html index.htm;`, and Nginx uses the first one it finds.

### `location / { try_files $uri $uri/ =404; }`

We touched on `location` blocks in Module 4 — this is our first real one. The pattern `/` matches *every* request as a fallback (we'll cover more specific patterns in Module 6).

> 📘 **Term: `try_files`**
> Tells Nginx to try a list of things in order and use the first one that actually exists on disk, falling back to the last option if nothing matches. Here: first try the exact file requested (`$uri`), then try it as a directory (`$uri/`, which would look for an `index.html` inside it), and if neither exists, return a `404 Not Found` error.

> 📘 **Term: Variable (`$uri`)**
> Nginx config files support built-in variables, always prefixed with `$`. `$uri` holds the path of the currently requested URL (e.g., `/about.html`). Nginx has many built-in variables — we'll meet more as we go.

---

## Step 3: Enable the site

Right now, this config exists in `sites-available`, but Nginx doesn't know to actually use it yet. Following the pattern from Module 3, create a symlink in `sites-enabled`:

```bash
sudo ln -s /etc/nginx/sites-available/mysite.conf /etc/nginx/sites-enabled/
```

---

## Step 4: Point `mysite.local` at your own machine

Since `mysite.local` isn't a real, registered domain, we need to tell our own computer to treat it as pointing to `localhost`. We do this by editing the **hosts file**.

> 📘 **Term: Hosts File**
> A local file (`/etc/hosts` on Linux/macOS, `C:\Windows\System32\drivers\etc\hosts` on Windows) that lets you manually map a domain name to an IP address on your own machine, bypassing real DNS lookups entirely. Great for local testing before a domain is actually registered or pointed anywhere.

```bash
sudo nano /etc/hosts
```

Add this line at the bottom:

```
127.0.0.1   mysite.local
```

Save and exit. Now, any request to `mysite.local` from this machine resolves to `127.0.0.1` (your own machine — remember `localhost` from Module 2).

---

## Step 5: Test the config, then reload

Remember the golden rule from Module 4 — **always test before reloading**:

```bash
sudo nginx -t
```

If it reports success:

```bash
sudo systemctl reload nginx
```

---

## Step 6: See it live

```bash
curl http://mysite.local
```

Or open `http://mysite.local` in your browser. You should see your **"Hello from my own server block!"** heading — not the default Nginx welcome page. 🎉

---

## What if something's wrong?

- Double-check every directive in your `server` block ends with `;`.
- Re-run `sudo nginx -t` — it'll point you straight at the file and line number of any mistake.
- Confirm the symlink exists: `ls -l /etc/nginx/sites-enabled/`.
- Check `/etc/hosts` was saved correctly.
- If all else fails, check `/var/log/nginx/error.log` (we'll dig into log-reading properly in Module 13).

---

## Quick recap

- We created a folder (`/var/www/mysite`) and an `index.html` to serve.
- We wrote a `server` block defining `listen`, `server_name`, `root`, `index`, and a `location` block.
- We **enabled** the site by symlinking it into `sites-enabled`.
- We pointed a fake local domain at our machine using the **hosts file**.
- We tested with `nginx -t`, then applied changes with `reload`.

---

**Next up:** [[06 - Locations and Routing|Module 6: Locations & Routing]] — we'll go deeper into `location` blocks and learn the different ways Nginx can match URL paths.
