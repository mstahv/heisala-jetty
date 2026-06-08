# FFM API vs. JNI in `libcamera-4j` — Analysis

## First, a reality check on the current state

The docs are misleading. `package-info.java`, the `pom.xml` description, and the
Maven artifact description all claim the project uses *"the Foreign Function &
Memory API (Project Panama)"* against *"the libcamera **C** library."* **Neither
is true today.** The actual implementation is classic **JNI**:

- `src/main/native/libcamera4j.cpp` — 1084 lines of hand-written C++ with
  `extern "C"` `JNIEXPORT` functions
- ~50 `native` method declarations across `Camera`, `CameraManager`, `Request`,
  `FrameBuffer`, etc.
- A CMake + Docker (ARM64) cross-compile toolchain producing `libcamera4j.so`
- `NativeLoader.java` extracting the `.so` from the JAR at runtime

So the question is really: *should we replace this JNI shim with FFM?* And the
answer hinges on one fact that the docs get wrong.

## The decisive constraint: libcamera is C++, not C

**libcamera has no stable public C ABI.** Its API is modern C++ —
`std::shared_ptr<Camera>`, `std::unique_ptr<CameraConfiguration>`, `std::vector`,
signals/slots (`requestCompleted.connect(...)`), templated
`controls().set(controls::AfMode, …)`, RAII lifetimes, name-mangled symbols, etc.

FFM (`java.lang.foreign`, `Linker`, `jextract`) can **only** call **C ABI**
symbols — flat functions, C structs, primitive pointers. It cannot:

- call C++ name-mangled methods
- manage `shared_ptr`/`unique_ptr` lifetimes
- subscribe to libcamera's C++ signal/slot callbacks
- read templated `ControlList` values

That is *exactly* the work `libcamera4j.cpp` does today. Therefore:

> **FFM cannot bind libcamera directly. You would still need a native C shim that
> flattens the C++ API into plain C functions.** FFM does not eliminate native
> code here — it only moves the Java-side glue from the C++ file into Java.

