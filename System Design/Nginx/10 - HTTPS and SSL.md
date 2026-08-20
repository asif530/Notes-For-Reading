# Module 10: HTTPS & SSL/TLS with Nginx

*Part of the [[Nginx for Beginners]] series.*

---

## Why HTTPS matters

Everything we've built so far has used plain `http://` — fine for local learning, but no real website should run this way. Plain HTTP sends data **completely unencrypted**, meaning anyone snooping on the network between the visitor and your server (a nosy person on the same public Wi-Fi, an ISP, anyone in between) could read passwords, cookies, or any other data flowing back and forth, in plain readable text.

> 📘 **Term: Encryption**
> Scrambling data using a mathematical process so that only someone with the correct "key" can unscramble and read it. Encrypted traffic is unreadable gibberish to anyone intercepting it along the way.

**HTTPS** solves this by encrypting the connection between browser and server.

> 📘 **Term: HTTPS**
> HTTP, but running over an encrypted connection. It's the same underlying request/response system we've been configuring this whole series — the difference is entirely in how the connection is secured, not in how Nginx routes requests, serves files, or proxies to backends.

---

## SSL vs TLS — a quick, common point of confusion

You'll hear both terms used constantly, often interchangeably.

> 📘 **Term: SSL / TLS**
> **SSL** (Secure Sockets Layer) was the original protocol for encrypting web traffic. It's been officially obsolete and insecure for years, replaced by its successor, **TLS** (Transport Layer Security). In practice, almost everyone still casually says "SSL" out of habit, even though modern systems — including Nginx — are actually using TLS under the hood. When you see `ssl_certificate` in an Nginx config, it's configuring TLS; the naming is just a historical leftover.

---

## What a certificate actually is

To enable HTTPS, your server needs a **certificate** — a small file that does two jobs: it proves your server is who it claims to be, and it provides the cryptographic material needed to set up an encrypted connection.

> 📘 **Term: Certificate (SSL/TLS Certificate)**
> A digitally-signed file that binds a **public key** to a domain name, issued by a trusted **Certificate Authority (CA)**. When your browser connects to `https://example.com`, it checks that the certificate presented really is for `example.com` and was signed by a CA it trusts — this is what makes the little padlock icon appear.

> 📘 **Term: Certificate Authority (CA)**
> A trusted organization that verifies you actually control a domain, then issues a certificate for it. Browsers and operating systems ship with a built-in list of CAs they trust automatically. If a certificate wasn't issued by one of these trusted CAs (e.g., you made one up yourself), browsers will show a scary security warning.

Historically, certificates cost money and required manual renewal every year — a real hassle. Today, thanks to a free, automated CA called **Let's Encrypt**, getting a trusted certificate takes minutes and costs nothing.

---

## Getting a free certificate with Let's Encrypt and Certbot

> 📘 **Term: Let's Encrypt**
> A free, automated, widely-trusted Certificate Authority, run as a nonprofit service. It issues short-lived certificates (90 days) specifically designed to be renewed automatically by tooling, rather than manually.

> 📘 **Term: Certbot**
> The most popular official tool for requesting and installing Let's Encrypt certificates. It has a plugin specifically for Nginx that can even edit your config files for you automatically.

⚠️ **Important prerequisite:** Let's Encrypt needs to actually verify you control the domain by reaching your server over the real internet — this means the domain must have real DNS pointing at a real, publicly reachable server. It will **not** work with a local `mysite.local` fake domain like we used for testing in earlier modules (recall the hosts-file trick from Module 5) — that only exists on your own machine, not out on the internet where Let's Encrypt can see it.

Assuming you have a real domain pointed at a real server, on Ubuntu:

```bash
sudo apt install certbot python3-certbot-nginx
sudo certbot --nginx -d example.com -d www.example.com
```

Certbot will:
1. Verify you control the domain (by briefly serving a special file Let's Encrypt checks for).
2. Obtain the certificate.
3. **Automatically edit your Nginx config** to add the HTTPS configuration and reload Nginx for you.
4. Set up automatic renewal (usually via a scheduled task) so you never have to think about the 90-day expiry.

---

## What the resulting config actually looks like

It's worth understanding what Certbot generates, so you're not just trusting a black box. A manually-written equivalent looks like this:

```nginx
server {
    listen 443 ssl;
    server_name example.com;

    ssl_certificate     /etc/letsencrypt/live/example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/example.com/privkey.pem;

    root /var/www/example;
    index index.html;
}
```

- **`listen 443 ssl;`** — recall from Module 5 that `listen 80` is the default HTTP port; `443` is the equivalent standard, default port for HTTPS. Adding `ssl` here tells this `server` block to expect and handle encrypted connections.
- **`ssl_certificate`** — path to the **public** certificate file, the part that gets shown to visiting browsers.
- **`ssl_certificate_key`** — path to the matching **private key** — the secret half of the cryptographic pair, which must never be shared or exposed. This is what actually lets your server prove it legitimately owns the certificate.

> 📘 **Term: Public Key / Private Key**
> A matched pair of cryptographic keys. The **public key** (embedded in the certificate) can be freely shared with anyone — it's used to encrypt data or verify signatures. The **private key** must be kept completely secret on the server — it's the only thing that can decrypt data encrypted with the matching public key, or produce a valid signature. This pairing is the mathematical foundation that makes HTTPS trust possible.

---

## Redirecting HTTP to HTTPS

Once HTTPS works, you generally want to make sure **no one accidentally stays on the insecure `http://` version** of your site. The standard pattern: keep a `server` block listening on port `80` purely to redirect everything to the HTTPS version.

```nginx
server {
    listen 80;
    server_name example.com;
    return 301 https://example.com$request_uri;
}

server {
    listen 443 ssl;
    server_name example.com;

    ssl_certificate     /etc/letsencrypt/live/example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/example.com/privkey.pem;

    root /var/www/example;
    index index.html;
}
```

> 📘 **Term: `return 301`**
> Sends back an HTTP "permanent redirect" response, telling the browser "what you're looking for has moved — go here instead," and the browser automatically follows it. `301` specifically means *permanent* (as opposed to `302`, temporary), which also hints to search engines that they should update their records to the new address.

> 📘 **Term: `$request_uri`**
> A built-in Nginx variable (like `$uri` from Module 5, but including the query string too, e.g., `?page=2`) holding the exact path and query the visitor originally requested — used here so the redirect lands on the *same page*, just over HTTPS, instead of dumping every visitor onto the homepage regardless of what they asked for.

Certbot, conveniently, sets up exactly this pattern automatically when you run it (it'll even ask you whether you want to force the redirect).

---

## Quick recap

- **HTTPS** encrypts the connection between browser and server; **TLS** is the modern protocol behind it (people still colloquially say "SSL").
- A **certificate**, issued by a trusted **Certificate Authority**, proves your server's identity and enables encryption.
- **Let's Encrypt** + **Certbot** get you a free, trusted certificate in minutes, with automatic renewal, and can even edit your Nginx config for you.
- The core config: `listen 443 ssl;` plus `ssl_certificate` and `ssl_certificate_key` pointing at your cert files.
- Always add an HTTP → HTTPS redirect using `return 301`, so visitors never stay on the insecure version.

---

**Next up:** [[11 - Caching and Performance|Module 11: Caching & Performance]] — we'll speed things up further with caching and compression.
