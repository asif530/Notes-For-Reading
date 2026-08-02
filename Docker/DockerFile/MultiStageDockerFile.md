# Dockerfile Explanation

## Dockerfile Content

```dockerfile
# Build stage
FROM gradle:9.1.0-jdk25 AS builder

WORKDIR /app

# Copy Gradle wrapper and configs
COPY gradlew settings.gradle build.gradle gradle.properties ./
COPY gradle gradle

# Create flyway dummy folder to bypass Gradle 9.0+ strict build
RUN mkdir -p flyway

# Copy proto module
COPY proto proto

# Copy api build.gradle for Gradle configuration
COPY api/build.gradle api/

# Pre-cache dependencies and build proto module first
RUN gradle --no-daemon dependencies
RUN gradle :proto:build --no-daemon

# Copy api source code (after proto is built and cached)
COPY api/src api/src

# Build api module (proto artifacts are already available)
RUN gradle :api:bootJar --no-daemon

# Runtime stage
FROM eclipse-temurin:25-jre-alpine

# Update Alpine packages to fix libpng vulnerabilities
RUN apk update && apk upgrade --no-cache

WORKDIR /app

COPY --from=builder /app/api/build/libs/*.jar app.jar

# Download the Elastic APM Java Agent
ADD https://repo1.maven.org/maven2/co/elastic/apm/elastic-apm-agent/1.55.0/elastic-apm-agent-1.55.0.jar /app/elastic-apm-agent.jar
RUN chmod 644 /app/elastic-apm-agent.jar

# application port
EXPOSE 8080

ENTRYPOINT ["java", \
    "-XX:InitialRAMPercentage=70.0", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:+UseG1GC", \
    "-XX:MaxGCPauseMillis=200", \
    "-XX:+UseStringDeduplication", \
    "-XX:+HeapDumpOnOutOfMemoryError", \
    "-XX:+ExitOnOutOfMemoryError", \
    "-javaagent:/app/elastic-apm-agent.jar", \
    "-Delastic.apm.disable_instrumentations=messaging", \
    "-jar", "app.jar"]
```

---

## Multi-Stage Build Overview

This Dockerfile uses a **two-stage build**: a heavy `builder` stage (Gradle + JDK) and a lean `runtime` stage (JRE only). The final image only contains what's needed to run the app — not build it. This is a best practice that dramatically reduces image size and attack surface.

---

## Stage 1 — Build Stage

```dockerfile
FROM gradle:9.1.0-jdk25 AS builder
```
- Uses the official Gradle image with JDK 25, pinned to an exact version.
- `AS builder` names this stage so the runtime stage can reference it later.
- **Why pin versions?** Using `latest` makes builds non-reproducible — a newer Gradle or JDK version could break the build silently.
- **Without this stage:** You'd need a separate CI step to build the JAR before running Docker.

---

```dockerfile
WORKDIR /app
```
- Sets `/app` as the working directory for all following instructions in this stage.
- Without it, commands run from `/` — messy and error-prone.
- **Best practice:** Always define `WORKDIR` explicitly; never rely on the default.

---

```dockerfile
COPY gradlew settings.gradle build.gradle gradle.properties ./
COPY gradle gradle
```
- Copies **only build configuration files** — not source code — first.
- **Why this order matters (layer caching):** Docker caches each instruction as a layer. If source code changes but these files don't, Docker reuses the cached dependency-download layer. Reversing this order means every source code change re-downloads all dependencies (slow!).
- Example: if you modified `api/src/...`, Docker still reuses the cached layer from `gradle dependencies` below.

---

```dockerfile
RUN mkdir -p flyway
```
- Creates an empty `flyway` directory.
- **Why:** Gradle 9.0+ has stricter build configuration — it requires all declared subproject directories to exist at configuration time, even if you're not building that module. The flyway submodule is declared in `settings.gradle` but its source isn't needed in the build stage.
- **Without it:** The Gradle build fails with an error that the `flyway` project directory is missing.

---

