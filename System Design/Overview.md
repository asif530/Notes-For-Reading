## Sidecar, Ambassador, Adapter
# Sidecar

Adds infrastructure capabilities.

        +------------------+
        | Spring Boot App  |
        +------------------+
                 │
        ┌────────┴────────┐
        │                 │
     Business Logic     Sidecar
                         Logging
                         Metrics
                         Tracing
# Ambassador
Represents the application when communicating externally.
    Application -> Ambassador -> External Service


# Adapter
Converts one interface into another.
    Application -> Adapter -> Different API

## Real Spring Boot Examples

**Sidecar**

Spring Boot -> OpenTelemetry Collector Sidecar -> Jaeger
Spring Boot -> Fluent Bit Sidecar -> Loki
Spring Boot -> Envoy Sidecar -> Other Services

**Ambassador**

Spring Boot -> Envoy Ambassador -> External Payment Provider
Spring Boot -> Nginx Ambassador -> Legacy SOAP Server

**Adapter**

Spring Boot -> StripeAdapter -> Stripe SDK
Spring Boot -> Rest ->LegacyAdapter -> Old SOAP API
Spring Boot -> JsonToXmlAdapter ->Legacy XML Service

**Sidecar vs Ambassador vs Adapter**
├───────────────────────────┼────────────────────────────────────────────┼──────────────────────────────┼────────────────────────────────────────────┤                     
│ Usually another container │ Yes                                        │ Yes                          │ Usually a class, library, or service       │                     
├───────────────────────────┼────────────────────────────────────────────┼──────────────────────────────┼────────────────────────────────────────────┤                     
│ Modifies business code    │ No                                         │ No                           │ Usually no (translation layer)             │                     
├───────────────────────────┼────────────────────────────────────────────┼──────────────────────────────┼────────────────────────────────────────────┤                     
│ Handles logging/metrics   │ Yes                                        │ Sometimes                    │ No                                         │                     
├───────────────────────────┼────────────────────────────────────────────┼──────────────────────────────┼────────────────────────────────────────────┤                     
│ Handles outbound requests │ Sometimes                                  │ Yes (primary purpose)        │ No                                         │                     
├───────────────────────────┼────────────────────────────────────────────┼──────────────────────────────┼────────────────────────────────────────────┤                     
│ Translates APIs           │ No                                         │ No                           │ Yes                                        │                     
├───────────────────────────┼────────────────────────────────────────────┼──────────────────────────────┼────────────────────────────────────────────┤                     
│ Typical tools             │ Envoy, Fluent Bit, OpenTelemetry Collector │ Envoy, Nginx, HAProxy        │ Java Adapter classes, Integration Services │                     
└───────────────────────────┴────────────────────────────────────────────┴──────────────────────────────┴────────────────────────────────────────────┘

Relationship Between Them: Sidecar is the general deployment pattern of running a helper process alongside the app; Ambassador is a specialized sidecar focused            
specifically on proxying outbound network communication; Adapter is a different kind of pattern (not tied to deployment) that translates one interface into another the app
expects.

The easiest way to remember them is:
    Sidecar → "I add capabilities to my application."
    Ambassador → "I represent my application when talking to the outside world."
    Adapter → "I translate one interface into another."

**An Ambassador is often implemented as a specialized Sidecar, whereas an Adapter is a completely different pattern focused on compatibility rather than deployment.**

Interview Summary

Sidecar	-> Adds infrastructure capabilities to an application without changing its code.
Ambassador ->	A specialized Sidecar that acts as a proxy for outbound communication with external services.
Adapter	-> Converts one interface or protocol into another so incompatible components can work together.