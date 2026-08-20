# Module 3: The Nginx File Layout

*Part of the [[Nginx for Beginners]] series.*

---

## Why this matters

Before we start editing configuration, it helps enormously to know **where things actually live** on disk. Nginx spreads its files across a few predictable locations — once you know the map, everything else in this series will make a lot more sense.

Everything below assumes a standard Ubuntu/Debian install from Module 2. (Other distros and Docker images sometimes tweak these paths slightly, but the *concepts* are identical everywhere.)

---

## The main configuration directory: `/etc/nginx/`

> 📘 **Term: `/etc`**
> On Linux, `/etc` is the conventional home for **configuration files** for almost every installed program. If you're ever hunting for how *any* Linux service is configured, `/etc/<program-name>/` is the first place to check.

Here's what you'll typically find inside `/etc/nginx/`:

```
/etc/nginx/
├── nginx.conf              <- the main, top-level config file
├── mime.types               <- maps file extensions to content types
├── conf.d/                  <- extra config files, auto-loaded
├── sites-available/         <- all defined website configs live here
├── sites-enabled/           <- symlinks to the sites that are *active*
└── snippets/                <- small reusable config fragments
```

Let's go through the important ones.

---

## `nginx.conf` — the root of everything

This is the **main configuration file** Nginx reads when it starts up. Every other config file gets pulled in from here, usually through an `include` directive near the bottom, like:

```nginx
include /etc/nginx/conf.d/*.conf;
include /etc/nginx/sites-enabled/*;
```

> 📘 **Term: Directive**
> A single configuration instruction in an Nginx config file — basically one "setting." For example, `listen 80;` is a directive telling Nginx which network port to listen on. We'll cover directive syntax properly in the next module.

> 📘 **Term: include**
> A directive that tells Nginx to pull in and process another file's contents at that point in the config — similar to how you might split a large document into chapters and reference them from a table of contents, instead of writing one giant file.

You *can* put all your configuration directly into `nginx.conf`, but in practice almost nobody does — it gets unwieldy fast. Instead, real-world setups split configuration into small, focused files (one per website, typically), and `nginx.conf` just includes them all.

---

## `sites-available/` and `sites-enabled/`

This pairing trips up a lot of beginners, so let's be precise about it:

- **`sites-available/`** — contains configuration files for *every* website you've defined, whether it's currently active or not. Think of it as your full catalog of sites.
- **`sites-enabled/`** — contains **symbolic links** (symlinks) pointing to the specific files in `sites-available/` that you want *actually active* right now.

> 📘 **Term: Symbolic Link (Symlink)**
> A pointer/shortcut file that references another file elsewhere on disk, without duplicating its contents. Editing the original file automatically reflects everywhere the symlink points, since it's the same underlying file.

Why bother with two folders instead of just one? Because it makes **turning a site on or off** trivial and safe — you don't delete or rewrite any configuration, you just add or remove a symlink:

```bash
# Enable a site (create the symlink)
sudo ln -s /etc/nginx/sites-available/mysite.conf /etc/nginx/sites-enabled/

# Disable a site (remove the symlink only — the original config is untouched)
sudo rm /etc/nginx/sites-enabled/mysite.conf
```

We'll actually use this pattern hands-on in Module 5 and Module 7.

> ⚠️ Note: this `sites-available`/`sites-enabled` convention is a Debian/Ubuntu packaging choice, not something built into Nginx itself. On some other distros (and in the official Docker image), you'll instead find everything simply loaded from `conf.d/`. Both approaches work — Ubuntu's is just a little more organized for managing many sites.

---

## `conf.d/`

A simpler alternative/companion to `sites-available`: any `.conf` file dropped in here gets automatically loaded (no symlink step needed). Some people prefer this simplicity for smaller setups with just one or two sites; the `sites-available`/`sites-enabled` pattern shines once you're juggling many.

---

## Log files

Nginx keeps two crucial log files, usually under `/var/log/nginx/`:

```
/var/log/nginx/
├── access.log     <- records every request Nginx receives
└── error.log      <- records problems and warnings
```

> 📘 **Term: Log File**
> A plain text file that a program continuously appends lines to, recording what happened over time — requests received, errors encountered, etc. Logs are your primary tool for understanding what a running server is actually doing.

- **`access.log`** — one line per request, showing things like the visitor's IP address, the URL they requested, the response status code, and how big the response was.
- **`error.log`** — records anything that went wrong: a misconfiguration, a backend that failed to respond, a permissions issue, etc.

We'll spend real time reading these properly in Module 13 (Troubleshooting) — but it's worth knowing right now that whenever something seems broken, **these two files are the first place to look.**

---

## The default welcome page

Remember the "Welcome to nginx!" page from Module 2? It's a real HTML file sitting on disk, by default at:

```
/var/www/html/index.nginx-debian.html
```

> 📘 **Term: Document Root**
> The folder on disk that a web server treats as the "base" for serving files. If the document root is `/var/www/html` and someone requests `/about.html`, Nginx looks for the file at `/var/www/html/about.html`.

`/var/www/html/` is the **default document root** — the folder Nginx serves files from when no other configuration overrides it. In Module 5, we'll point Nginx at our *own* folder to serve a custom site instead of this default page.

---

## Quick recap

| Path | What it is |
|---|---|
| `/etc/nginx/nginx.conf` | Main config file, includes everything else |
| `/etc/nginx/sites-available/` | All defined site configs (active or not) |
| `/etc/nginx/sites-enabled/` | Symlinks to the sites currently active |
| `/etc/nginx/conf.d/` | Auto-loaded config files, no symlink needed |
| `/var/log/nginx/access.log` | Every request Nginx receives |
| `/var/log/nginx/error.log` | Problems and warnings |
| `/var/www/html/` | Default document root (where files are served from) |

---

**Next up:** [[04 - Config Structure|Module 4: Understanding the Config File Structure]] — now that we know *where* the files live, let's learn how to actually read and write what's inside them.