```dockerfile
COPY proto proto
COPY api/build.gradle api/
```
- Copies the `proto` submodule (which `api` depends on) and **only** `api/build.gradle` (not its source yet).
- This allows Gradle to resolve all dependencies in the next step while still benefiting from caching — adding source code later won't invalidate this layer.

---

```dockerfile
RUN gradle --no-daemon dependencies
RUN gradle :proto:build --no-daemon
```
- **`dependencies`:** Downloads and caches all declared dependencies into the Gradle cache.
- **`:proto:build`:** Compiles the proto module (likely generates Java classes from `.proto` files) because `api` depends on its artifacts.
- **`--no-daemon`:** The Gradle daemon is a background process that speeds up local development by staying warm between builds. Inside Docker it's wasteful (single build, container exits) and can cause resource/locking issues. **Always use `--no-daemon` in Docker.**
- Running these as **two separate** `RUN` instructions creates two distinct cache layers — if proto changes, only its layer is invalidated.

---

```dockerfile
COPY api/src api/src
```
- **Only now** is source code copied — after all dependencies are cached. This is the optimal Docker layer ordering.
- Any change to `api/src` only invalidates layers from this line onward, not the dependency download.

---

```dockerfile
RUN gradle :api:bootJar --no-daemon
```
- Runs the Spring Boot Gradle task to produce a **fat/uber JAR** — a single self-contained executable that includes all dependencies.
- Output: `api/build/libs/*.jar`

---

## Stage 2 — Runtime Stage

```dockerfile
FROM eclipse-temurin:25-jre-alpine
```
- Starts completely fresh from a minimal base image. Everything from the builder stage is discarded.
- **JRE vs JDK:** JRE (~100 MB) vs JDK (~400 MB) — you don't need the compiler (javac, jlink, etc.) at runtime.
- **Alpine vs Debian/Ubuntu:** Alpine Linux base is ~5 MB vs ~70 MB. Smaller image = faster pulls, smaller attack surface, fewer CVEs.
- **eclipse-temurin:** Adoptium's (formerly AdoptOpenJDK) production-grade, well-maintained OpenJDK distribution.

---

```dockerfile
RUN apk update && apk upgrade --no-cache
```
- Updates all installed Alpine packages to their latest patched versions.
- The comment in the file says this specifically targets `libpng` CVEs.
- **`--no-cache`:** Tells APK not to store the package index on disk, saving image layer size.
- **Without this:** Known CVEs in bundled Alpine packages (like `libpng`, `zlib`) would remain in the final image, flagged by security scanners.
- **Best practice:** Always upgrade base image packages in production images.

---

```dockerfile
WORKDIR /app
```
- Sets the working directory in the runtime stage (independent from the build stage).

---

```dockerfile
COPY --from=builder /app/api/build/libs/*.jar app.jar
```
- The key line of multi-stage builds: copies **only the JAR** from the `builder` stage.
- The entire Gradle installation, JDK, source code, build caches, and downloaded dependencies stay behind in the discarded builder stage.
- Result: the final image might be ~200-300 MB instead of 1+ GB.

---

```dockerfile
ADD https://repo1.maven.org/maven2/co/elastic/apm/elastic-apm-agent/1.55.0/elastic-apm-agent-1.55.0.jar /app/elastic-apm-agent.jar
RUN chmod 644 /app/elastic-apm-agent.jar
```
- Downloads the **Elastic APM Java Agent** directly from Maven Central.
- **`ADD` vs `COPY`:** `ADD` can fetch URLs; `COPY` cannot. However, `ADD` with a URL re-fetches on every build if the cache is busted, while a pre-downloaded file with `COPY` would be fully cached. The tradeoff here is simplicity vs offline build capability.
- **`chmod 644`:** Owner gets read+write, group and others get read-only. Removes any executable bit that might have been set — **least-privilege principle**.
- **Without the agent:** The app runs fine but you lose distributed tracing, transaction monitoring, and error tracking in Elastic APM.

---

