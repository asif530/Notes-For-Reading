Grafana Observability Stack: Architecture, Components, and Open Source vs Enterprise Comparison | by Akash Sahani | Medium
https://archive.is/EAc1x

## Summary: Grafana Observability Stack

This article makes the case for Grafana's open-source observability stack as a unified alternative to fragmented monitoring tools or expensive vendor-locked 
platforms like Datadog, New Relic, or Splunk.

**Core architecture and components**

The stack unifies the three pillars of observability — metrics, logs, and traces — through five main pieces working together:

- **Alloy** is the telemetry collection layer (replacing the older Grafana Agent), built on OpenTelemetry standards. It auto-discovers services in Kubernetes/Docker, 
  routes metrics to Mimir, logs to Loki, and traces to Tempo, and injects metadata so everything can be cross-referenced later.
- **Loki** handles log storage but only indexes metadata labels rather than full log text, which keeps storage costs low while still allowing 
  fast filtering via its Prometheus-like query language, LogQL.
- **Tempo** manages distributed tracing across microservices, using cheap object storage and schema-free ingestion so it can scale to high trace volumes 
   without heavy indexing overhead.
- **Mimir** provides horizontally scalable, Prometheus-compatible metrics storage with high cardinality support and effectively unlimited retention.
- **Grafana** itself is the visualization and correlation layer, letting users jump seamlessly between a metric spike, related logs, and the exact trace causing an 
  issue — all in one interface.

**Competitive comparisons**

The piece walks through component-level comparisons: Loki against Elasticsearch (cheaper/simpler vs. better full-text search), 
                                                     Tempo against Jaeger (better Grafana integration and cost efficiency), 
                                                     Mimir against Thanos (easier setup at scale), 
                                                 and Alloy against Fluent Bit (broader telemetry coverage vs. logs-only focus).

**Key takeaways**

Grafana stack's main advantages are vendor neutrality (avoiding lock-in), a composable/modular design (teams can adopt pieces incrementally), 
and cost efficiency (through label-based indexing and object storage), while still matching enterprise tools on performance and scalability. 

The tradeoff noted is that it requires more setup and technical expertise than fully managed commercial solutions.


### Three pillars of observability — metrics, logs, and traces. What are they

The three pillars of observability describe different types of telemetry data, each answering a different question about a system's behavior:

**Metrics** are numerical measurements aggregated over time, like CPU usage, request rate, error count, or latency percentiles. They answer "what is happening and how much?" Metrics are lightweight, cheap to store long-term, and great for dashboards, trends, and alerting thresholds (e.g., "alert if error rate exceeds 5%"), but they don't tell you why something happened, just that a number crossed a boundary.

