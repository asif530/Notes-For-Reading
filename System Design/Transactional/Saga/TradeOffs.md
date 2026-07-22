  ┌────────────────────────────────┬────────────────────────────────────────────────┬────────────────────────────────────────────────┐   
  │                                │                 Orchestration                  │                  Choreography                  │   
  ├────────────────────────────────┼────────────────────────────────────────────────┼────────────────────────────────────────────────┤   
  │ Visibility into saga state     │ One query, one table                           │ Reconstruct from distributed event history     │   
  ├────────────────────────────────┼────────────────────────────────────────────────┼────────────────────────────────────────────────┤   
  │ Adding/changing a step         │ Change the orchestrator                        │ Every affected service needs updating          │   
  ├────────────────────────────────┼────────────────────────────────────────────────┼────────────────────────────────────────────────┤   
  │ Failure handling /             │ Centralized decision logic                     │ Scattered across participants                  │   
  │ compensation                   │                                                │                                                │   
  ├────────────────────────────────┼────────────────────────────────────────────────┼────────────────────────────────────────────────┤   
  │ Coupling                       │ Services coupled to the orchestrator           │ Services coupled to each other's event         │   
  │                                │                                                │ contracts                                      │   
  ├────────────────────────────────┼────────────────────────────────────────────────┼────────────────────────────────────────────────┤   
  │ Best fit                       │ Complex, multi-step workflows needing          │ Simple, few-step flows valuing max             │   
  │                                │ visibility                                     │ independence                                   │   
  └────────────────────────────────┴────────────────────────────────────────────────┴────────────────────────────────────────────────┘   
  
See this project for Orchestration Saga pattern
https://github.com/asif530/SB-Saga-Orchestration.git                                                                                                                                       

Here the reasoning (with the same comparison table) is written up in the README under Why orchestration, not choreography 
was chosen for this project.

# When to use Choreography
Choreography shines when the steps are more like independent reactions to a fact than a tightly coordinated transaction with branching
compensation logic. Some good-fit scenarios:

1. Fan-out side effects after a single event, each independent of the others                                                           
   E.g. OrderPlaced triggers: Inventory decrements stock, Shipping schedules a shipment, Email sends a confirmation, Analytics logs it,   
   Loyalty awards points. None of these need to know about each other or wait on each other — they just each react to the same event and  
   do their own thing. If one fails, it doesn't need to unwind the others in a coordinated way; it just publishes its own failure event   
   and whoever cares (if anyone) reacts to that.

2. Growing the number of consumers without touching existing code                                                                      
   Choreography's big advantage is that adding a new subscriber to an existing event stream requires zero changes to the publisher or any
   other consumer — a new team can add "send this order to our partner API when OrderPlaced fires" just by subscribing. With              
   orchestration, every new step means editing the orchestrator. This matters a lot in large orgs where many independent teams keep adding
   consumers over time.

3. Domain events that are facts, not commands, with no rollback needed                                                                 
   Things like UserRegistered → welcome email, CRM lead creation, trial subscription setup, analytics tracking. These aren't really a     
   "transaction" that can fail and need compensating — they're just independent projections of one fact into several systems. There's no  
   saga to coordinate, just pub/sub.  
4. Short, mostly-linear chains (2–3 steps, little branching)                                                                           
   If the failure/compensation logic barely branches, the overhead of standing up a dedicated orchestrator component often isn't worth it
   — a couple of event listeners each doing their own compensating action is simpler to build and reason about than a whole state-machine
   service.

5. Cross-service data sync / cache & index invalidation                                                                                
   ProductUpdated → search index re-indexes, cache invalidates, recommendation engine recomputes. Pure "react and forget," no             
   transactional semantics at all.

The general heuristic: as the number of steps and the number of branching failure paths grows, choreography's implicit distributed     
state (reconstructed from scattered event history) gets hard to reason about fast — that's exactly why this project's saga (3–4 steps,
real compensation branching between payment/inventory) used orchestration instead. If you swapped this same saga to choreography,      
Payment Service would need to know to listen for InventoryReservationFailed and decide to refund itself, Inventory would need to know  
about payment states, etc. — the coordination logic doesn't disappear, it just gets smeared across every participant instead of living
in one place. 