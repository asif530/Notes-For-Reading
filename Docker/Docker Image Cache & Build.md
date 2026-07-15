How the build cache works
Every Dockerfile instruction produces a layer. The builder compares each instruction to previously cached layers; once one layer's cache is invalidated, 
every layer after it must rebuild too, even if the result would be identical.


Cache invalidation rules
COPY/ADD (and RUN --mount=type=bind) invalidate cache based on a file-metadata checksum — file modification time (mtime) alone does not invalidate it. 
Plain RUN instructions are compared only by the command string, never by what actually changed inside the container, 
so a RUN apt-get install layer can silently stay stale forever. 
WORKDIR's cache validity is tied to the SOURCE_DATE_EPOCH build arg — changing it invalidates WORKDIR and everything after it. 
Build secrets (--secret) are never part of the cache, so changing a secret's value alone won't bust the cache; pair it with a changing build arg (e.g. CACHEBUST) 
if you need that.

Forcing a rebuild
Ways to break/clear cache: change an earlier layer so the chain breaks, run docker builder prune, or build with --no-cache (whole build) 
or --no-cache-filter <stage> (one stage only). --pull and --no-cache are different: --pull fetches a newer base image even if one is cached; 
--no-cache rebuilds every layer but doesn't fetch a new base image. Combine both for a fully fresh build.

Optimizing cache usage
Order instructions from least- to most-frequently-changing (install deps before copying source). 
Keep the build context small with .dockerignore. Use bind mounts (RUN --mount=type=bind) for files only needed transiently, 
so they don't become part of a layer or the cache. Use cache mounts (RUN --mount=type=cache,target=...) 
for persistent package-manager caches (npm, apt, Go modules, etc.) so only changed packages re-download. 
Use multi-stage builds and shared reusable base stages so common layers build once. Watch the classic apt-get pitfall: 
splitting apt-get update and apt-get install into separate RUN lines means editing only the install line can reuse a stale cached update layer — always chain them
in one RUN, optionally with version pins, to intentionally "cache bust."

External cache backends
BuildKit's internal cache is automatic; external caches (inline, registry, local, gha; s3/azblob unreleased) must be explicitly 
exported via --cache-to and imported via --cache-from — essential for CI/CD where builders are ephemeral. mode=min (default) caches only layers in the final image; 
mode=max caches all intermediate layers too, larger but more cache hits. Each cache destination can only be written by one build at a time without overwriting; 
use separate locations per branch if needed. Compression and OCI media-type options (compression=zstd, oci-mediatypes, image-manifest) 
are supported on local/registry backends.

Garbage collection (GC)
BuildKit periodically prunes cache automatically (separate from manual docker builder prune), evaluating policies from most specific to broadest: 
first "stale" easily-regenerated cache (local contexts, git checkouts, cache mounts) unused over 48 hours; then anything unused over 60 days; 
then unshared cache over the size limit; then anything at all if still over budget. Configure via daemon.json's builder.gc.defaultKeepStorage (default docker driver)
or buildkitd.toml's reservedSpace/maxUsedSpace/minFreeSpace (other builders). 

Docker Compose specifics
docker compose build tags the image using the service's image: name if set, otherwise defaults to project-service. 
docker compose up only rebuilds if you pass --build; otherwise it reuses whatever image already exists, and only recreates a container 
if config/image changed (force with --force-recreate, block with --no-recreate). docker compose down -v only stops/removes containers, networks, and named volumes 
— it never rebuilds anything. If a service defines both build: and image: with no pull_policy, Compose tries to pull that image name first and only builds locally if it's not found in the registry/cache. docker compose push skips services with no image: attribute; the tags: build attribute can add extra tags beyond image:.

Image tag mutability — the core risk
Tags (e.g. postgres:17, alpine:3.21) are mutable local pointers, not fixed identities — retagging or rebuilding with an existing tag name reassigns that pointer, 
whether done by a registry publisher or by your own build/tag command. This is exactly what happens if two Compose services (one official, one custom-built) 
share the same image: name — the custom build silently overwrites the official image locally. 

Mitigation: give custom builds their own distinct tag, and for critical/production base images, pin to an exact digest (image@sha256:...) 
instead of relying on a tag alone.

Practical checklist
Never share an image: name between an official image and a custom build. 
After editing a Dockerfile, always run docker compose build (or up --build) — don't assume up/down -v will pick up the change. 
Use docker inspect <container> --format='{{.Config.Image}}' and --format='{{json .Config.Entrypoint}}' 
plus docker logs <container> to verify what a running container is actually built from when something looks wrong. Use --pull/--no-cache deliberately depending on 
whether you want a fresh base image, a fresh rebuild, or both.