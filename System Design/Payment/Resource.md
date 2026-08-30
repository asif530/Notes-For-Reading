https://codefarm0.medium.com/system-design-interview-what-happens-if-kafka-receives-an-event-but-the-producer-never-gets-the-6f3456538eae
https://medium.com/javarevisited/system-design-interview-how-would-you-prevent-a-payment-from-being-processed-twice-724aded39642
https://levelup.gitconnected.com/idempotency-in-payment-systems-how-revolut-prevents-double-charges-04e31977d990
https://archive.is/ZXjmk


Dot net
https://www.youtube.com/watch?v=RwQVRXEs370
https://medium.com/@guizordan/getting-started-with-net-core-and-c-on-linux-a-step-by-step-guide-manjaro-edition-50fccfccbf02
https://dotnettutorials.net/course/asp-net-web-api/

This is a UML sequence diagram with 5 participants (lifelines): Payment Service, Routing Service, Redis Cache, Provider Adapter, Payment Provider. Time flows top to       
bottom; arrows show who calls whom and in which direction (solid arrow = request/call, dashed arrow = response/return).

1. Kick-off                                                                                                                                                                
   Payment Service → Routing Service: Route Request {paymentMethod, amount, merchantId} — the caller asking "which provider should handle this payment?"
2. Get provider performance data (with caching)                                                                                                                            
   Routing Service → Redis Cache: Get Provider Metrics {providerId}                                                                                                           
   Inside an alt block (two mutually exclusive branches):
- Cache Hit: Redis Cache ⇢ Routing Service returns Provider Metrics {latency, successRate, cost} immediately.
- Cache Miss: Routing Service does a self-call (Calculate Metrics from historical data — arrow loops back to itself, meaning internal computation, no other participant    
  involved), then Routing Service → Redis Cache: Cache Metrics, writing the freshly computed values back for next time.

3. Decide which provider to use (all self-calls on Routing Service)                                                                                                        
   These three steps are internal logic — the arrow leaves and returns to Routing Service itself, no other lifeline is touched:
- Filter Providers (by health, merchant rules)
- Calculate Scores (cost, latency, success rate)
- Select Best Provider

4. Return the decision                                                                                                                                                     
   Routing Service ⇢ Payment Service: dashed return arrow, Selected Provider {providerId, score} — routing decision handed back to the caller.
5. Execute the payment                                                                                                                                                     
   Payment Service → Provider Adapter: Process Payment {providerId, transaction} — note this call skips Routing Service entirely; Payment Service talks directly to the       
   adapter using the providerId it was just given.                                                                                                                            
   Provider Adapter → Payment Provider: Payment Request                                                                                                                       
   Payment Provider ⇢ Provider Adapter: dashed Payment Response back.                                                                                                         
   Provider Adapter ⇢ Payment Service: dashed Transaction Result back — bubbling the outcome up to the original caller.

6. Feed the outcome back into the metrics loop                                                                                                                             
   Payment Service → Routing Service: Update Provider Metrics {providerId, latency, success} — so future routing decisions learn from this transaction's real-world result.   
   Routing Service → Redis Cache: Update Metrics Cache — persists that updated metric, closing the loop back to step 2's cache.

Overall shape: it's a request→decide→execute→feedback cycle. Routing Service is the "brain" (cache lookups + three self-computed scoring steps), Payment Service is the    
orchestrator that calls Routing Service for a decision, then bypasses it to execute payment directly via Provider Adapter, and finally reports results back to Routing     
Service so its metrics/cache stay fresh for next time.    