This single fact invalidates the headline benefit usually cited for Panama ("no
native code, no C compiler, no toolchain"). It doesn't apply to a C++-only
library like libcamera.

## How big is the job?

**Medium-to-large, and larger than people expect** — because of the C++
constraint above. Three realistic paths:

| Path | What it means | Effort |
|------|---------------|--------|
| **A. Pure FFM, no native code** | Impossible — libcamera exposes no C ABI. ❌ | n/a |
| **B. Thin C shim + FFM** | Rewrite `libcamera4j.cpp` as a *C-ABI* shim (same ~50 functions, still C++ internally, but `extern "C"` flat signatures — which it largely already is), then call it from Java via FFM instead of JNI declarations. You still build/cross-compile/ship a `.so`. | **Moderate.** Rewrite ~50 Java binding stubs as FFM `MethodHandle`s + `MemorySegment` marshalling; keep most of the C++ but strip the `jni.h`/`JNIEnv*` dependency. Roughly 1–2 focused weeks. |
| **C. Keep JNI** | Status quo. It works. | Zero. |

The irony: your `extern "C"` functions are *already* very close to a C ABI. The
JNI-specific parts (the `JNIEnv* env` argument, `env->NewStringUTF`,
`env->SetByteArrayRegion`, `env->NewDoubleArray`, exception throwing via
`FindClass`/`ThrowNew`) are what FFM would let you delete from C++ and move to
Java. So path B is "de-JNI-ify the shim," not "remove the shim."

The fiddly parts to port:

- **String arrays** (`nativeGetCameraIds`) — build `char**` / read in Java
- **Primitive arrays** (`nativeGetColourCorrectionMatrix` → `double[9]`, black
  levels `int[4]`) — `MemorySegment` copies instead of `SetDoubleArrayRegion`
- **The frame buffer copy** (`nativeCopyBuffer`, mmap'd planes) — this is the one
  place FFM is genuinely *nicer* (see below)
- **The completion callback** — currently polled (`nativePollCompletedRequest`),
  which sidesteps upcalls; FFM `upcallStub` could later make it event-driven

## Advantages of switching to FFM

1. **No `JNIEnv` boilerplate.** All the `env->...` marshalling (strings, arrays,
   exceptions) moves to ordinary Java. The C side shrinks and gets simpler.
2. **Zero-copy frame access.** This is the strongest win for *this* project.
   Today `nativeCopyBuffer` does an `mmap` in C++ then `SetByteArrayRegion` into a
   Java `byte[]` — a full copy of every (potentially multi-MB) frame. With FFM you
   can wrap the mmap'd pointer as a `MemorySegment` and read pixels directly with
   **no copy**, which matters for a timelapse/streaming camera app.
3. **No `javah`/JNI signature coupling.** JNI breaks silently if a Java method
   signature and the mangled C name drift. FFM binds by symbol name +
   `FunctionDescriptor`, decoupled from Java method names.
4. **Better tooling path.** `jextract` can auto-generate FFM bindings from a C
   header — so if you write a clean `libcamera4j.h` for your shim, the Java side
   can be largely generated.
5. **Cleaner builds eventually.** You no longer need JDK headers (`jni.h`,
   `jni_md.h`) in the native build — the CMake `find_path(JNI_INCLUDE_DIR …)`
   dance goes away.
6. **Future-proof.** JNI still works but FFM is where the JDK is investing;
   `--enable-native-access` is already in your `pom.xml` surefire config, so the
   runtime model is in place.

## Disadvantages / costs

1. **You still ship a native `.so`.** The C++ shim, CMake, Docker ARM64
   cross-build, and `NativeLoader` extraction all remain. The biggest hoped-for
   win (eliminate native toolchain) **does not materialize** with libcamera.
2. **Rewrite risk for working code.** The current JNI code is debugged and runs on
   the Pi. Rewriting ~50 bindings is pure churn with no new features — classic
   "if it ain't broke."
3. **Manual memory management moves to Java.** `Arena`, `MemorySegment` lifetimes,
   confinement — get them wrong and you get JVM crashes just like bad JNI, but now
   in Java code. The handle-map pattern you use today (`g_cameras`, `g_requests`
   keyed by `jlong`) would stay, so lifetime bugs remain possible.
4. **Callbacks are harder, not easier.** libcamera's `requestCompleted` signal is
   C++. FFM upcalls require a *C* callback in the shim that then calls a Java
   `upcallStub`. Your current polling design avoids this entirely — switching to
   FFM doesn't fix it for free.
5. **Threading/`Arena` subtleties.** libcamera fires callbacks on its own threads;
   FFM upcalls from foreign threads need an attached/global arena and careful
   confinement. Easy to get subtly wrong.
6. **Java version floor.** FFM is stable as of JDK 22 (`java.lang.foreign`). You
   target JDK 25, so fine — but it locks you off older JREs that JNI tolerated
   (the README still says "Java 17+", which would no longer hold).

## Recommendation

For *this* project, **the pragmatic call is: keep JNI, but adopt one FFM idea
surgically.** Specifically:

- **Don't do a full rewrite.** Because libcamera is C++-only, FFM can't deliver
  its marquee benefit (toolchain-free binding) here. You'd spend 1–2 weeks
  rewriting working, debugged code for marginal architectural gain.
- **Do consider FFM for the hot path only:** replace `nativeCopyBuffer`'s
  mmap-then-`byte[]`-copy with a `MemorySegment` over the mmap'd frame, exposed to
  Java for **zero-copy** pixel access. That's the single change with real,
  measurable payoff (less GC pressure, faster captures) for a camera/timelapse
  workload — and it can coexist with the rest of the JNI bindings.
- **Fix the docs regardless.** `package-info.java` and the POM description
  currently claim Panama/C-library and should say JNI/C++ until/unless you
  migrate, or they'll keep misleading contributors.

If you *do* eventually want the full FFM migration (e.g., to make the Java side
`jextract`-generatable and drop the JDK-header build dependency), path **B** is
the route: keep your `extern "C"` shim, strip the `JNIEnv` usage, write a small
`libcamera4j.h`, and generate/handwrite the FFM layer. Just go in knowing the
`.so` and its cross-compile pipeline are *not* going away.
