# Metaspace vs PermGen

**Explain the difference between Metaspace and PermGen, and why the change matters operationally**

PermGen (pre-Java 8) stored class metadata in a fixed-size region of the heap, causing frequent `OutOfMemoryError: PermGen space` in apps with heavy class loading (app servers, OSGi, frameworks generating proxies at runtime).

Metaspace moved this to native memory, growing dynamically by default.

**Operationally:** you no longer size PermGen manually, but you can still leak native memory if you don't cap Metaspace (`-XX:MaxMetaspaceSize`) in environments doing dynamic class generation — e.g., heavy CGLIB proxy creation in Spring apps with thousands of beans.
