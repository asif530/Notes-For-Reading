# Docker Interview Prep Notes

# Explain these 
Docker Client
Docker Engine API
Docker Daemon
Docker Registry/Hub
Docker Images
Docker containers

## Build Cache Fundamentals

**Q: How does Docker's build cache work at a high level?**

Each Dockerfile instruction becomes a layer; layers are reused from cache if the instruction and its dependent files are unchanged. Once one layer's cache is invalidated, every subsequent layer rebuilds regardless of whether the output would differ.

**Q: What actually invalidates a cache layer?**

| Instruction    | Invalidation trigger                                                                                                                                           |
|----------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `COPY` / `ADD` | A checksum of file metadata (not mtime).                                                                                                                       |
| `RUN`          | Only the literal command string is compared — Docker never inspects what the command changed, so a `RUN apt-get install` can reuse a stale layer indefinitely. |
| `WORKDIR`      | Additionally sensitive to `SOURCE_DATE_EPOCH`.                                                                                                                 |

**Q: Difference between `--pull` and `--no-cache` on `docker build`?**

- `--pull` forces fetching a newer base image even if cached locally; it does **not** force rebuilding your own layers.
- `--no-cache` forces every layer in your Dockerfile to rebuild but does **not** by itself fetch a new base image.
- Use both together for a fully fresh build.

---

## Docker Compose Behavior

**Q: Why would `docker compose up` not reflect a Dockerfile change?**

- `up` doesn't rebuild by default — it reuses an existing local image unless you pass `--build`, and it only recreates containers if it detects a config/image change.
- `down -v` also never rebuilds; it just removes containers, networks, and named volumes.
- You need an explicit `docker compose build` (ideally `--no-cache`) or `up --build`.

---

## Image Tags & Debugging

**Q: What happens if two services share the same `image:` name, where one has a `build:` block?**

Building that service tags the resulting custom image with the shared name, overwriting the local pointer for that tag — since tags are mutable references, not fixed identities. Any other service also referencing that same tag now unknowingly runs the custom image.

> Root cause of "container running the wrong entrypoint" bugs.

**Q: How would you debug a container running unexpected behavior/entrypoint?**

```
docker inspect <container> --format='{{.Config.Image}}'
docker inspect <container> --format='{{json .Config.Entrypoint}}'
docker logs <container>
```

Cross-check the image/entrypoint baked into the container against its actual logs/behavior.

**Q: How do you guard against a tag unexpectedly changing over time (supply-chain risk)?**

Pin the base image to a content digest — `FROM image:tag@sha256:...` — instead of relying on a mutable tag, trading automatic updates for guaranteed reproducibility.

> Tools like Docker Scout can automate checking/updating pinned digests (e.g. via automated remediation PRs).

---

## BuildKit Garbage Collection & Caching

**Q: How does BuildKit garbage-collect its cache, and how would you tune it?**

GC runs periodically with ordered policies:

1. Stale/easily-regenerated cache unused **>48h** first.
2. Anything unused **>60 days**.
3. Unshared cache over a size limit.
4. Everything, if still over budget.

**Tunable via:**
- `daemon.json`'s `builder.gc.defaultKeepStorage` — default driver.
- `buildkitd.toml`'s `reservedSpace` / `maxUsedSpace` / `minFreeSpace` — custom builders.

**Q: When would you use an external cache backend, and what's the min/max mode tradeoff?**

Mainly in CI/CD where builders are ephemeral and lose local cache between runs; export/import via `--cache-to` / `--cache-from` (registry, local, gha, inline, etc.).

- `mode=min`: caches only final-image layers (smaller, faster transfer).
- `mode=max`: caches all intermediate layers (larger, more likely to hit cache on future builds).

** What is the e-flag in docker?
Passed with the docker run command. The -e flag indicates setting an environmental variable in the container, 
