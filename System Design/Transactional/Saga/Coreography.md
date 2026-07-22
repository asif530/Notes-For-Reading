There is no central coordinator — each service reacts to events published by others and independently decides what to do,
including publishing its own events for the next service to react to.

- Implicit flow: Order Service publishes OrderCreated → Payment Service listens for that, processes payment, publishes PaymentSucceeded
  → Inventory Service listens for that, reserves stock, publishes InventoryReserved → ... and so on
- Saga state is implicit, reconstructed by replaying/correlating events across all services — no single table tells you "where the saga
  is right now"
- Every service needs to know which events to listen for and what to do in response, including compensations
- Services are more decoupled from each other (no one central point of knowledge), but more coupled to the event contracts  