```dockerfile
EXPOSE 8080
```
- Documents that the application listens on port 8080.
- **`EXPOSE` does NOT publish the port** — that happens at `docker run -p 8080:8080` or in `docker-compose.yml`. It serves as documentation and enables `docker run -P` (auto-publish all exposed ports).
- **Without it:** Functionally identical if you always specify `-p`, but it's considered good practice for discoverability.

---

```dockerfile
ENTRYPOINT ["java",
    "-XX:InitialRAMPercentage=70.0",
    "-XX:MaxRAMPercentage=75.0",
    "-XX:+UseG1GC",
    "-XX:MaxGCPauseMillis=200",
    "-XX:+UseStringDeduplication",
    "-XX:+HeapDumpOnOutOfMemoryError",
    "-XX:+ExitOnOutOfMemoryError",
    "-javaagent:/app/elastic-apm-agent.jar",
    "-Delastic.apm.disable_instrumentations=messaging",
    "-jar", "app.jar"]
```

**JSON array (exec form) vs shell string (shell form):**
- Exec form (`["java", ...]`) means the JVM is PID 1 directly. `SIGTERM` from `docker stop` goes straight to Java, enabling graceful shutdown.
- Shell form (`ENTRYPOINT java ...`) wraps in `/bin/sh -c`, making sh PID 1. Signals may not be forwarded to the JVM, breaking graceful shutdown.

**JVM flags explained:**

| Flag                                               | What it does                                                       | Without it                                                                                  |
|----------------------------------------------------|--------------------------------------------------------------------|---------------------------------------------------------------------------------------------|
| `-XX:InitialRAMPercentage=70.0`                    | JVM heap starts at 70% of container memory                         | JVM reads host RAM, allocating far too little heap in a container                           |
| `-XX:MaxRAMPercentage=75.0`                        | JVM heap cap at 75% of container memory                            | Same — JVM default is 25% of host RAM, starving the app                                     |
| `-XX:+UseG1GC`                                     | Enables Garbage-First GC, tuned for server workloads               | JVM picks a default GC that may have longer pauses                                          |
| `-XX:MaxGCPauseMillis=200`                         | G1GC targets ≤200ms GC pauses                                      | G1GC uses its own default target (also ~200ms, but explicit is clearer)                     |
| `-XX:+UseStringDeduplication`                      | G1GC deduplicates identical String objects in heap                 | Wasted heap for apps with many repeated strings (JSON keys, paths, etc.)                    |
| `-XX:+HeapDumpOnOutOfMemoryError`                  | Writes a `.hprof` heap dump on OOM                                 | OOM crash leaves no forensic evidence; impossible to debug                                  |
| `-XX:+ExitOnOutOfMemoryError`                      | JVM exits immediately on OOM                                       | JVM limps along in a broken half-allocated state; Kubernetes won't restart it               |
| `-javaagent:/app/elastic-apm-agent.jar`            | Attaches Elastic APM for tracing/metrics                           | No APM observability                                                                        |
| `-Delastic.apm.disable_instrumentations=messaging` | Disables APM instrumentation for messaging (Kafka, RabbitMQ, etc.) | APM instruments messaging calls — likely disabled to avoid noise or a known incompatibility |

---

## Summary of Best Practices Observed

| Practice                         | Where Applied                                                                     |
|----------------------------------|-----------------------------------------------------------------------------------|
| Multi-stage build                | Separating builder and runtime stages                                             |
| Pinned image versions            | `gradle:9.1.0-jdk25`, `eclipse-temurin:25-jre-alpine`, `elastic-apm-agent/1.55.0` |
| Optimal layer caching            | Build configs copied before source code                                           |
| Minimal runtime image            | JRE Alpine instead of JDK Ubuntu                                                  |
| Package vulnerability patching   | `apk upgrade` in runtime stage                                                    |
| Exec-form ENTRYPOINT             | Proper signal handling for graceful shutdown                                      |
| Container-aware JVM heap         | `RAMPercentage` flags instead of fixed `-Xmx`                                     |
| Fail-fast on OOM                 | `ExitOnOutOfMemoryError` + `HeapDumpOnOutOfMemoryError`                           |
| Least-privilege file permissions | `chmod 644` on the APM agent JAR                                                  |
