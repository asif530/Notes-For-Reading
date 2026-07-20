# Purpose
An Adapter translates one interface into another. It solves compatibility problems. 
Unlike Sidecar or Ambassador,

**It is usually an design / integration pattern.**

# Example
Suppose application expects

interface PaymentService {
pay();
}

But the third-party SDK provides

class StripeClient {
charge();
}

These APIs don't match.
The Adapter converts one into the other.

# Architecture
    Application -> Adapter -> Third-party Library
```java
class StripeAdapter implements PaymentService {
    private StripeClient stripe;
    @Override
    public void pay() {
        stripe.charge();
    }
}
```

The application thinks it's using PaymentService. Internally, the Adapter translates it into Stripe's API.

# Cloud Native Adapter
Suppose application publishes JSON but another system only accepts XML.
The Adapter converts  JSON -> Adapter -> XML
The application doesn't change.

