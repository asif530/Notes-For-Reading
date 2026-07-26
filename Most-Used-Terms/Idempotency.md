# Idempotency

# Consumer Idempotency
is the property of a message/event consumer where processing the same message multiple times produces the same end result as processing it
exactly once — no duplicate side effects.

Common implementation strategies

1. Deduplication table / idempotency key
   Track processed message IDs (e.g., in a DB table or Redis set with TTL). Before processing, check if the ID was already handled; skip if so.
2. Natural idempotency via upserts
   Design the operation so replays are naturally safe — e.g., UPDATE balance SET amount = 100 instead of amount = amount + 10, or INSERT ... ON CONFLICT DO
   NOTHING.
3. Version/state checks (optimistic concurrency)
   Only apply the update if the entity's current version matches the expected prior version (compare-and-swap style), rejecting stale/duplicate applies.
4. Atomic "process + record" transaction
   Perform the business update and the "mark as processed" write in the same DB transaction, so a crash between them can't cause a silent duplicate or a silent
   skip.
5. Idempotency keys from the producer

Producer attaches a unique key (e.g., UUID, or a business key like order_id + event_type) to each logical event; consumer dedupes on that key rather than
trusting message-broker-level offsets alone.

Key nuance

Exactly-once delivery is hard to guarantee at the transport layer, so systems instead aim for at-least-once delivery + idempotent consumers = effectively-once
processing. That's the standard pattern in event-driven/microservices architectures.