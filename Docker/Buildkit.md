# Docker BuildKit

## What is BuildKit?

According to Docker's official documentation ([docs.docker.com/build/buildkit](https://docs.docker.com/build/buildkit)), BuildKit is the builder backend used by Docker — it's the engine that actually executes your image builds, replacing the older "legacy builder" used in earlier Docker versions.

It's the default builder for both Docker Desktop and Docker Engine.

> **Windows note:** Windows container builds still fall back to the legacy builder, though BuildKit has experimental Windows support since v0.13.

---

## What BuildKit Improves

Compared to the legacy builder, BuildKit improves things in three main areas:

| Area | Improvement |
|---|---|
| **Performance** | Uses a fully concurrent build graph solver — detects and skips unused build stages, runs independent build stages in parallel, and only transfers files from your build context that actually changed between builds rather than re-reading/uploading everything each time. |
| **Storage management** | (see cache backends / GC — covered in the Image Cache & Build note) |
| **Extensibility** | Frontends allow build definitions beyond the Dockerfile format (see below). |

---

## LLB: The Core of the Caching Model

At its core, BuildKit uses **LLB (Low-Level Build)**, an intermediate binary format that represents your build as a **content-addressable dependency graph**.

This is what the whole caching model is built on: instead of the legacy builder's heuristic image comparisons, LLB tracks exact checksums of build operations and their content. This is why BuildKit's cache can be precise, portable, and even exported to a remote registry for reuse across different machines — exactly the `--cache-to` / `--cache-from` mechanism from the cache-backends discussion.

---

## Frontends

Because LLB is a low-level graph format rather than something humans write directly, BuildKit relies on **"frontends"** — components that translate a human-readable build definition into LLB.

> The Dockerfile format you normally write is one such frontend; BuildKit uses an external Dockerfile frontend to convert your Dockerfile into the LLB graph it actually executes.

---

## Summary

BuildKit is the modern build engine underneath `docker build` / `docker buildx build`. Everything covered elsewhere — build cache mechanics, cache invalidation rules, optimize-cache techniques, external cache backends, and garbage collection policies — is all specifically **BuildKit's** caching and execution behavior.
