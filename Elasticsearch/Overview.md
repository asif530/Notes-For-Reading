Elasticsearch is a distributed search engine where data is automatically partitioned and replicated across multiple nodes. 
Unlike a relational database, Elasticsearch is built from day one for distributed indexing and searching.
Elasticsearch is built on top of Apache Lucene, which is a high-performance, full-featured text search engine library written in Java. 
Lucene provides the core indexing and searching capabilities, while Elasticsearch adds distributed capabilities, RESTful APIs, and additional features 
like aggregations, analyzers, and more.

# Elasticsearch High-Level Architecture

                  Client
                     |
      +------------------------------+
      |     REST API (9200)          |
      +------------------------------+
                     |
              Coordinating Node
                     |
        +------------+------------+
        |                         |
Search Request           Index Request
      |                         |
Scatter Phase           Primary Shard
      |                         |
All Relevant Shards      Replica Shards
      |                         |
Gather Results             Replication
      |
Final Response

That's why a malformed query throws "Search Failed: All Shards Failed."

# Main Components

Cluster
    |
    +------ Nodes             (physical/logical topology)
    |
    +------ Indices           (logical data organization)
                |
                +------ Shards        (assigned to Nodes, not owned by them)
                            |
                            +------ Segments
                                        |
                                        +------ Documents
Let's understand each.

1. Cluster
A cluster is simply all Elasticsearch nodes working together.

Example
Cluster Name : production-cluster
  Node 1
  Node 2
  Node 3
  Node 4
  Node 5

The cluster provides 
    distributed storage
    distributed searching
    replication
    fault tolerance

Every cluster has
    Cluster UUID
    Cluster State
    Master Node
    Metadata
    Routing Table

2. Node
A node is simply one running Elasticsearch instance.

Example

Machine A             Machine B         Machine C
Elasticsearch         Elasticsearch     Elasticsearch

Each node stores some shards.
A node can have different responsibilities.

Node Types
 Master Eligible Node

Responsible for

cluster state

creating indices

deleting indices

shard allocation

node joining

node leaving

elections

It does not perform heavy searching.

Think of it as Kubernetes API Server.

Data Node

Stores

documents

shards

indices

Performs

indexing

searching

aggregation

This is where most CPU is consumed.

Coordinating Node

Every node can act as a coordinating node.

Its responsibilities

receive client request

forward request

merge results

send response

It stores nothing special.

Think of it as an API Gateway.

Ingest Node

Used before indexing.

Client

Document

↓

Ingest Pipeline

↓

Processors

↓

Indexed Document
Processors include

rename

remove

lowercase

geoip

attachment

script

set

date parsing

Machine Learning Node

Used for

anomaly detection

forecasting

NLP

Mostly used in Elastic commercial features.

3. Index

An index is similar to a database table.

shop_index

Document 1
Document 2
Document 3
Document 4
But internally it is much more complex.

4. Document

A document is one JSON object.

Example

{
"id":1,
"name":"iPhone",
"price":1200,
"brand":"Apple"
}
Every document has

_id
_source
_version
_seq_no
_primary_term
5. Shard

This is where Elasticsearch becomes distributed.

Suppose

100 GB Index
One machine cannot efficiently store or search it.

Split it.

Shard 1

Shard 2

Shard 3

Shard 4

Shard 5
Now

Node A

Shard 1
Shard 4

Node B

Shard 2

Node C

Shard 3
Shard 5
Every shard is an independent Lucene index.

Primary Shards

Every document belongs to exactly one primary shard.

Example

Primary 1

Primary 2

Primary 3
When inserting

Document

↓

Hash(_id)

↓

Shard Number
Formula

shard = hash(_id) % number_of_primary_shards
Therefore

Product 1 -> Shard 2

Product 2 -> Shard 1

Product 3 -> Shard 3
Replica Shards

Replica shards are copies of primary shards.

Example

Primary 1

Replica 1
Benefits

High availability

Read scalability

Disaster recovery

Example

Node A

Primary 1

Node B

Replica 1
Node A dies

↓

Replica becomes available for reads and can be promoted to primary by the master node.

Why Primary and Replica Cannot Live Together

Node A

Primary 1

Replica 1
If Node A dies

Both disappear.

No redundancy.

Therefore Elasticsearch always allocates them on different nodes whenever possible.

Inside a Shard

Many developers stop here.

