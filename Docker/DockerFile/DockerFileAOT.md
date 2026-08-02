### This dockerfile is created after springboot 4 and implemented jvm aot with spring aot
## DockerFile
# Digest-pinned JRE base shared by the AOT-training and runtime stages, so the JVM
# that creates app.aot is identical to the one that consumes it. Refresh the digest
# when bumping the JRE:
ARG RUNTIME_BASE=eclipse-temurin:25-jre-noble@sha256:f9bd8815e73632c22985ebb133ec49b9fc4ad5ffe0657594ac02748ad0431ab7

############################################
# Stage 1 — Resolve dependencies (warm Gradle cache)
############################################
FROM eclipse-temurin:25-jdk-noble AS deps

# GRADLE_USER_HOME lives under /workspace so the warmed cache is carried into the
# builder stage by `COPY --from=deps /workspace/`
ENV GRADLE_USER_HOME=/workspace/.gradle-home \
GRADLE_OPTS="-Dorg.gradle.daemon=false -Dorg.gradle.welcome=never"

WORKDIR /workspace

# Only files that influence dependency resolution — code edits never bust this layer.
COPY gradlew settings.gradle build.gradle gradle.properties ./
COPY gradle gradle
COPY api/build.gradle    api/build.gradle
COPY proto/build.gradle  proto/build.gradle
COPY flyway/build.gradle flyway/build.gradle

# All dependencies resolve from Maven Central — no private repo required.
RUN chmod +x ./gradlew && \
./gradlew --no-daemon --no-watch-fs \
:api:dependencies   --configuration runtimeClasspath \
:proto:dependencies --configuration runtimeClasspath \
-q || true


############################################
# Stage 2 — Build the Spring Boot layered JAR
############################################
FROM eclipse-temurin:25-jdk-noble AS builder

ENV GRADLE_USER_HOME=/workspace/.gradle-home \
GRADLE_OPTS="-Dorg.gradle.daemon=false -Dorg.gradle.welcome=never"

WORKDIR /workspace

# Carries the warmed Gradle cache (under /workspace) plus the build scripts.
COPY --from=deps /workspace/ /workspace/

# processAot reads config/aot-training.env at configuration time — needed here too.
COPY config/aot-training.env config/aot-training.env

# Sources last so a code edit only invalidates this layer + the application JAR layer.
COPY proto/src proto/src
COPY api/src   api/src

