A central coordinator (the orchestrator) tells each participant what to do and decides what happens next based on their responses.

- Explicit command/response flow: orchestrator → "do X" → participant → "X succeeded/failed" → orchestrator decides next step
- Saga state lives in one place (in this project, order-service's saga table)
- The orchestrator is the only thing that knows the full sequence of steps
- Participants are "dumb" — they just execute a command and report back; they don't know a saga exists at all

In this project: order-service is the orchestrator. It publishes CreatePaymentCommand, waits for PaymentSucceeded/PaymentFailed, then  
decides to publish ReserveInventoryCommand or fail the saga. Payment/Inventory services never talk to each other directly or know what
step comes next. 