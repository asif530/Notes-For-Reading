### difference between checked and unchecked exceptions from an API design standpoint

Checked exceptions are a compiler-enforced contract: the caller *must* handle or declare them, which makes sense for recoverable conditions the caller 
can reasonably act on (e.g., `IOException` — retry, fallback).

Unchecked exceptions signal programming errors or conditions the immediate caller can't meaningfully recover from (e.g., `IllegalStateException`). 

Checked exceptions don't compose well with functional interfaces and streams, which is why most modern APIs (Spring, reactive libraries) favor unchecked exceptions
and push recovery logic to a higher layer via `@ControllerAdvice` or centralized error handling rather than forcing try/catch at every call site.