Actually

Shard

↓

Lucene Index

↓

Segments

↓

Inverted Index

↓

Term Dictionary

↓

Posting Lists
The shard is actually Apache Lucene.

See [Lucene.md](./Lucene.md) for a deeper look at Lucene's internals and why it's high-performance.

Segment

Segments are immutable files.

Segment A

Segment B

Segment C
When indexing

Document

↓

Memory Buffer

↓

Refresh

↓

New Segment
No existing segment is modified.

Instead

Segment 1

Segment 2

Segment 3
Later

Merge

↓

Larger Segment
This is why Elasticsearch performs segment merges in the background.

Inverted Index

Instead of

Document

↓

Words
Lucene stores

Word

↓

Documents
Example

Documents

Doc1

Java Spring Boot

Doc2

Spring Elasticsearch

Doc3

Java Elasticsearch
Inverted index

Java

Doc1
Doc3

Spring

Doc1
Doc2

Elasticsearch

Doc2
Doc3
Searching becomes extremely fast.

Cluster State

Master node stores

Indices

Mappings

Settings

Nodes

Shard Locations

Aliases

Templates

Pipelines
Every node has a copy of the cluster state, but only the elected master can modify it and publish updates.

Indexing Flow

Client
↓
Coordinating Node
↓
Primary Shard
↓
Lucene Index
↓
Replica Shards
↓
Acknowledgement
Detailed

Client
↓
Node A
↓
Routing
↓
Primary Shard
↓
Write
↓
Replica
↓
Replica
↓
Success
Search Flow

Searching is distributed.

Client
↓
Coordinator
↓
Shard 1
Shard 2
Shard 3
↓
Partial Results
↓
Merge
↓
Return
This is called

Scatter phase

Gather phase

For example, if an index has 5 primary shards (and possibly replicas), the coordinating node sends the query to one copy of each shard, collects the partial results, merges and sorts them, then returns the final response.

Refresh

Unlike relational databases, documents are not immediately searchable.

Index Request
↓
Memory Buffer
↓
Refresh
↓
Searchable
Default refresh interval

1 second
Refresh creates new searchable segments without committing them to durable storage.

Flush

Flush is different.

Memory
↓
Commit
↓
Disk
↓
Transaction Log Cleared
Flush makes indexed operations durable and truncates the translog after a successful commit.

Translog

Writes first go to

Memory
+
Translog
If Elasticsearch crashes. Recovery happens from the translog.Kind of like Postgres wall log

Complete Architecture Diagram

                    Cluster
                        |
        -----------------------------------
        |               |                 |
     Node A          Node B           Node C
        |               |                 |
Primary 1       Primary 2                         Primary 3
Replica 3       Replica 1                          Replica 2
|                                  |                                    |
Shards          Shards           Shards
|               |                 |
Lucene          Lucene           Lucene
|               |                 |
Segments        Segments         Segments
|               |                 |
Inverted Index  Inverted Index   Inverted Index
|               |                 |
Documents       Documents        Documents
How this maps to the internals you've been working with

Since you've been building features around mappings, analyzers, autocomplete, KNN vectors, and ingest pipelines, it's useful to connect those concepts to the architecture:

Mapping defines how fields are indexed inside each shard's Lucene index.

Analyzer converts text into tokens before they're written into the inverted index.

Ingest pipelines run on ingest nodes (or any node with the ingest role) before the document is routed to its primary shard.

Dense vectors are stored within shards and searched locally; distributed KNN queries fan out to the relevant shards, and the coordinating node merges the nearest-neighbor results.

Aggregations execute independently on each shard, producing partial aggregation results that the coordinating node reduces into the final answer.

The architecture in one picture

REST Client
│
▼
Coordinating Node
│
├────────── Search ───────────────┐
│                                 │
▼                                 ▼
Primary/Replica Shards           Primary/Replica Shards
│                                 │
▼                                 ▼
Lucene Index                     Lucene Index
│                                 │
▼                                 ▼
Segments (immutable)
│
▼
Inverted Index + Stored Fields + Doc Values + Vectors
│
▼
Documents
This layered model—Cluster → (Nodes | Indices → Shards, with Shards assigned to Nodes) → Lucene Index → Segment → Inverted Index → Document—is the fundamental mental model for understanding nearly every Elasticsearch feature, from routing and replication to search performance, aggregations, and vector search.