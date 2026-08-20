# Module 13: Logs & Troubleshooting

*Part of the [[Nginx for Beginners]] series.*

---

## Why logs are your best friend

Across this whole series we've mentioned, in passing, that when something goes wrong, "check the logs" — introduced back in Module 3, where we found `access.log` and `error.log` living under `/var/log/nginx/`. Now let's actually put them to work. Think of yourself as a detective: the logs are the crime scene, and every line is a clue.

---

## Reading `access.log`

Every request that reaches Nginx gets a line written here (by default, in a format called the **combined log format**). A typical line looks like:

```
203.0.113.42 - - [10/Aug/2026:14:32:01 +0000] "GET /dashboard HTTP/1.1" 200 1523 "https://example.com/" "Mozilla/5.0 ..."
```

Let's decode each part, left to right:

| Piece | Meaning |
|---|---|
| `203.0.113.42` | Client IP address (recall `$remote_addr` from Module 8) |
| `[10/Aug/2026:14:32:01 +0000]` | Timestamp of the request |
| `"GET /dashboard HTTP/1.1"` | The request line: HTTP method, path, and protocol version |
| `200` | The **status code** returned |
| `1523` | Size of the response, in bytes |
| `"https://example.com/"` | The **referrer** — the page the visitor came from, if any |
| `"Mozilla/5.0 ..."` | The **User-Agent** — identifies the visitor's browser/device |

> 📘 **Term: Status Code**
> A three-digit number in an HTTP response summarizing the outcome of a request. Codes starting with `2` mean success, `3` mean redirection, `4` mean the client made a bad request, and `5` mean the server itself failed. We'll cover the most common ones you'll actually encounter next.

> 📘 **Term: User-Agent**
> A header sent by the browser (or any HTTP client) identifying what software made the request — e.g., "Chrome on Windows" or "curl." Useful for understanding traffic patterns, or spotting bots.

Live-watching this file while testing is one of the most useful habits you can build:

```bash
sudo tail -f /var/log/nginx/access.log
```

> 📘 **Term: `tail -f`**
> A command that prints the last lines of a file and then **keeps watching**, printing new lines as they're appended in real time (`-f` stands for "follow"). Perfect for watching a log file live while you reproduce a problem in your browser.

---

## Reading `error.log`

While `access.log` records *every* request regardless of outcome, `error.log` only records things that went wrong — misconfigurations, backend failures, permission issues.

```
2026/08/10 14:35:12 [error] 1234#1234: *5 connect() failed (111: Connection refused) while connecting to upstream, client: 203.0.113.42, server: myapp.local, request: "GET /api/users HTTP/1.1", upstream: "http://127.0.0.1:3000/api/users", host: "myapp.local"
```

This single line is packed with clues: the severity (`[error]`), which client triggered it, which `server` block was involved, what the actual request was, and critically — the `upstream` line, telling us Nginx tried to reach `127.0.0.1:3000` (our backend from Module 8) and got a **connection refused**, meaning nothing was actually listening on that port. That's an incredibly specific, actionable clue — in this example, the fix is almost certainly "start the backend app."

> 📘 **Term: Log Severity Level**
> A label indicating how serious a logged event is — common levels include `debug`, `info`, `notice`, `warn`, `error`, and `crit`, in increasing order of severity. By default, Nginx's `error.log` only records `error` and above, but this is configurable if you need more (or less) detail while debugging.

---

## Common HTTP errors and what causes them

### 403 Forbidden

The server understood the request but refuses to fulfill it — usually a **permissions** problem, not a "this doesn't exist" problem.

Typical causes: the Linux file permissions on your site's files/folders don't allow the Nginx process to read them, or a missing `index` file (Module 5) combined with `autoindex off` (Module 6) — Nginx finds the folder but has nothing it's allowed to serve back.

```bash
# Check permissions on your site's folder
ls -la /var/www/mysite
```

Nginx's worker processes typically run as a low-privilege user (often `www-data` on Ubuntu) — that user needs read (and execute, for directories) permission on your site's files.

### 404 Not Found

The simplest one: Nginx looked for the requested file and it genuinely doesn't exist at the resolved path. Common causes: a typo in the URL, a wrong `root` directive (Module 5), or a `location` block (Module 6) matching a path you didn't expect it to, pointing somewhere unexpected.

### 502 Bad Gateway

This one is specific to **reverse proxying** (Module 8) — it means Nginx successfully received the request, tried to forward it to the backend as instructed, but the backend either wasn't reachable at all, or responded with something Nginx couldn't understand as a valid HTTP response.

Typical causes:
- The backend app isn't actually running (exactly the scenario in the `error.log` example above).
- The backend is running, but on a different port than what's configured in `proxy_pass`.
- The backend crashed mid-request.

### 504 Gateway Timeout

Similar to 502, but here the backend *was* reachable — it just took too long to respond, and Nginx gave up waiting. Common with slow database queries or an overloaded backend (recall Module 9's load balancing — this is often exactly the kind of problem load balancing is meant to prevent).

### 500 Internal Server Error

A generic "something broke" response, almost always coming from the **backend application itself**, not Nginx — Nginx faithfully relayed whatever error the app produced. When you see a `500`, the real clues live in the *application's* own logs, not Nginx's.

---

## Using `nginx -t` and service logs to debug

We've used `nginx -t` (Module 4) throughout this series to catch config mistakes before reloading — worth re-emphasizing here as step one of any troubleshooting session:

```bash
sudo nginx -t
```

If Nginx itself won't start or crashes right after a reload, `systemctl` can show you what happened at the service level, which sometimes catches things even before Nginx writes its own error log:

```bash
sudo systemctl status nginx
```

For a deeper, scrollable view of everything systemd has recorded about the Nginx service:

```bash
sudo journalctl -u nginx
```

> 📘 **Term: `journalctl`**
> A command for reading logs collected by **systemd** (recall this term from Module 2) — the `-u nginx` flag filters it down to only logs related to the `nginx` service specifically, rather than the entire system's combined log stream.

---

## A simple troubleshooting checklist

When something's broken, work through these roughly in order:

1. `sudo nginx -t` — is the config even valid?
2. `sudo systemctl status nginx` — is the service actually running?
3. `sudo tail -f /var/log/nginx/error.log` — reproduce the problem and watch what gets logged.
4. Check the specific status code in `access.log` for the failing request, and match it against the list above.
5. If it's a `502`/`504`, verify the backend app is actually running and listening on the port your `proxy_pass` expects.
6. If it's a `403`, check file permissions with `ls -la`.

---

## Quick recap

- **`access.log`** records every request; **`error.log`** records only problems.
- Use `tail -f` to watch a log live while reproducing an issue.
- **403** = permissions problem, **404** = file genuinely not found, **502** = backend unreachable/invalid, **504** = backend too slow, **500** = the backend app's own error, not Nginx's.
- `nginx -t`, `systemctl status`, and `journalctl -u nginx` cover config validity, service health, and deeper system-level logs respectively.

---

**Next up:** [[14 - Cheat Sheet|Module 14: Wrap-up Cheat Sheet]] — a one-page summary of everything we've covered, plus where to go from here.