RUN chmod +x ./gradlew && \
./gradlew --no-daemon --no-watch-fs \
:api:bootJar -x test \
--parallel --build-cache && \
mkdir -p /out && \
cp api/build/libs/*.jar /out/app.jar

# Modern Spring Boot extraction (replaces deprecated layertools).
WORKDIR /out
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher --destination layered


############################################
# Stage 3 — Elastic APM agent, checksum-verified, cacheable
############################################
FROM eclipse-temurin:25-jdk-noble AS apm
ARG APM_VERSION=1.55.0
ARG APM_SHA256=3cbba96a64593c14568399dbc816fc36a5647e39449e1d4cf1eedce9880a9d3e
ADD https://repo1.maven.org/maven2/co/elastic/apm/elastic-apm-agent/${APM_VERSION}/elastic-apm-agent-${APM_VERSION}.jar \
/apm/elastic-apm-agent.jar
# Verify integrity (replaces BuildKit's `ADD --checksum`).
RUN echo "${APM_SHA256}  /apm/elastic-apm-agent.jar" | sha256sum -c -


############################################
# Stage 3b — AOT cache training run (baked, static)
# Must mirror the runtime stage exactly: same base, WORKDIR, extracted layout,
# copy order, JarLauncher launch, and GC. Any divergence makes the JVM silently
# ignore the cache at runtime.
############################################
FROM ${RUNTIME_BASE} AS aotcache
WORKDIR /app

# Same layout + copy order as runtime, from the same builder output so jar bytes
# and mtimes match. Do NOT re-extract here.
COPY --from=builder /out/layered/dependencies/          ./
COPY --from=builder /out/layered/spring-boot-loader/    ./
COPY --from=builder /out/layered/snapshot-dependencies/ ./
COPY --from=builder /out/layered/application/           ./

# Placeholder fillers for the templated yaml; inert against a concrete prod yaml.
# The offline -D flags below override any yaml. Baked only into this throwaway stage.
COPY config/aot-training.env /tmp/aot-training.env

# Clean cache (no APM agent). GC + StringDedup mirror the runtime ENTRYPOINT.
# The -D switches force an offline context refresh — the CI builder can't reach
# real backing services. app.aot lands at /app, the exact path runtime consumes.
# --add-modules java.instrument matches the module set the runtime APM -javaagent
# adds implicitly; without it the runtime JVM rejects the cache on a module mismatch.
RUN set -a && . /tmp/aot-training.env && set +a && \
java \
--add-modules java.instrument \
-XX:+UseG1GC \
-XX:+UseStringDeduplication \
-Xmx1g \
-XX:AOTCacheOutput=/app/app.aot \
-Dspring.aot.enabled=true \
-Dspring.context.exit=onRefresh \
-Dspring.cloud.vault.enabled=false \
-Dspring.cloud.vault.fail-fast=false \
-Dapp.datasource.read.hikari.initialization-fail-timeout=-1 \
-Dapp.datasource.write.hikari.initialization-fail-timeout=-1 \
-Dapp.training.allow-jdbc-metadata-access=false \
org.springframework.boot.loader.launch.JarLauncher \
&& test -s /app/app.aot


############################################
# Stage 4 — Runtime
############################################
FROM ${RUNTIME_BASE} AS runtime

# Non-root user (fixed UID/GID for predictable k8s PSP/PSA).
# app.aot is baked read-only and only consumed at runtime — no writable CDS dir needed.
RUN groupadd --system --gid 10001 app \
&& useradd  --system --uid 10001 --gid app --home-dir /app --shell /sbin/nologin app \
&& mkdir -p /app /tmp/heapdumps \
&& chown -R app:app /app /tmp/heapdumps

WORKDIR /app

# Spring Boot layers — least- to most-changing.
# A code-only change re-uploads only the `application/` layer (~few MB).
COPY --chown=10001:10001 --from=builder /out/layered/dependencies/          ./
COPY --chown=10001:10001 --from=builder /out/layered/spring-boot-loader/    ./
COPY --chown=10001:10001 --from=builder /out/layered/snapshot-dependencies/ ./
COPY --chown=10001:10001 --from=builder /out/layered/application/           ./

# APM agent in its own layer — changes on its own cadence.
COPY --chown=10001:10001 --from=apm /apm/elastic-apm-agent.jar /app/elastic-apm-agent.jar

# Baked JVM AOT cache — created clean (no agent) in the aotcache stage. Read-only,
# consumed via -XX:AOTCache below; same absolute path (/app) it was trained at.
COPY --chown=10001:10001 --from=aotcache /app/app.aot /app/app.aot

USER app:app
EXPOSE 8080

# Docker-level health probe against Spring Boot Actuator.
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
CMD ["bash","-c","exec 3<>/dev/tcp/127.0.0.1/8080 && printf 'GET /actuator/health/liveness HTTP/1.0\\r\\nHost: localhost\\r\\n\\r\\n' >&3 && grep -q '\"status\":\"UP\"' <&3"]

ENTRYPOINT ["java", \
"-XX:InitialRAMPercentage=50.0", \
"-XX:MaxRAMPercentage=60.0", \
"-XX:+UseG1GC", \
"-XX:MaxGCPauseMillis=200", \
"-XX:+UseStringDeduplication", \
"-XX:+HeapDumpOnOutOfMemoryError", \
"-XX:HeapDumpPath=/tmp/heapdumps", \
"-XX:+ExitOnOutOfMemoryError", \
"-XX:AOTCache=/app/app.aot", \
"-Dspring.aot.enabled=true", \
"-javaagent:/app/elastic-apm-agent.jar", \
"-Delastic.apm.disable_instrumentations=messaging", \
"org.springframework.boot.loader.launch.JarLauncher"]

## Explanation

This Dockerfile builds a Spring Boot application (targeting Spring Boot with JDK 25 / "Spring Boot 4" as the comment notes) using JVM Ahead-of-Time (AOT) caching, in a 6-stage multi-stage build.

### Overall architecture: multi-stage, multi-purpose build

There are 5 named stages, each with a narrow job, so Docker's layer cache and BuildKit's parallelism can be exploited maximally:

| Stage      | Base image              | Purpose                                            |
|------------|-------------------------|----------------------------------------------------|
| `deps`     | JDK 25                  | Pre-warm the Gradle dependency cache               |
| `builder`  | JDK 25                  | Compile & package the actual application JAR       |
| `apm`      | JDK 25                  | Fetch + checksum-verify the Elastic APM Java agent |
| `aotcache` | JRE 25 (`RUNTIME_BASE`) | Produce the JVM AOT cache file (`app.aot`)         |
| `runtime`  | JRE 25 (`RUNTIME_BASE`) | Final, minimal, non-root image that actually ships |

Only `runtime` becomes the shipped image — everything else is discarded, so the final image contains no JDK, no Gradle, no build tools, just a JRE + the app + the APM agent + the pre-baked AOT cache.

### Key technical concepts

**1. Dependency-cache warming split from source build (`deps` vs `builder`)**
`deps` only copies build files that affect dependency resolution (`build.gradle`, `settings.gradle`, module `build.gradle` files) — not source code. This means editing application code never invalidates this Docker layer, so `./gradlew :api:dependencies` (which just forces Gradle to download everything into `GRADLE_USER_HOME=/workspace/.gradle-home`) is cached across builds. `builder` then does `COPY --from=deps /workspace/ /workspace/` to inherit that warmed cache before copying in actual source and compiling. This is a classic "cache the expensive, rarely-changing part separately from the fast-changing part" pattern applied to Docker's layer model.

**2. Spring Boot layered jars (not the deprecated `layertools` launcher)**
Instead of shipping one fat jar, stage 2 extracts the built jar into distinct layers (`dependencies/`, `spring-boot-loader/`, `snapshot-dependencies/`, `application/`) via the new `java -Djarmode=tools -jar app.jar extract` command (replacing the older `-Djarmode=layertools`). These layers are then copied into the runtime image **in order from least-to-most frequently changing**. This means a pure code change only invalidates/re-uploads the small `application/` layer, not the whole dependency tree — much smaller image push/pull deltas in CI/CD and registries.

**3. JVM AOT cache (Project Leyden-style `-XX:AOTCacheOutput` / `-XX:AOTCache`)**
This is the most novel part. Java's AOT cache (introduced as part of the AOT/Leyden work, paired with Spring's own `spring.aot.enabled`) lets you pre-run the app once ("training run") to record class-loading/JIT-relevant metadata into a binary cache file (`app.aot`), which the real runtime process loads via `-XX:AOTCache=/app/app.aot` to start up faster (skipping class parsing/verification/some JIT warmup work it already recorded).

- Stage `aotcache` runs the app with `-XX:AOTCacheOutput=/app/app.aot` and `-Dspring.context.exit=onRefresh` — meaning "boot Spring, let it fully refresh the context (so all the classes/beans get touched), then exit immediately" — purely to produce the cache artifact, not to actually serve traffic.
- Because the AOT cache is sensitive to exact JVM version, classpath layout, and file paths, the comments are emphatic that `aotcache` must mirror `runtime` **exactly**: same base image (`RUNTIME_BASE` shared via `ARG` + pinned digest), same `WORKDIR /app`, same layer copy order, same launcher class, same GC flags, even the same `--add-modules java.instrument` (because the runtime stage loads the APM agent via `-javaagent`, which implicitly adds that module — if the training run doesn't also add it, the module graph won't match and the JVM will silently discard the cache instead of erroring).
- The training run explicitly disables things that would try to reach real infrastructure (Vault, JDBC metadata access, Hikari fail-fast) via system properties sourced from `config/aot-training.env`, since this happens in an offline CI builder with no real backing services.
- `test -s /app/app.aot` at the end guards against silently shipping an empty/failed cache file.

**4. Digest-pinned shared base image (`ARG RUNTIME_BASE=...@sha256:...`)**
Both `aotcache` and `runtime` stages derive from the identical image reference, pinned by content digest rather than just a tag. This guarantees byte-for-byte JVM identity between the stage that *creates* the AOT cache and the stage that *consumes* it — a floating tag (`:25-jre-noble`) could drift between builds and silently invalidate the cache. Comments tell maintainers to bump the digest deliberately when upgrading the JRE.

**5. Supply-chain integrity via checksum verification**
Rather than relying on Docker's newer `ADD --checksum` flag, the APM agent stage manually does `ADD <url> ...` then `sha256sum -c` against a pinned `APM_SHA256` build arg — verifying the downloaded jar hasn't been tampered with/corrupted, while staying compatible with builders that don't support the checksum flag.

**6. Least-privilege runtime (non-root, fixed UID/GID)**
The `runtime` stage creates a system user/group with fixed numeric UID/GID `10001`, matching common Kubernetes Pod/Container Security Standards (mentioned as "PSP/PSA" — Pod Security Policy/Pod Security Admission) that require predictable non-root UIDs. Files are copied with `--chown=10001:10001` directly during `COPY` (avoiding an extra `RUN chown` layer).

**7. Container health checks aligned with Spring Boot Actuator**
The `HEALTHCHECK` doesn't use `curl`/`wget` (often absent in minimal JRE images); instead it uses a raw bash `/dev/tcp` pseudo-device to open a TCP socket and manually write a minimal HTTP/1.0 GET request against `/actuator/health/liveness`, checking for `"status":"UP"` in the response. This avoids needing extra packages in the image just for health checks.

**8. Runtime JVM tuning flags**
The final `ENTRYPOINT` sets:
- `-XX:InitialRAMPercentage`/`MaxRAMPercentage` — container-aware heap sizing (percentage of the cgroup memory limit, not host RAM)
- `-XX:+UseG1GC` + `MaxGCPauseMillis=200` — low-pause garbage collector tuned for pause-time
- `-XX:+UseStringDeduplication` — reduces duplicate `String` memory overhead (G1-specific feature)
- `-XX:+HeapDumpOnOutOfMemoryError` + `HeapDumpPath` + `-XX:+ExitOnOutOfMemoryError` — crash-and-restart-cleanly-with-diagnostics pattern instead of limping along in a broken state (important for k8s liveness/restart semantics)
- `-XX:AOTCache=/app/app.aot` + `-Dspring.aot.enabled=true` — actually consumes the pre-baked AOT cache for faster startup
- `-javaagent:/app/elastic-apm-agent.jar` — APM instrumentation attached at JVM bootstrap, with messaging instrumentation explicitly disabled (likely to reduce noise/overhead for a message-queue-heavy app)

### Summary

Architecturally, this is a **build-time-shifted startup optimization** pattern: rather than paying JVM class-loading/JIT costs at every pod start (expensive in autoscaling/rolling-deploy scenarios), the image bakes a Spring-context-aware AOT cache once at build time in CI, under carefully controlled conditions to guarantee bit-for-bit JVM/environment parity with production, and ships that artifact alongside a minimal, layered, non-root, integrity-verified runtime image.
