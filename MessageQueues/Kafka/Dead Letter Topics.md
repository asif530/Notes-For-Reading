# Retry and Dead Letter Queue in Kafka

**Source:** [Retry and Dead Letter Queue in Kafka](https://medium.com/@turkishtechnology/retry-and-dead-letter-queue-in-kafka-28335b43c832)

Summary of the article, plus worked-through code samples.

---

## Overview

The article addresses a common problem in Kafka-based systems: what happens when a consumer fails to process a message. By default, Kafka will keep retrying a failed message immediately and repeatedly, which blocks the consumer from moving on to subsequent messages and can stall the whole pipeline.

The author walks through progressively more robust ways to handle this, ending with a full retry-topic-plus-dead-letter-queue design.

---

## Key Concepts

### 1. Bounded retry

Configure a simple bounded retry policy (e.g., retry a fixed number of times with a fixed delay between attempts) so that transient errors don't cause infinite blocking. This works fine for short-lived glitches but isn't enough for persistent failures, like a downstream API or database being down for an extended period — even five quick retries will still fail, and just increasing the count risks a growing backlog.

### 2. Retry Topic

Instead of blocking the main consumer while retrying, a failed message is immediately handed off to a secondary **"Retry Topic,"** freeing the original consumer to keep processing new messages. A separate listener handles retrying messages from that topic.

### 3. Dead Letter Queue (DLQ)

Since even the retry topic can't guarantee eventual success, once a message has failed a set number of retries (tracked via a retry-count value attached to the message), it gets routed to a dedicated "dead letter" topic. A final consumer listens to that DLQ topic purely to log the failure and persist a record of it (e.g., to a database or a monitoring system), so failed events aren't silently lost even though they're no longer retried automatically.

### 4. Further refinements

Batch retries on a schedule (e.g., a cron-style job every few minutes) rather than retrying instantly, to reduce load on downstream systems during outages.

---

## Architecture Diagram

Preferred design — retry topic + dead letter topic:

```
┌───────────┐        message        ┌───────────────────┐
│  Producer │ ─────────────────────▶│ Topic: createOrder │
└───────────┘                       └──────────┬──────────┘
                                                │
                                                ▼
                                     ┌─────────────────────┐        success        ┌────────────┐
                                     │      Consumer       │ ─────────────────────▶│  API call  │
                                     │  (main listener)    │                       │  succeeds  │
                                     └──────────┬───────────┘                       └────────────┘
                                                │ on failure (retryCount = 0)
                                                ▼
                                     ┌─────────────────────┐
                                     │ Topic: createOrder  │
                                     │       Retry         │◀─────────────┐
                                     └──────────┬───────────┘              │
                                                │                          │ failure,
                                                ▼                          │ retryCount++
                                     ┌─────────────────────┐               │
                                     │   Retry Consumer    │───────────────┘
                                     │  tries API again    │
                                     └──────────┬───────────┘
                                                │ retryCount == max (5)
                                                ▼
                                     ┌─────────────────────┐
                                     │ Topic: createOrder  │
                                     │     DeadLetter      │
                                     └──────────┬───────────┘
                                                ▼
                                     ┌─────────────────────┐
                                     │    DLQ Consumer     │
                                     │ - logs the failure  │
                                     │ - writes record to DB│
                                     └─────────────────────┘
```

**In words:** a message starts on the main topic; if the consumer's API call fails, the message moves to a retry topic instead of blocking; the retry consumer keeps trying and bumps a retry counter on each failure; once that counter reaches the configured max, the message is shunted to a dead letter topic; a final consumer there simply records the failure so nothing is silently dropped, while the rest of the pipeline keeps flowing.

---

## Article's Code Snippets (summarized, not reproduced)

1. **Bounded retry handler** — configures a Spring Boot Kafka listener container with an error handler that retries a failed message a fixed number of times at a fixed interval before giving up, with a special case for logging serialization errors.
2. **Retry-topic hand-off** — two listener methods: one on the main topic that tries to call an external API and, on failure, forwards the message to the retry topic; and one on the retry topic that simply attempts the same API call again.
3. **Retry-count + DLQ** — extends the above by attaching a retry-count value to each message header. Each time the retry-topic listener fails, it increments that count; once the count hits the configured maximum, the message is forwarded to the dead-letter topic instead of being retried again. A third listener on the dead-letter topic logs the failure and writes it to a database for record-keeping.

---

## Sample Code (own worked-through examples)

> **Note:** The three samples below hand-roll retry topics, backoff, and DLQ routing manually. Spring Kafka (since 2.7.0) actually provides this as a **built-in** feature: annotate a listener with `@RetryableTopic` to get automatic non-blocking retries with configurable backoff and auto-created retry topics, and add a `@DltHandler` method to handle messages once retries are exhausted — no manual retry-count headers or hand-off logic required. The manual approach here is still useful to understand what's happening under the hood, but in a real Spring Kafka app, prefer `@RetryableTopic`/`@DltHandler` over reimplementing it.

### 1. Bounded Retry Handler
*(fixed retries, no blocking beyond limit)*

Configures a Kafka listener container so that a failed message is retried a fixed number of times with a fixed delay, instead of retrying forever.

```java
@Configuration
@EnableKafka
public class KafkaRetryConfig {

    @Bean
    public ConcurrentKafkaListenerContainerFactory<?, ?> listenerContainerFactory(
            ConsumerFactory<Object, Object> consumerFactory) {

        ConcurrentKafkaListenerContainerFactory<Object, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(boundedRetryHandler());
        return factory;
    }

    @Bean
    public DefaultErrorHandler boundedRetryHandler() {
        // 4 retries, 2 seconds apart
        BackOff backOff = new FixedBackOff(2000L, 4);

        return new DefaultErrorHandler((record, exception) -> {
            if (exception instanceof SerializationException) {
                log.error("Could not deserialize record: {}", record, exception);
            }
        }, backOff);
    }
}
```

### 2. Retry-Topic Hand-off
*(non-blocking retry via a secondary topic)*

When processing fails, the message is forwarded to a dedicated retry topic so the main consumer can immediately continue with the next message.

```java
@Service
public class OrderEventListener {

    @KafkaListener(topics = "orders", groupId = "orders-group")
    public void onOrderCreated(String payload) {
        try {
            callOrderApi(payload);
        } catch (Exception ex) {
            forwardToRetryTopic(payload);
        }
    }

    @KafkaListener(topics = "orders-retry", groupId = "orders-group")
    public void onOrderRetry(String payload) {
        callOrderApi(payload);
        // any failure here is handled by the container's own retry/backoff
    }
}
```

### 3. Retry Count + Dead Letter Queue
*(full workflow)*

Tracks how many times a message has been retried using a header value. Once the count reaches a maximum, the message is routed to a dead letter topic instead of being retried again, and a separate consumer logs/persists it.

```java
@Service
public class OrderEventListenerWithDlq {

    private static final int MAX_RETRIES = 5;

    @KafkaListener(topics = "orders", groupId = "orders-group")
    public void onOrderCreated(String payload) {
        try {
            callOrderApi(payload);
        } catch (Exception ex) {
            sendToRetryTopic(payload, 0);
        }
    }

    @KafkaListener(topics = "orders-retry", groupId = "orders-group")
    public void onOrderRetry(String payload,
                              @Header("retryCount") int retryCount) {
        try {
            callOrderApi(payload);
        } catch (Exception ex) {
            int nextCount = retryCount + 1;
            if (nextCount >= MAX_RETRIES) {
                sendToDeadLetterTopic(payload);
            } else {
                sendToRetryTopic(payload, nextCount);
            }
        }
    }

    @KafkaListener(topics = "orders-dlq", groupId = "orders-group")
    public void onOrderDeadLettered(String payload) {
        log.warn("Message moved to DLQ after {} retries: {}", MAX_RETRIES, payload);
        persistFailedEvent(payload);
    }
}
```
