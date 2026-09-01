# “If a method is called while a transaction already exists, what should Spring do with the transaction?”
=> The basic scenario
API Request
    │
    ▼
OrderService.placeOrder()
    │
    │  Transaction A starts
    ▼
PaymentService.processPayment()
    │
    │  What happens to Transaction A?
    ▼
InventoryService.reserveStock()
    │
    │  What happens to Transaction A?


Each method may declare its own transaction boundary.

Spring needs to decide one of these 4:
    Should the inner method join the existing transaction?
    Should it create a completely new transaction?
    Should it run without a transaction?
    Should it suspend the existing transaction temporarily?

That's transaction propagation.

## physical vs logical transactions
This distinction makes Spring transaction propagation much easier to understand.

Logical transaction
A logical transaction is the transaction boundary defined by a Spring method.
For example:
placeOrder()
    └── processPayment()
        └── reserveStock()
**Each method can have its own logical transaction scope.**

Physical transaction
A physical transaction is the actual database transaction associated with a database connection.
You can have:
3 logical transaction scopes
        │
        ▼
    1 physical DB transaction
This is exactly what commonly happens with REQUIRED.

# REQUIRED — the default
REQUIRED means:
    Join the current transaction if one exists; otherwise create one.

Suppose:
OrderService
    │
    ▼
placeOrder()
    │
    └── PaymentService.processPayment()

placeOrder() starts Transaction A.
Then processPayment() is called.
Because REQUIRED is the default:

placeOrder()
    │
    │ Transaction A
    ▼
processPayment()
    │
    │ joins Transaction A
    ▼
same physical transaction
So:

Logical scope 1 ─────┐
│
Logical scope 2 ─────┼──► Physical Transaction A
│
Logical scope 3 ─────┘

There isn't a second database transaction.


### Complete rest from https://chatgpt.com/g/g-p-6a6f21f852d481919ea5c3b2b74d39a3-springboot/c/6a95100a-a438-83ee-a595-adb2f264816c

Transaction Propagation and Isolation in Spring @Transactional
https://www.baeldung.com/spring-transactional-propagation-isolation

Transaction Propagation Mechanics in Spring Boot Explained
https://medium.com/@AlexanderObregon/transaction-propagation-mechanics-in-spring-boot-explained-e93ef2675faf

Transaction Propagation and Isolation in Spring @Transactional Annotation
https://www.geeksforgeeks.org/advance-java/transaction-propagation-and-isolation-in-spring-transactional-annotation/