**Logs** are timestamped, discrete text records emitted by applications or infrastructure, capturing specific events like an error message, a warning, or a debug statement. They answer "what exactly happened, in detail?" Logs are the most granular and context-rich signal, useful for root-cause investigation, but they can be high-volume and expensive to store and search if indexed naively (which is why Loki's label-based approach, mentioned in the article, is notable).

**Traces** follow a single request as it travels through multiple services in a distributed system, showing the full path and how much time was spent at each step (each step is called a "span"). They answer "where in the system did this request go, and where did it slow down or fail?" Traces are essential for pinpointing bottlenecks or failures in microservice architectures where a single user action might touch a dozen services.

In practice they complement each other: a metric might show a latency spike, a trace can show which specific service in the chain caused it, and logs can reveal the exact error or condition that triggered the problem in that service. That correlation across all three is exactly what tools like the Grafana stack are designed to make seamless.

### Can elastic apm be used for all three ?

Yes. Elastic APM (part of the Elastic Observability/Elastic Stack) is designed to handle both.

For tracing, Elastic APM auto-instruments applications (via language agents for Java, Python, Node.js, .NET, Go, Ruby, etc., or through OpenTelemetry) to capture distributed traces, showing transactions and spans as requests move across services, similar to what Tempo does in the Grafana stack.

For metrics, the APM agents also collect application-level metrics such as JVM/CLR memory and garbage collection stats, CPU and memory usage of the monitored process, and custom application metrics. These get shipped alongside trace data into Elasticsearch and can be visualized in Kibana's APM UI.

So in the "three pillars" framing, Elastic APM overlaps with both the metrics and traces pillars, and when paired with Elasticsearch's broader logging capabilities (e.g., Filebeat/Logstash), the Elastic Stack as a whole can cover all three pillars, much like the Grafana stack does with its separate Mimir/Tempo/Loki components. The key architectural difference is that Elastic tends to centralize data in Elasticsearch's index-based storage, whereas Grafana's stack uses separate specialized backends optimized for each data type.

### Connect dots between backend application, Logs, traces, metrics, monitor of these three. 

No browsing needed here either — this is a conceptual map, so let me lay out the mental model layer by layer.

**The big picture: think in 4 layers**

Every observability setup, regardless of vendor, breaks down into the same four layers. Data is generated inside your app, gathered and forwarded by an agent, stored in a backend suited to that data type, and finally queried/visualized on top.

```
┌─────────────────────────────────────────────────────────────--┐
│ 4. VISUALIZATION / QUERY                                      │
│    Grafana  |  Kibana  |  Jaeger UI  |  Datadog UI            │
└─────────────────────────── ▲──────────────────────────────────┘
                             │ queries
┌─────────────────────────────────────────────────────────────--┐
│ 3. STORAGE BACKENDS (one per signal type)                     │
│  METRICS: Prometheus TSDB, Mimir, Thanos, Cortex,             │
│           VictoriaMetrics, InfluxDB, Elasticsearch            │
│  LOGS:    Loki, Elasticsearch, Splunk, CloudWatch Logs        │
│  TRACES:  Tempo, Jaeger, Zipkin, Elasticsearch (APM), X-Ray   │
└─────────────────────────── ▲──────────────────────────────────┘
                             │ ships data via OTLP / remote-write / push API
┌─────────────────────────────────────────────────────────────--┐
│ 2. COLLECTION / AGENT LAYER (gathers, batches, routes)        │
│  Grafana Alloy | OpenTelemetry Collector | Fluent Bit/Fluentd │
│  Logstash/Filebeat (Beats) | Vector | Telegraf | Datadog Agent│
└─────────────────────────── ▲──────────────────────────────────┘
                             │ emits telemetry
┌─────────────────────────────────────────────────────────────--┐
│ 1. INSTRUMENTATION (inside your application code)             │
│  OpenTelemetry SDK/API | Micrometer | Spring Boot Actuator    │
│  Elastic APM agent | Prometheus client libraries              │
└─────────────────────────────────────────────────────────────--┘
```

**Layer 1 — Instrumentation: how telemetry is actually produced**

This is code running inside your application that generates metrics, logs, or trace spans.

- **OpenTelemetry (OTel)** is not a product but a vendor-neutral CNCF *standard*: it defines an API/SDK for instrumenting code and a wire protocol (OTLP) for shipping the data. Most modern tools, including Grafana's and increasingly Elastic's, are converging on this standard so you aren't locked into one vendor's agent format.
- **Micrometer** is a metrics-only instrumentation facade for the JVM, conceptually similar to how SLF4J abstracts logging frameworks. Your code calls Micrometer's API, and Micrometer can export those metrics in different formats (Prometheus, Datadog, New Relic, etc.) without you rewriting instrumentation code.
- **Spring Boot Actuator** is a Spring Boot module that exposes production-ready HTTP endpoints (health, info, and importantly `/actuator/prometheus`) which serve Micrometer-collected metrics in a format that Prometheus (or Alloy) can scrape.
- **Elastic APM agents** are language-specific agents (Java, Node, Python, etc.) that auto-instrument code for both traces and some metrics, tightly coupled to the Elastic ecosystem, similar in spirit to OTel SDKs but Elastic-proprietary (Elastic has also added OTel-compatibility more recently).

**Layer 2 — Collection/Agent: gathering and routing telemetry**

These are the "collectors" that sit near your infrastructure, scrape or receive telemetry, optionally transform it, and forward it to the right backend.

- **Grafana Alloy** (built on OpenTelemetry Collector code) is Grafana's unified agent — it can scrape Prometheus-style metrics, tail logs, and receive OTLP traces, then route each to Mimir, Loki, and Tempo respectively.
- **OpenTelemetry Collector** is the vendor-neutral reference collector implementation that Alloy is based on; you can use it directly instead of Alloy if you don't want Grafana-specific extensions.
- **Fluent Bit / Fluentd / Logstash / Filebeat (Beats)** are collectors focused mainly on logs, commonly paired with Elasticsearch/Splunk.
- **Telegraf** is InfluxData's collector, focused mainly on metrics, commonly paired with InfluxDB.
- **Vector** is another modern, high-performance log/metrics collector, vendor-neutral.
- **Datadog Agent** is Datadog's proprietary all-in-one collector for their SaaS platform.

**Layer 3 — Storage backends: one per signal type**

Each signal type has different storage needs (metrics are numeric time-series, logs are text blobs, traces are structured spans with parent-child relationships), so purpose-built backends exist for each:

- *Metrics*: **Prometheus** (the original scrape-based TSDB, single-node by default), **Mimir** (Grafana's horizontally scalable, long-term Prometheus-compatible store), **Thanos** and **Cortex** (older/alternative approaches to scaling Prometheus long-term storage — Cortex is actually what Mimir evolved from), **VictoriaMetrics** (a popular high-performance alternative TSDB), **InfluxDB** (general-purpose time-series database, pairs with Telegraf).
- *Logs*: **Loki** (Grafana's label-indexed, low-cost log store), **Elasticsearch** (full-text indexed, powers the "ELK" stack along with Logstash and Kibana), **Splunk** (commercial log platform), cloud-native options like **CloudWatch Logs**.
- *Traces*: **Tempo** (Grafana's object-storage-backed trace store), **Jaeger** (CNCF's widely-used open-source tracing backend, originally from Uber), **Zipkin** (an older, simpler open-source tracer), **AWS X-Ray** (AWS-native tracing), and **Elasticsearch via Elastic APM** (Elastic stores trace data as documents in Elasticsearch rather than a dedicated trace store).

**Layer 4 — Visualization/query**

**Grafana** and **Kibana** are the two most common dashboard/query front ends, each tied to their respective ecosystems (Grafana natively queries Mimir/Loki/Tempo/Prometheus; Kibana queries Elasticsearch). **Jaeger UI** is a standalone trace viewer if you're just running Jaeger without Grafana.

**How it all clicks together as "stacks"**

Rather than picking each layer separately, most teams adopt a pre-integrated stack:

- **Grafana "LGTM" stack**: Loki + Grafana + Tempo + Mimir, fed by Alloy, using OpenTelemetry where possible. This is what the article you read described.
- **Elastic Stack (ELK + APM)**: Elasticsearch as the single backend for logs, metrics, and traces, fed by Beats/Logstash for logs and Elastic APM agents for traces/metrics, visualized in Kibana.
- **DIY Prometheus-centric stack**: Prometheus for metrics, Jaeger or Zipkin for traces, ELK or Loki for logs — components glued together manually rather than as one product.
- **Commercial SaaS all-in-ones**: Datadog, New Relic, Dynatrace, Splunk Observability Cloud — proprietary agents and backends, less setup effort but with vendor lock-in and licensing cost.

**One clarifying point**: 
Prometheus itself is both an instrumentation client-library ecosystem *and* a metrics backend/TSDB — it straddles layers 1 and 3, 
which is why it's often described as "Prometheus-compatible" rather than cleanly fitting one layer, 
and why tools like Mimir and Thanos exist specifically to extend Prometheus's storage beyond single-node limits rather than replace its data model or query language (PromQL).