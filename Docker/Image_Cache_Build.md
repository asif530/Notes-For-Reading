# Docker Image Cache & Build

## 1. How the Build Cache Works

Every Dockerfile instruction produces a layer. The builder compares each instruction to previously cached layers; once one layer's cache is invalidated, every layer after it must rebuild too, even if the result would be identical.

---

## 2. Cache Invalidation Rules

| Instruction                                  | Cache invalidation trigger                                                                                                                                    |
|----------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `COPY` / `ADD` (and `RUN --mount=type=bind`) | Invalidates based on a **file-metadata checksum** — file modification time (mtime) alone does **not** invalidate it.                                          |
| Plain `RUN`                                  | Compared only by the **command string**, never by what actually changed inside the container — a `RUN apt-get install` layer can silently stay stale forever. |
| `WORKDIR`                                    | Cache validity is tied to the `SOURCE_DATE_EPOCH` build arg — changing it invalidates WORKDIR and everything after it.                                        |
| Build secrets (`--secret`)                   | Never part of the cache — changing a secret's value alone won't bust the cache. Pair it with a changing build arg (e.g. `CACHEBUST`) if you need that.        |

---

## 3. Forcing a Rebuild

Ways to break/clear cache:

- Change an earlier layer so the chain breaks.
- Run `docker builder prune`.
- Build with `--no-cache` (whole build) or `--no-cache-filter <stage>` (one stage only).

> **`--pull` vs. `--no-cache`:** These are different. `--pull` fetches a newer base image even if one is cached; `--no-cache` rebuilds every layer but doesn't fetch a new base image. Combine both for a fully fresh build.

---

## 4. Optimizing Cache Usage

- Order instructions from **least- to most-frequently-changing** (install deps before copying source).
- Keep the build context small with `.dockerignore`.
- Use **bind mounts** (`RUN --mount=type=bind`) for files only needed transiently, so they don't become part of a layer or the cache.
- Use **cache mounts** (`RUN --mount=type=cache,target=...`) for persistent package-manager caches (npm, apt, Go modules, etc.) so only changed packages re-download.
- Use **multi-stage builds** and shared reusable base stages so common layers build once.

> **Classic `apt-get` pitfall:** Splitting `apt-get update` and `apt-get install` into separate `RUN` lines means editing only the install line can reuse a stale cached update layer — always chain them in one `RUN`, optionally with version pins, to intentionally "cache bust."

---

## 5. External Cache Backends

BuildKit's internal cache is automatic; external caches must be explicitly exported via `--cache-to` and imported via `--cache-from` — essential for CI/CD where builders are ephemeral.

| Backend                      | Status     |
|------------------------------|------------|
| inline, registry, local, gha | Available  |
| s3, azblob                   | Unreleased |

- **`mode=min`** (default): caches only layers in the final image.
- **`mode=max`**: caches all intermediate layers too — larger, but more cache hits.
- Each cache destination can only be written by **one build at a time** without overwriting — use separate locations per branch if needed.
- Compression and OCI media-type options (`compression=zstd`, `oci-mediatypes`, `image-manifest`) are supported on local/registry backends.

---

## 6. Garbage Collection (GC)

BuildKit periodically prunes cache automatically (separate from manual `docker builder prune`), evaluating policies from most specific to broadest:

1. "Stale," easily-regenerated cache (local contexts, git checkouts, cache mounts) unused over **48 hours**.
2. Anything unused over **60 days**.
3. Unshared cache over the size limit.
4. Anything at all, if still over budget.

**Configuration:**
- `daemon.json`'s `builder.gc.defaultKeepStorage` — default docker driver.
- `buildkitd.toml`'s `reservedSpace` / `maxUsedSpace` / `minFreeSpace` — other builders.

---

## 7. Docker Compose Specifics

- `docker compose build` tags the image using the service's `image:` name if set, otherwise defaults to `project-service`.
- `docker compose up` only rebuilds if you pass `--build`; otherwise it reuses whatever image already exists, and only recreates a container if config/image changed (force with `--force-recreate`, block with `--no-recreate`).
- `docker compose down -v` only stops/removes containers, networks, and named volumes — it **never rebuilds anything**.
- If a service defines both `build:` and `image:` with no `pull_policy`, Compose tries to **pull that image name first** and only builds locally if it's not found in the registry/cache.
- `docker compose push` skips services with no `image:` attribute; the `tags:` build attribute can add extra tags beyond `image:`.

---

## 8. Image Tag Mutability — The Core Risk

Tags (e.g. `postgres:17`, `alpine:3.21`) are **mutable local pointers, not fixed identities** — retagging or rebuilding with an existing tag name reassigns that pointer, whether done by a registry publisher or by your own build/tag command.

> This is exactly what happens if two Compose services (one official, one custom-built) share the same `image:` name — the custom build silently overwrites the official image locally.

**Mitigation:**
- Give custom builds their own distinct tag.
- For critical/production base images, pin to an exact digest (`image@sha256:...`) instead of relying on a tag alone.

---


## 10. Docker Build and Docker Compose 
 A bare "docker build" and "docker compose up" are two separate, disconnected things here:

1. docker build (run without -t <tag>, or from outside compose) produces an image, but if it's not tagged with exactly the name compose
   expects (<project>-<service>, here sb-heap-obeservability-app:latest), compose never sees it — it just sits as a dangling/differently-tagged image.
2. docker compose up does not rebuild automatically. It only builds an image for a service if no image with that tag exists yet. If sb-heap-obeservability-app:latest
   already exists (from any earlier build), up just reuses it — it does not diff your source against the image.
3. On top of that, there was already a stopped container (sb-heap-app, exited 16h ago) sitting around from a previous run. docker compose up without --force-recreate
   will happily restart an existing container as-is rather than create a new one from a newer image, if it doesn't detect the image changed.

That's consistent with what I found: the image was last actually built 2026-07-17T14:53, well before your new ThreadLocalLeak* files existed on disk — so no rebuild in
between had ever produced a fresh image, regardless of how many times docker compose up was run.

The fix is what I just did: docker compose build --no-cache app (or the lighter docker compose build app, cache is fine here since COPY src src correctly invalidates on
real content changes) followed by docker compose up -d, which recreates the container from the new image. Going forward, docker compose up --build in one shot does both
steps and avoids this trap.


## 9. Practical Checklist

- [ ] Never share an `image:` name between an official image and a custom build.
- [ ] After editing a Dockerfile, always run `docker compose build` (or `up --build`) — don't assume `up`/`down -v` will pick up the change.
- [ ] Use `docker inspect <container> --format='{{.Config.Image}}'` and `--format='{{json .Config.Entrypoint}}'` plus `docker logs <container>` to verify what a running container is actually built from when something looks wrong.
- [ ] Use `--pull` / `--no-cache` deliberately depending on whether you want a fresh base image, a fresh rebuild, or both.