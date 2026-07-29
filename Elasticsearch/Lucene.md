# Apache Lucene — Overview

Lucene is a Java library (not a standalone server) that provides full-text indexing and search. Elasticsearch, Solr, and others wrap it and add distribution, REST APIs, and clustering on top — but the actual indexing/searching work happens inside Lucene, one shard at a time (Shard → Lucene Index → Segments → Inverted Index).

## Core structures

- **Inverted index** — instead of doc → words, it stores word → list of docs (postings list). This is what makes "find all docs containing X" fast instead of scanning every document.
- **Segments** — a Lucene index is a collection of immutable segment files. Writes never modify existing segments; they create new ones. Segments periodically merge into larger ones in the background.
- **Term dictionary** — sorted list of unique terms per segment, stored as an FST (finite state transducer) so term lookup is close to O(1)-ish and highly compressed in memory.
- **Postings lists** — per term, the list of doc IDs (and positions/frequencies) is stored sorted and delta-encoded, so it compresses well and supports fast intersection (AND queries).
- **Doc values** — a column-oriented (forward) index alongside the inverted index, used for sorting, aggregations, and scripting without needing to load full documents.
- **Stored fields** — the original document content, compressed separately, only decompressed when you need to return `_source`/actual field values, not during the search itself.

## Why it's high-performance

1. **Immutability of segments** — no locks needed for reads while writes happen; readers see a consistent point-in-time view. This also makes segments trivially cacheable by the OS page cache.
2. **Append-only writes** — new data becomes new segments (sequential I/O), avoiding expensive in-place updates/random I/O.
3. **Compression** — term dictionaries (FST), postings (delta + variable-byte encoding), and doc values are all compressed, so more of the index fits in memory/OS cache, reducing disk I/O.
4. **Skip lists on postings** — allow skipping over blocks of doc IDs that can't match, instead of scanning every posting, which speeds up conjunctions (AND) and phrase queries.
5. **Memory-mapped files** — Lucene relies on the OS page cache via `mmap` rather than managing its own buffer cache, letting the OS handle efficient caching of hot segments.
6. **Segment merging** — background merges consolidate small segments into fewer, larger ones, reducing the number of files a query has to fan out to and reclaiming space from deleted docs.
7. **Concurrent segment search** — each segment can be searched independently and in parallel, then results merged — this is exactly what lets Elasticsearch parallelize search across a shard's segments, and across shards' concurrent segment search feature in newer versions.
8. **Efficient scoring (BM25)** — precomputed statistics (term frequency, document frequency, field length norms) let relevance scoring be calculated cheaply per match rather than requiring full-document analysis at query time.

The short version: Lucene is fast because it turns "search" into an *intersection of pre-sorted, compressed, cacheable lists* rather than a scan — and it does so with an immutable, append-only, highly parallelizable storage model.