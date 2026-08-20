# Module 14: Wrap-up Cheat Sheet

*Part of the [[Nginx for Beginners]] series.*

---

## You made it

Thirteen modules ago, we started with "what is a web server?" Now you know how to install Nginx, structure its config, serve static sites, host multiple domains, reverse-proxy to real apps, load-balance across instances, secure everything with HTTPS, speed things up with caching, lock things down, and debug problems like a detective. This module is a single-page reference to keep coming back to — plus a few pointers on where to go next.

---

## Service management

```bash
sudo systemctl start nginx      # start
sudo systemctl stop nginx       # stop
sudo systemctl restart nginx    # full stop + start (drops connections)
sudo systemctl reload nginx     # apply config changes gracefully (preferred)
sudo systemctl status nginx     # is it running?
sudo systemctl enable nginx     # auto-start on boot
```

## Before every reload

```bash
sudo nginx -t                   # ALWAYS run this before reload/restart
```

## File locations

| Path | Purpose |
|---|---|
| `/etc/nginx/nginx.conf` | Main config, includes everything else |
| `/etc/nginx/sites-available/` | All site configs (active or not) |
| `/etc/nginx/sites-enabled/` | Symlinks = the *active* sites |
| `/etc/nginx/conf.d/` | Auto-loaded config, no symlink needed |
| `/var/www/html/` | Default document root |
| `/var/log/nginx/access.log` | Every request |
| `/var/log/nginx/error.log` | Only problems |

## The block hierarchy

```
http { ... }                 # all web traffic
  server { ... }              # one website (by port and/or domain)
    location { ... }          # one URL path pattern within that site
```

## Basic static site

```nginx
server {
    listen 80;
    server_name example.com;
    root /var/www/example;
    index index.html;

    location / {
        try_files $uri $uri/ =404;
    }
}
```

## `location` matching types

| Syntax | Matches |
|---|---|
| `location /path/ { }` | Prefix (starts-with) |
| `location = /path { }` | Exact only |
| `location ~ pattern { }` | Regex, case-sensitive |
| `location ~* pattern { }` | Regex, case-insensitive |
| `location ^~ /path/ { }` | Prefix, skips regex checking if it's the longest match |

Priority order: exact → longest prefix remembered → regexes in file order → fallback to longest prefix.

## Reverse proxy (near-mandatory boilerplate)

```nginx
location / {
    proxy_pass http://localhost:3000;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
}
```

## Virtual hosting (multiple sites, one Nginx)

```nginx
server {
    listen 80;
    server_name blog.example.com;
    root /var/www/blog;
}

server {
    listen 80 default_server;
    server_name _;
    return 444;   # silently drop unmatched requests
}
```

## Load balancing

```nginx
upstream myapp_backend {
    # least_conn;   # or: ip_hash;   (default with neither: round robin)
    server localhost:3000;
    server localhost:3001;
    server localhost:3002 backup;
}
```

| Method | Directive | Use when |
|---|---|---|
| Round robin | *(default)* | Requests are roughly equal cost |
| Least connections | `least_conn;` | Requests vary a lot in duration |
| IP hash | `ip_hash;` | App needs the same client on the same server (session state) |

## HTTPS

```bash
sudo apt install certbot python3-certbot-nginx
sudo certbot --nginx -d example.com -d www.example.com
```

```nginx
server {
    listen 443 ssl;
    server_name example.com;
    ssl_certificate     /etc/letsencrypt/live/example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/example.com/privkey.pem;
}

server {
    listen 80;
    server_name example.com;
    return 301 https://example.com$request_uri;
}
```

## Caching & performance

```nginx
http {
    gzip on;
    gzip_types text/css application/javascript application/json text/html;
    proxy_cache_path /var/cache/nginx levels=1:2 keys_zone=my_cache:10m max_size=1g inactive=60m;
}

location ~* \.(jpg|jpeg|png|gif|css|js|ico)$ {
    expires 30d;
    add_header Cache-Control "public, immutable";
}

location / {
    proxy_cache my_cache;
    proxy_cache_valid 200 10m;
}
```

## Security basics

```nginx
http {
    server_tokens off;                                                  # hide version
    limit_req_zone $binary_remote_addr zone=mylimit:10m rate=5r/s;      # define rate limit
}

location /login {
    limit_req zone=mylimit burst=10 nodelay;
}

location /admin/ {
    allow 192.168.1.0/24;
    deny all;
}
```

## Common status codes

| Code | Meaning | Typical cause |
|---|---|---|
| 403 | Forbidden | File permissions, or missing index + autoindex off |
| 404 | Not Found | Wrong path, bad `root`, or `location` mismatch |
| 500 | Internal Server Error | The **backend app's** own error, not Nginx's |
| 502 | Bad Gateway | Backend unreachable, wrong port, or crashed |
| 504 | Gateway Timeout | Backend too slow to respond |

## Troubleshooting order

1. `sudo nginx -t` — config valid?
2. `sudo systemctl status nginx` — service running?
3. `sudo tail -f /var/log/nginx/error.log` — reproduce and watch.
4. Check the status code in `access.log` against the table above.
5. `sudo journalctl -u nginx` for deeper service-level logs.

---

## Where to go next

You now have a genuinely solid, practical foundation — enough to confidently run Nginx in front of a real personal project or small production app. A few natural next steps, roughly in order of how most people encounter them:

- **Nginx + Docker** — running Nginx inside a container, often as the "front door" container in a multi-container app (a pattern you may have already seen if you've explored the [[Docker]] material in this vault).
- **Nginx Ingress Controller for Kubernetes** — the same routing/load-balancing ideas from this series (Modules 6, 7, 9), but expressed as Kubernetes resources instead of hand-written config files, managing traffic into a whole cluster.
- **NGINX Plus** — the commercial version, adding features like active health checks (mentioned briefly in Module 9) and more advanced load balancing.
- **Other reverse proxies** — once Nginx clicks, tools like **Traefik**, **Caddy**, or **Envoy** (mentioned in [[Sidecar]]/[[Service Mesh]] in this vault) will feel very familiar, since they solve the same core problems with different config styles.
- **Web Application Firewalls (WAF)** — for a deeper layer of protection than the rate limiting and IP restriction covered in Module 12, e.g., ModSecurity or a cloud provider's WAF.

---

*This completes the [[Nginx for Beginners]] series. Nice work getting through all fourteen modules.*
