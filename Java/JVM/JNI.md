# JNI (Java Native Interface)

## What is JNI?

JNI references are special handles used when Java code interacts with native code (like C or C++).

Because the Java Virtual Machine (JVM) automatically manages memory via Garbage Collection (GC) while C/C++ requires manual memory management, they need a "bridge" to communicate. JNI references act as that bridge, telling the Java Garbage Collector whether a Java object is still being used by native C/C++ code so the GC doesn't accidentally delete it.

---

## The Core Problem They Solve

Imagine you pass a Java object to a C++ function. While the C++ function is running, a Java Garbage Collection cycle triggers. The GC scans the Java heap, sees no active Java variables pointing to that object, and deletes it. Suddenly, your C++ code tries to access that object, resulting in a catastrophic crash or memory corruption.

JNI references solve this by explicitly registering the object's usage with the JVM.

---

## Reference Types

| Reference Type                | How it's Created                                                                                                                                                                                                                     | Lifespan                                                                      | GC Behavior                                                                                                                                                                                                                                       | Cleanup / Usage                                                                                                                                                                                                                         |
|-------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Local Reference** (default) | Automatically created when you pass a Java object into a native method, or when a JNI function returns an object.                                                                                                                    | Valid only for the duration of that specific native method call.              | Prevents the Java object from being garbage collected while the native method is executing.                                                                                                                                                       | Automatically freed when the native method returns to Java. However, if you create a large number of them inside a loop in C++, you must free them manually using `DeleteLocalRef` to avoid running out of local reference table space. |
| **Global Reference**          | If you need a Java object to persist across multiple native method calls (for example, caching a Java object configuration in a C++ global variable), you must promote a local reference to a global reference using `NewGlobalRef`. | Valid until you explicitly destroy it.                                        | Completely blocks the Java object from being garbage collected.                                                                                                                                                                                   | Manual. If you forget to call `DeleteGlobalRef`, you will cause a permanent memory leak on the Java heap, because the GC will assume the object is always in use.                                                                       |
| **Weak Global Reference**     | Similar to global references, these can persist across multiple native method calls, but they use a weak pointer created via `NewWeakGlobalRef`.                                                                                     | Valid until explicitly freed, or until the GC reclaims the underlying object. | Does not prevent the object from being garbage collected — it can be collected on any GC cycle in which it isn't otherwise strongly reachable. This is not tied to memory pressure; that's `SoftReference` behavior, not weak reference behavior. | Before using a weak global reference in C++, native code must always check if the object is still alive using `IsSameObject(env, weakRef, NULL)` rather than comparing the raw pointer directly.                                        |

---

## Why Java Interacts With Native Code

**Scenario: Java code interacts with native code (like C or C++)**

While Java is powerful and highly portable, it operates inside a controlled virtual machine (the JVM). Java code interacts with native code (C or C++) in scenarios where you need to break out of the JVM's sandbox to achieve something Java cannot do natively or efficiently on its own.

Here are the primary scenarios where this interaction is necessary:

### 1. Accessing Platform-Specific or Hardware Features

Java is designed to be "Write Once, Run Anywhere," which means standard Java libraries only include features common to all operating systems. If you need to interact directly with OS-specific APIs or underlying hardware, you need native code.

- **Interacting with specialized hardware**: Talking to custom USB devices, medical equipment, or proprietary sensors that only provide C/C++ drivers.
- **Low-level OS features**: Accessing specific Windows Registry functions, system-level event hooks, or advanced Linux kernel features not exposed by the standard Java API.

### 2. Reusing Legacy C/C++ Libraries

Rewriting millions of lines of battle-tested code in Java is expensive, time-consuming, and risky. Native interaction allows Java applications to reuse existing software assets.

- **Enterprise Migration**: A bank migrating its core infrastructure to Java might keep its original C++ financial calculation engine intact and wrap it so Java can call it.
- **Open-Source Powerhouses**: Utilizing highly optimized open-source C/C++ libraries for cryptography (like OpenSSL), video/audio encoding (like FFmpeg), or database engines (like SQLite).

### 3. Performance-Critical Applications (Graphics & Physics)

The JVM introduces overhead via garbage collection, bounds checking, and bytecode execution. For applications that require predictable, raw execution speeds with microsecond latency, C or C++ is preferred for the heavy lifting.

- **Game Development**: While a game's UI or logic might be written in Java (like early Android games), the underlying 3D graphics engine (OpenGL/Vulkan) or physics simulation engine is often written in C++ for maximum frame rates.
- **Real-time Graphics processing**: Image processing, computer vision (e.g., OpenCV), and heavy matrix multiplication.

### 4. Building OS-Level Systems & Embedded Devices

In resource-constrained environments or system-level development, Java relies entirely on native code to function.

- **The Android OS Architecture**: The Android framework is written in Java, but it sits on top of a Linux kernel and a massive layer of native C/C++ libraries (handling everything from surface rendering to media playback and SSL).
- **Embedded IoT Systems**: Interacting with microcontrollers or low-resource devices where Java code acts as the high-level control logic, but C code handles the fast, low-level GPIO pin toggling.

---

## How It's Done: The Tooling

To achieve this, developers historically used **JNI** (Java Native Interface), which requires writing a C/C++ "bridge" file.

In modern Java, developers often use newer, simpler alternatives:

- **JNA (Java Native Access)**: Allows Java to call native libraries directly using dynamic proxies, eliminating the need to write any C code bridges.
- **Project Panama (Foreign Function & Memory API)**: Introduced in recent Java versions to completely replace JNI, offering a highly optimized, safe way to bind native libraries and access off-heap memory directly from Java.
