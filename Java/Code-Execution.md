# How a `ServletInputStream.read()` Method Gets Called Long After Its Constructor Finished

This is one of the most confusing concepts when learning Java web applications because it feels like **a method created inside a constructor somehow executes itself later**. In reality, nothing magical happens. It is simply **object references, delegation, lazy execution, and polymorphism** working together.

---

# The Core Idea

When you create an object, **only the constructor executes immediately**.

Any methods belonging to that object **do not execute automatically**.

They execute only when **some other code that holds a reference to that object calls them later.**

Think of it like building a television.

* The constructor builds the television.
* It does **not** start playing a movie.
* Hours later someone presses the remote.
* The TV starts playing.

The TV constructor is long finished.
The TV object still exists.

Exactly the same thing happens with a `ServletInputStream`.

---

# Step 1 — The Filter Creates the Wrapper

Suppose the filter executes

```java
chain.doFilter(
    new GzipDecompressingRequestWrapper(request),
    response
);
```

At this moment Java performs

```
new GzipDecompressingRequestWrapper(...)
```

which executes the constructor.

Inside the constructor something like this happens

```java
gzipStream = new GZIPInputStream(request.getInputStream());

decompressedStream = new ServletInputStream() {

    @Override
    public int read() {
        return gzipStream.read();
    }

};
```

Notice something important.

The constructor **does not call**

```java
read();
```

Instead it creates an object that *contains* a `read()` method.

After the constructor finishes, memory looks like

```
GzipDecompressingRequestWrapper
│
├── gzipStream
│
└── decompressedStream
      │
      └── read()
```

Nothing has been read yet.

No decompression has happened.

The wrapper simply owns an object that knows **how** to read.

---

# Step 2 — The Wrapper Is Passed Down the Filter Chain

The filter calls

```java
chain.doFilter(wrapper, response);
```

Notice what is being passed.

Not bytes.

Not JSON.

Not decompressed data.

Just an object reference.

```
Filter A

     │
     ▼

wrapper object
```

The next filter receives exactly the same object.

```
Filter B

request parameter
      │
      ▼

same wrapper object
```

No copying happens.

The object simply keeps moving through the application.

---

# Step 3 — Another Wrapper Wraps It

Many Spring applications use

```
ContentCachingRequestWrapper
```

Internally this does something conceptually like

```java
this.request = wrapper;
```

Memory now looks like

```
ContentCachingRequestWrapper
           │
           ▼
GzipDecompressingRequestWrapper
           │
           ▼
decompressedStream
```

Again—

No one has called `read()`.

No bytes have been consumed.

---

# Step 4 — DispatcherServlet Receives the Request

Eventually all filters finish.

The request reaches Spring MVC.

```
DispatcherServlet
```

Suppose the controller is

```java
@PostMapping
public void echo(
    @RequestBody Map<String,Object> body
)
```

Spring now needs to build the `body`.

But where does it get the JSON?

It has to read the HTTP request body.

---

# Step 5 — Jackson Needs Bytes

Spring uses

```
MappingJackson2HttpMessageConverter
```

Jackson cannot parse JSON until it gets bytes.

So it asks

```java
request.getInputStream()
```

Notice something.

The request object it currently has is

```
ContentCachingRequestWrapper
```

not your wrapper.

---

# Step 6 — Delegation Starts

`ContentCachingRequestWrapper` doesn't actually own the HTTP request.

It wraps another request.

So it delegates.

```
ContentCachingRequestWrapper
          │
          ▼
getInputStream()

calls

wrappedRequest.getInputStream()
```

The wrapped request is

```
GzipDecompressingRequestWrapper
```

So your wrapper's

```java
getInputStream()
```

executes.

It simply returns

```java
return decompressedStream;
```

That object was created much earlier in the constructor.

Nothing new is created.

It returns the exact same object.

---

# Step 7 — Jackson Finally Holds the Stream

Now Jackson has

```
ServletInputStream
```

which is actually

```
decompressedStream
```

Memory looks like

```
Jackson
   │
   ▼
decompressedStream
```

Jackson now calls

```java
read()
```

or

```java
read(byte[])
```

This is the first time any reading occurs.

---

# Step 8 — Dynamic Dispatch Chooses the Correct Method

Although Jackson only knows

```java
ServletInputStream
```

