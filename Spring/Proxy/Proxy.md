# Why spring works via proxy ?
Spring uses proxies to implement caching (and other annotation-driven features like @Transactional) because of how it handles cross-cutting concerns through aspect-oriented programming (AOP), rather than by modifying your actual code.
Why proxy: 
when you annotate a method with @Cacheable, you haven't changed what that method does internally, you've just declared metadata saying "check the cache before running this." 
Spring needs some way to intercept calls to that method so it can insert this cache-checking logic without you having to hand-write it inside every method. 
Since Java doesn't let you rewrite method bytecode at runtime, Spring instead creates a proxy object that wraps the real bean. Callers interact with this proxy instead of the original object, and the proxy's job is to intercept the method call, 
run any applicable logic (like checking Redis via the CacheInterceptor), and then either return a cached result or delegate to the real method if nothing is cached yet.
This approach has a couple of practical implications worth knowing: it only works on Spring-managed beans (so calling a @Cacheable method from within the same class, bypassing the bean reference, skips the proxy entirely and the caching logic won't trigger), 
and depending on whether Spring uses JDK dynamic proxies or CGLIB proxies, there can be constraints like needing an interface or a non-final class. This proxy-based interception is essentially what lets Spring keep your business logic clean, 
the caching behavior lives entirely outside your method body, attached declaratively through the annotation.