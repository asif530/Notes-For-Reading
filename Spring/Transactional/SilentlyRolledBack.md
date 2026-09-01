Transaction silently rolled back because it has been marked as rollback-only                                                                                                     
What scenarios might trigger this ?

=> This message comes from Spring's transaction management. Here are the common scenarios that trigger it:

1. Nested @Transactional with inner exception caught by outer

@Transactional                                                                                                                                                                    
public void outer() {                                     
try {
inner(); // throws, marks tx as rollback-only
} catch (Exception e) {                                                                                                                                                       
// you swallowed the exception, but tx is already doomed
}                                                                                                                                                                             
// Spring tries to commit → "silently rolled back"    
}

@Transactional                                                                                                                                                                    
public void inner() {                                     
throw new RuntimeException(); // marks the shared tx as rollback-only
}                                                                                                                                                                                 
This is the most common cause. Both methods share the same transaction (default PROPAGATION_REQUIRED), the inner exception marks it rollback-only, and the outer catches it
thinking it recovered.

2. Calling TransactionAspectSupport.currentTransactionStatus().setRollbackOnly() manually

Someone explicitly marked the transaction for rollback but didn't throw an exception to signal it.

3. @Transactional(noRollbackFor=...) mismatch

A checked exception was thrown that Spring would normally not roll back on, but something else already marked the transaction rollback-only.

4. Hibernate/JPA flushing an invalid state mid-transaction

A constraint violation or optimistic locking failure during a flush (not at commit time) marks the session/transaction as invalid.

5. Async or cross-thread access to the same EntityManager

Sharing an EntityManager or Session across threads can corrupt transaction state.
                                                            
---                                                                                                                                                                               
6. RuntimeException being caught and swallowed somewhere inside a @Transactional method? That's the culprit 90% of the time.

To find it, look for try/catch blocks inside @Transactional methods in your service layer — especially around calls to other @Transactional methods.
                                                                                                                                                                                    
======================================================================================================================                                         

What does it mean "marked as rollback only"? What is its relation with @Transactional annotation ?

=> "marked as rollback-only" means

Spring manages transactions through a TransactionStatus object. This object has a boolean flag: rollback-only.

When this flag is set to true, it means:

▎ "No matter what happens next, this transaction cannot commit. It will be rolled back."

Once set, it cannot be unset. The transaction is doomed.
                                                            
---                                                                                                                                                                               
How @Transactional relates

@Transactional is a proxy-based mechanism. Spring wraps your method in a proxy that:

[Spring Proxy]
→ begins/joins transaction                                                                                                                                                    
→ calls your method                                   
→ on exception: sets rollback-only flag (or rolls back immediately)                                                                                                           
→ on normal return: tries to commit

The critical piece is propagation. By default (PROPAGATION_REQUIRED), all nested @Transactional methods share the same transaction.

outerMethod()  ──┐                                                                                                                                                                
│  same Transaction object                                                                                                                                       
innerMethod()  ──┘

So when the inner method's proxy catches a RuntimeException, it sets rollback-only = true on the shared transaction object — even if the outer method never sees the exception.
                                                                                                                                                                                    
---                                                                                                                                                                               
The lifecycle visualized

outer() starts         → tx created, rollback-only = false
inner() throws       → Spring proxy sets rollback-only = true                                                                                                                   
outer() catches it   → outer thinks it's fine           
outer() returns        → Spring proxy tries to commit                                                                                                                             
→ sees rollback-only = true                                                                                                                                
→ "Transaction silently rolled back"
                                                                                                                                                                                    
---                                                       
The flag is set by Spring when:

┌──────────────────────────────────────────────────────┬─────────────────────┐
│                       Trigger                        │    What happens     │                                                                                                    
├──────────────────────────────────────────────────────┼─────────────────────┤                                                                                                    
│ RuntimeException thrown out of @Transactional method │ proxy sets flag     │
├──────────────────────────────────────────────────────┼─────────────────────┤                                                                                                    
│ Error thrown                                         │ proxy sets flag     │                                                                                                    
├──────────────────────────────────────────────────────┼─────────────────────┤
│ Checked exception if rollbackFor is configured       │ proxy sets flag     │                                                                                                    
├──────────────────────────────────────────────────────┼─────────────────────┤                                                                                                    
│ Manual setRollbackOnly() call                        │ flag set directly   │
├──────────────────────────────────────────────────────┼─────────────────────┤                                                                                                    
│ JPA/Hibernate marks session invalid                  │ flag set internally │
└──────────────────────────────────────────────────────┴─────────────────────┘
                                                            
---
Bottom line: @Transactional proxies intercept exceptions and mark the transaction as rollback-only. The problem arises when you catch that exception outside the proxy boundary
but inside the same transaction — Spring's flag persists even though your code thinks the error was handled. 
