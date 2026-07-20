# Purpose
An Ambassador is actually a specialized Sidecar. Instead of adding generic capabilities,
it acts as the application's proxy to the outside world. **Its a deployment pattern.**

Think of it as
    "The application's personal representative."

## Architecture
    Application
        ↓
    Ambassador
        ↓
   External Service

The application never communicates directly with external systems.
Everything goes through the Ambassador.

# Example 1 
Suppose application calls

payment.company.com

Instead of
    Spring Boot -> Payment API
the flow becomes
    Spring Boot -> Envoy Ambassador -> Payment API

Now the Ambassador handles
    retries
    timeout
    TLS
    authentication
    load balancing
    failover
without changing application code.

## Why use it?
Imagine the payment provider changes
    payment.company.com -> payments.company.com
Only the Ambassador configuration changes. The application remains untouched.

# Example 2
A Spring Boot application performs
    restTemplate.getForObject("http://localhost:15001/payment");

Envoy forwards the request to: https://payment.company.com

If tomorrow the provider changes, nothing changes inside Spring Boot.

Characteristics
1. Handles outbound communication
2. Acts as a client proxy
3. Usually implemented using Envoy or Nginx
4. A specialized form of Sidecar