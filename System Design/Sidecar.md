# Sidecar Pattern 
# Sample Code: https://github.com/asif530/SB-Outbox-Sidecar

## What is the Sidecar Pattern?

The **Sidecar pattern** is a **deployment pattern** where a helper component (the *sidecar*) runs **alongside a main application**, 
sharing the same lifecycle, network, and sometimes storage. It is usually deployed inside the same pod of the main application container
**Its a deployment pattern.**

Think of it as:

> *“An auxiliary process that adds capabilities to an application **without changing the application code**.”*

📦 In containerized environments (Docker / Kubernetes), the sidecar is typically **another container in the same Pod**.

---

## Architecture
+-------------------------------------+
|               Pod                   |
|                                     |
| +-------------------------------+   |
| | Spring Boot Application       |   |
| +-------------------------------+   |
|                                     |
| +-------------------------------+   |
| | Sidecar                       |   |
| | Envoy / Fluent Bit / OTEL     |   |
| +-------------------------------+   |
+-------------------------------------+

Both containers

start together
stop together
share networking
may share storage

## Why do we need it?

Applications often need **cross-cutting concerns**:

* Logging
* Monitoring
* Security
* Service-to-service communication
* Configuration
* Traffic management

Instead of baking these concerns into **every service**, we offload them to a **sidecar**. Less code to write. Kind of outsourcing tasks.

---

## How it works (Conceptual Flow)

```
                                 Client
                                   |
                                   v
[ Sidecar Proxy ]  <----->  [ Application ]
        |
        v
  Other Services
```
* Client talks to **application**
* The **application** talks to the sidecar
* The **sidecar** handles:
    * Retries
    * Timeouts
    * TLS
    * Metrics
    * Rate limiting
  
* App code stays **simple and focused on business logic**

---

## Real-World Examples

### 🔹 Service Mesh (Most Important) 

* **Istio**
* **Linkerd**
* **Consul Connect**

Each service gets a **proxy sidecar (Envoy)**.

**What Envoy sidecar does:**

* Load balancing
* Circuit breaking
* mTLS
* Observability
* Traffic shaping

👉 Your Spring Boot service does **not** know any of this exists.

See [[Service Mesh]] for how this pattern is scaled out and centrally managed across an entire fleet of services.

---

### 🔹 Logging / Monitoring

* Fluentd sidecar collects logs
* Prometheus exporter sidecar exposes metrics

---

## Advantages

✅ No code changes
✅ Separation of concerns
✅ Language agnostic
✅ Consistent behavior across services

---

## Drawbacks

❌ Extra resource usage
❌ Operational complexity
❌ Debugging is harder (network hops)

---

## When to use Sidecar?

✔ Microservices
✔ Kubernetes
✔ Need infrastructure-level features
✔ Platform teams managing behavior centrally

---

## Interview One-Liner

> “The Sidecar pattern attaches auxiliary functionality like networking, security, or observability to a service **without modifying the service itself**, 
> commonly used in service meshes.”
