Elasticsearch Global Ordinals
https://medium.com/driven-by-code/elasticsearch-global-ordinals-31df2806391f

Elasticsearch Global Ordinals, Eager Global Ordinals & High Cardinality Fields
https://opster.com/guides/elasticsearch/data-architecture/elasticsearch-global-ordinals-high-cardinality/
https://www.elastic.co/docs/reference/elasticsearch/mapping-reference/eager-global-ordinals

elasticsearch refresh time ordinal
In Elasticsearch, the "refresh time" refers to the interval at which newly indexed documents become visible and searchable. 
By default, Elasticsearch automatically refreshes indices every second, but only for indices that have received at least one search request in the last 30 seconds. This interval can be adjusted using the index.refresh_interval setting. 
"Ordinal" in the context of Elasticsearch, particularly "global ordinals," relates to an internal optimization strategy used for specific field types, especially keyword and text fields when performing aggregations. Global ordinals assign a unique, incremental number (an ordinal) to each unique term found within a field across the entire index. This allows Elasticsearch to represent terms more efficiently during aggregation operations, improving performance.
Connection between Refresh Time and Global Ordinals:
When an index is refreshed, and new documents are added or existing ones updated, the global ordinals for relevant fields might need to be rebuilt or updated. This is particularly true if eager_global_ordinals is enabled for a field. Eager global ordinals are built proactively during the refresh process, rather than lazily when an aggregation is first performed. While this can speed up subsequent aggregations, it can also increase the refresh time, as the rebuilding of global ordinals is a resource-intensive operation.
Therefore, while "refresh time" dictates when new data becomes searchable, and "global ordinals" are an internal optimization for certain field types, the two are linked in terms of performance. Frequent refreshes, especially with eager global ordinals, can impact the overall indexing and refresh performance of your Elasticsearch cluster.

Aggregating data for faster performance
https://www.elastic.co/docs/explore-analyze/machine-learning/anomaly-detection/ml-configuring-aggregation

elsticsearch text search performance optimization
To optimize
Elasticsearch for text search performance, you should focus on tuning your hardware, index mapping, and query execution. A combination of these strategies can dramatically improve the speed and efficiency of full-text queries. 
Hardware and cluster configuration

    Allocate sufficient RAM for the filesystem cache. Elasticsearch relies heavily on the OS filesystem cache to keep hot regions of the index in physical memory. A general rule of thumb is to allocate 50% of the available RAM to the JVM heap and leave the other 50% for the OS filesystem cache.
    Use faster hardware. SSDs typically offer better performance than spinning disks for Elasticsearch's I/O-intensive search operations. Faster CPUs and higher disk throughput are also beneficial for both indexing and searching.
    Right-size your shards. Improper shard sizing can create performance bottlenecks. Aim for a shard size of 20 to 40 GB. Too many small shards increase cluster management overhead, while overly large ones can slow down searches and recovery.
    Tune replicas for throughput. While replicas provide high availability, they also serve to increase search throughput by allowing queries to run in parallel. A well-configured replica count can distribute the search load across your cluster, but too many can negatively impact indexing performance.
    Disable swapping. System swapping can kill Elasticsearch performance by causing long garbage collection pauses. Disable swapping entirely on your nodes or use the mlockall setting to lock the JVM heap in memory. 

Indexing and mapping strategy

    Denormalize your documents. Avoid complex joins, especially nested or parent-child relations, as they significantly increase query time. Denormalizing your data at index time is often faster.
    Model data for search. Copy text from multiple fields that are often searched together into a single, combined field. This allows your query to target one field rather than many, improving performance.
    json

    PUT movies
    {
      "mappings": {
        "properties": {
          "name_and_plot": { "type": "text" },
          "name": { "type": "text", "copy_to": "name_and_plot" },
          "plot": { "type": "text", "copy_to": "name_and_plot" }
        }
      }
    }

    Use code with caution.

Use the correct field types. Reserve the text field type for full-text search with analyzed content. For exact-match identifiers, codes, or tags, use the keyword type. Searching keyword fields is much faster than searching text fields.
Use custom analyzers. Customize text analysis for your specific data, language, and search requirements. This can involve using different tokenizers, filters for stemming, or synonym handling to improve relevance and efficiency.
Pre-index data. If you have predictable queries, like range filters on certain values, you can pre-index the results. For example, you can calculate price ranges at index time to speed up aggregations. 

Query execution and caching

    Favor filters over queries for exclusion. If you don't need scoring for a clause (e.g., status: published), use the filter clause within a bool query. Filters are cached and are much faster than queries for excluding documents.
    Tune search queries with the Profile API. The Profile API provides a detailed breakdown of how long each component of a query takes to process. Use it to find and optimize the most expensive parts of your searches.
    Use special mapping options for specific queries.
        index_phrases: Speed up phrase queries ("quick brown fox") by indexing two-word shingles.
        index_prefixes: Speed up prefix queries ("quic*") by indexing prefixes of all terms.
    Use index sorting to speed up conjunctions. For use cases where you often query for the latest data, use index sorting to place the latest documents together on disk. This can speed up queries on recent documents.
    Force-merge read-only indices. For indices that are no longer receiving writes (like historical data), force-merging them into a single segment can reduce overhead and improve search performance.
    Leverage caching with preference and rounded dates.
        Set the preference parameter to a user or session ID to ensure requests from the same client hit the same shards, improving cache utilization.
        Use date-rounding ("now/m") in date-range queries to make them cacheable for multiple users during the same time window. 

Advanced and maintenance strategies

    Use different clusters for indexing and searching. For high-throughput applications with heavy indexing, consider using a separate "search" cluster. You can use cross-cluster replication to replicate data from the indexing cluster to the search cluster, so the two processes don't compete for resources.
    Monitor performance metrics. Continuously monitor key metrics like cluster health, query latency, indexing rate, CPU, memory, and I/O usage. Use this data to identify bottlenecks and guide your optimization efforts. 
    
    
Elasticsearch copy_to
https://medium.com/@andre.luiz1987/using-copy-to-parameter-elasticsearch-34a3622bca6e
https://www.elastic.co/docs/reference/elasticsearch/mapping-reference/copy-to

lexicographic
refers to something related to dictionaries, especially the process of writing or compiling them (lexicography). It is also used to describe a general method of ordering or comparing sequences based on the alphabetical order of elements, such as in mathematical and computer science contexts. 


Elasticsearch Cardinality – Low + High Cardinality Fields
https://opster.com/guides/elasticsearch/data-architecture/elasticsearch-cardinality/

Cardinality aggregation
https://www.elastic.co/docs/reference/aggregations/search-aggregations-metrics-cardinality-aggregation


Improving the performance of high-cardinality terms aggregations
https://www.elastic.co/blog/improving-the-performance-of-high-cardinality-terms-aggregations-in-elasticsearch
