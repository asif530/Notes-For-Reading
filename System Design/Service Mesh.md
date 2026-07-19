# Service Mesh

## What is a Service Mesh?

A **Service Mesh** is a dedicated **infrastructure layer** that manages **service-to-service communication** in a microservices architecture — handling traffic, security, and observability **without changing application code**.

Think of it as:

> *"The [[Sidecar]] pattern applied consistently across an entire fleet of services, plus a control plane to manage them all."*

It is the most common **real-world implementation of the Sidecar pattern**.

---

## The Problem It Solves

In a large microservices system, every service ends up reimplementing the same networking concerns:

* Retries, timeouts, circuit breaking
* Load balancing
* mTLS / encryption
* Metrics, tracing, logging of calls
* Rate limiting, traffic shaping

Baking this into **every service's code**, in **every language**, is duplicated effort and inconsistent behavior.

> **"How do I get consistent networking behavior across all services without every team reimplementing it?"**

---

## The Core Idea

> **Attach a proxy (sidecar) to every service instance. Route all traffic through the proxy. Manage all proxies centrally.**

The application never talks to the network directly — it talks to its local sidecar, which talks to the destination's sidecar.

---

## Architecture: Data Plane + Control Plane

```
                     ┌───────────────────┐
                     │   Control Plane    │
                     │ (Istio / Linkerd)  │
                     └─────────┬─────────┘
                    configures │ all proxies
        ┌──────────────────────┼──────────────────────┐
        v                      v                       v
[ Service A ]           [ Service B ]            [ Service C ]
[  + Envoy  ] <-mTLS-> [  + Envoy   ] <-mTLS->  [  + Envoy   ]
   (sidecar)              (sidecar)                (sidecar)

              ------------- Data Plane -------------
```

### Data Plane
* The actual **sidecar proxies** (usually **Envoy**) sitting next to every service
* Intercepts **all inbound/outbound traffic**
* Executes: load balancing, retries, timeouts, mTLS, circuit breaking

### Control Plane
* Centrally configures and manages every proxy
* Pushes routing rules, security policy, certificates
* Examples: **Istio**, **Linkerd**, **Consul Connect**

---

## What It Handles

* **Traffic management** — load balancing, retries, timeouts, circuit breaking, canary / blue-green routing
* **Security** — mutual TLS (mTLS) between services, authN/authZ policies
* **Observability** — metrics, distributed tracing, access logs for every hop
* **Resilience** — fault injection, rate limiting, retries

👉 Your Spring Boot service does **not** know any of this exists — same guarantee as the plain [[Sidecar]] pattern.

---

## Relation to Sidecar Pattern

A service mesh **is** the Sidecar pattern, scaled out and centrally governed:

| Aspect        | Sidecar (general)                     | Service Mesh                          |
|---------------|----------------------------------------|----------------------------------------|
| Scope         | One helper attached to one service     | Sidecar attached to **every** service  |
| Management    | Configured per-instance / ad hoc       | Centrally managed by a **control plane** |
| Examples      | Logging agent, metrics exporter        | Istio, Linkerd, Consul Connect          |
| Consistency   | Depends on how it's rolled out         | Uniform behavior across the whole fleet |

See [[Sidecar]] for the underlying deployment pattern.

---

## Relation to Outbox Pattern

Different layer, different concern — but both show up in the same event-driven microservices stack:

| Aspect      | Service Mesh                      | [[Outbox]]                 |
|-------------|-------------------------------------|----------------------------|
| Category    | Network / infra layer               | Data consistency layer     |
| Problem     | Reliable, secure, observable calls  | Reliable event publishing  |
| Scope       | Synchronous service-to-service      | DB + messaging (async)     |
| Used With   | Kubernetes, Envoy, Istio            | Kafka, RabbitMQ            |

A mesh secures and observes the **live request path** between services; Outbox guarantees the **event gets published** once a DB transaction commits. A system can use both — mesh for the synchronous calls, Outbox for the async events.

---

## Advantages

✅ No application code changes
✅ Consistent behavior across services, regardless of language
✅ Strong security defaults (mTLS everywhere)
✅ Deep observability without instrumenting every service

---

## Drawbacks

❌ Extra resource usage (a proxy per instance)
❌ Operational complexity (control plane to run and upgrade)
❌ Extra network hop → latency and harder debugging

---

## When to use a Service Mesh?

✔ Large number of microservices
✔ Kubernetes-based deployments
✔ Need uniform security (mTLS) and observability across teams
✔ Platform team wants to manage networking behavior centrally, not per-service

---

## Interview One-Liner

> "A service mesh is infrastructure that manages service-to-service traffic — load balancing, mTLS, retries, observability — via per-service sidecar proxies (data plane) coordinated by a central control plane, without touching application code."