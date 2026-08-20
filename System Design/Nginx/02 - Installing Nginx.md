# Module 2: Installing Nginx

*Part of the [[Nginx for Beginners]] series.*

---

## Before we install anything

Nginx runs as a **background program** on your machine — you don't open it like a normal app with a window, it just quietly runs and listens for requests.

> 📘 **Term: Daemon**
> A program that runs in the background, without a user directly interacting with it, usually started automatically and left running indefinitely. Nginx runs as a daemon. (The word comes from old Unix systems — think of it as a "background helper.")

> 📘 **Term: Service**
> On Linux, a daemon that's registered with the operating system so it can be started, stopped, and monitored using standard commands (like `systemctl`). Nginx installs itself as a service.

We'll cover Ubuntu/Debian in detail since it's the most common setup for learning and for servers, with quick notes for other systems at the end.

---

## Installing on Ubuntu / Debian

Update your package list first, then install Nginx using `apt`:

```bash
sudo apt update
sudo apt install nginx
```

> 📘 **Term: Package Manager**
> A tool that installs, updates, and removes software for you, automatically handling dependencies. `apt` is Ubuntu/Debian's package manager — it's what fetches Nginx from official software repositories and sets it up.

> 📘 **Term: sudo**
> Short for "superuser do." It temporarily runs a command with administrator (root) privileges. Installing software and managing system services usually requires this because they affect the whole machine, not just your user account.

That's it — on Ubuntu, Nginx starts automatically right after installation.

---

## Starting, stopping, restarting, and checking status

Nginx is managed through `systemctl`, the standard tool for controlling services on most modern Linux systems (this is called **systemd**).

> 📘 **Term: systemd**
> The system and service manager used by most modern Linux distributions. It's responsible for starting services at boot, and for letting you start/stop/restart them manually.

```bash
# Check if Nginx is running
sudo systemctl status nginx

# Start Nginx
sudo systemctl start nginx

# Stop Nginx
sudo systemctl stop nginx

# Restart Nginx (fully stops, then starts again)
sudo systemctl restart nginx

# Reload Nginx (applies config changes without dropping active connections)
sudo systemctl reload nginx
```

> 📘 **Term: Restart vs Reload**
> A **restart** completely stops the Nginx process and starts a fresh one — briefly, your server is *unreachable*. A **reload** tells the already-running Nginx to re-read its configuration and apply changes gracefully, without dropping current connections. You'll almost always prefer `reload` after a config change, once Nginx is already running smoothly.

If you want Nginx to automatically start every time your machine boots up (common for real servers):

```bash
sudo systemctl enable nginx
```

> 📘 **Term: Enable (a service)**
> Tells systemd to automatically start this service at boot time, so you don't have to manually start it every time the machine restarts. This is different from `start`, which only starts it *right now*.

---

## Verifying it actually works

The simplest test: open a browser and visit:

```
http://localhost
```

or, from the command line:

```bash
curl http://localhost
```

> 📘 **Term: localhost**
> A special hostname that always refers to *your own machine* — the computer you're currently using. It's equivalent to the IP address `127.0.0.1`. Visiting `localhost` in a browser sends the request to a server running on your own computer, not out onto the internet.

> 📘 **Term: curl**
> A command-line tool for making network requests (like fetching a web page) without needing a browser. Very commonly used for quickly checking if a server responds, or for testing APIs.

If everything installed correctly, you'll see Nginx's default **"Welcome to nginx!"** page — a simple HTML page that ships with the installation, confirming the server is alive and serving content. We'll look at exactly where that page lives on disk in the next module.

---

## Notes for other operating systems

- **macOS:** install via [Homebrew](https://brew.sh): `brew install nginx`. Homebrew manages it slightly differently from a Linux service — use `brew services start nginx` to run it in the background.
- **CentOS / RHEL / Fedora:** use `dnf` (or the older `yum`) instead of `apt`: `sudo dnf install nginx`, then the same `systemctl` commands apply.
- **Windows:** Nginx has a native Windows build, but in practice most people either use **WSL** (Windows Subsystem for Linux, which lets you run a real Linux environment inside Windows) or run Nginx inside **Docker** — both give you the same experience as Linux.
- **Docker (any OS):** `docker run -p 80:80 nginx` spins up Nginx instantly in a container, no installation needed on your actual machine. Great for quick experiments.

> 📘 **Term: Container**
> A lightweight, isolated environment that packages an application with everything it needs to run, without needing a full separate operating system. Docker is the most popular tool for building and running containers. If you're already comfortable with Docker, it's a fast way to experiment with Nginx risk-free.

---

## Quick recap

- Nginx installs as a **service/daemon** — it runs quietly in the background.
- On Ubuntu/Debian: `sudo apt install nginx`.
- Control it with `systemctl start / stop / restart / reload / status / enable`.
- Prefer **reload** over **restart** once it's running, to avoid dropping connections.
- Verify success by visiting `http://localhost` and seeing the default "Welcome to nginx!" page.

---

**Next up:** [[03 - File Layout|Module 3: The Nginx File Layout]] — we'll explore exactly where Nginx keeps its configuration, logs, and that default welcome page.