the actual object is

```
Anonymous subclass of ServletInputStream
```

Java always executes methods based on the object's **actual runtime type**, not the variable's declared type.

This is called **dynamic dispatch** (runtime polymorphism).

So

```java
stream.read();
```

actually executes

```java
@Override
public int read() {
    return gzipStream.read();
}
```

This is your code.

---

# Why Doesn't Jackson Call ServletInputStream.read() Instead?

Suppose

```java
ServletInputStream stream = decompressedStream;
```

Even though the variable type is

```
ServletInputStream
```

the object is actually

```
MyAnonymousServletInputStream
```

Java therefore executes

```
MyAnonymousServletInputStream.read()
```

not

```
ServletInputStream.read()
```

Exactly like

```java
Animal a = new Dog();

a.makeSound();
```

Java executes

```
Dog.makeSound()
```

not

```
Animal.makeSound()
```

because the runtime object is a Dog.

---

# The Entire Lifecycle

```
Incoming HTTP Request
        │
        ▼
EncodingFilter

new GzipDecompressingRequestWrapper()

        │
        │ Constructor executes
        │
        ▼

Creates

gzipStream

and

decompressedStream

(read() NOT executed)

        │
        ▼

chain.doFilter(wrapper)

        │
        ▼

Other Filters

        │
        ▼

ContentCachingRequestWrapper

        │
        ▼

DispatcherServlet

        │
        ▼

Jackson Converter

        │
        ▼

request.getInputStream()

        │
        ▼

delegates

        │
        ▼

GzipDecompressingRequestWrapper.getInputStream()

        │
        ▼

returns

decompressedStream

(created much earlier)

        │
        ▼

Jackson calls

read()

        │
        ▼

Your overridden read()

        │
        ▼

gzipStream.read()

        │
        ▼

Compressed bytes

↓

Decompressed bytes

↓

Jackson parses JSON

↓

Controller receives

@RequestBody
```

---

# Important Design Pattern Being Used

This entire mechanism relies on the **Decorator Pattern**.

Each wrapper adds functionality while forwarding operations to the wrapped object.

```
ContentCachingRequestWrapper
        │
        ▼
GzipDecompressingRequestWrapper
        │
        ▼
Original HttpServletRequest
```

Every wrapper can:

* intercept a method,
* modify behavior,
* or simply delegate to the next wrapper.

Your wrapper intercepts `getInputStream()` and returns a stream that transparently decompresses GZIP data before anyone reads it.

---

# Lazy Execution

Nothing is decompressed during construction.

The wrapper merely prepares the objects required to perform decompression later.

Actual decompression happens only when some downstream component—such as Jackson—requests bytes from the stream. This is known as **lazy execution** or **on-demand processing**.

Benefits include:

* avoiding unnecessary work if the body is never read,
* reducing startup overhead,
* processing data only when required,
* streaming data instead of loading everything into memory at once.

---

# Why the Object Is Still Available Later

A common question is:

> "The constructor already finished. Why does the object still exist?"

Because constructors initialize objects—they do **not** determine their lifetime.

An object remains in memory as long as at least one live reference points to it.

In this request flow:

```
ContentCachingRequestWrapper
        │
        ▼
GzipDecompressingRequestWrapper
        │
        ▼
decompressedStream
```

Each object references the next one. Since the request wrappers remain alive for the duration of the HTTP request, the `decompressedStream` also remains alive. When Jackson later calls `getInputStream()`, it receives that same stream object that was created earlier.

---

# Key Takeaways

* Constructors execute **once** to initialize an object.
* Constructors can create objects that expose methods for future use.
* Those methods are **not** executed during construction.
* Objects remain alive while references to them exist.
* Filters pass object **references**, not copies of objects or request bodies.
* Request wrappers form a **Decorator Pattern**, where each wrapper delegates to the next.
* `getInputStream()` is resolved through this wrapper chain until the innermost implementation returns the actual stream.
* Jackson receives the same `ServletInputStream` instance created earlier and invokes `read()` only when it needs request-body bytes.
* Java's **dynamic dispatch** ensures that the overridden `read()` method on your anonymous subclass is executed, even though Jackson only knows it as a `ServletInputStream`.
* GZIP decompression happens **lazily**, only when bytes are actually read, not when the wrapper is constructed.
