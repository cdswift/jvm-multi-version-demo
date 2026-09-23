# JVM multi-version demo

Two ways to load **two versions of the same library** into one JVM and call
each one independently. The test subject is Apache Commons Lang, versions
**3.0** (2011) and **3.17.0** (2024). Both versions put their classes in the
same package, `org.apache.commons.lang3`.

| | Maven Shade relocation | Isolated ClassLoaders |
|---|---|---|
| When the versions are separated | Build time | Runtime |
| How | Classes are renamed: `org.apache.commons.lang3` → `v1.org...` / `v2.org...` | Original names, each version in its own `URLClassLoader` |
| Calling code | Normal Java, compile-time type checked | Through a shared interface (or reflection) |
| Wrong method or missing API | **Compile** error | **Runtime** `NoSuchMethodException` / `AbstractMethodError` |
| Choose or swap versions at runtime | No | Yes (plugins, hot reload) |
| Native (JNI) libraries | Breaks (JNI symbol names include the package) | Works, but only one loader per native lib |
| Heavy reflection / `Class.forName("...")` strings | May break | Works |

## Requirements

- JDK 17+
- Maven 3.9+

## Build and run

```bash
mvn package

java -jar shading/shading-demo/target/shading-demo.jar
java -jar classloader/classloader-demo/target/classloader-demo.jar
```

Use `mvn package`, not `mvn compile`. The shaded jars only exist after the
`package` phase, and the shading demo compiles against them (details below).

## Layout

```
pom.xml                          parent: module list, plugin versions, the two lang3 versions
shading/
  lang3-v1-shaded/               no code, just a pom: repackages lang3 3.0    as v1.org.apache.commons.lang3.*
  lang3-v2-shaded/               no code, just a pom: repackages lang3 3.17.0 as v2.org.apache.commons.lang3.*
  shading-demo/                  imports and calls both versions directly
classloader/
  probe-api/                     TextProbe: the interface shared between host and plugins
  probe-lang3-v1/                Lang3Probe implemented against lang3 3.0
  probe-lang3-v2/                Lang3Probe implemented against lang3 3.17.0 (same class name!)
  classloader-demo/              the host: one URLClassLoader per version
```

---

## Approach 1: Maven Shade relocation

### How it works

The shade plugin rewrites the bytecode of every class in the library, moving
it to a new package. It also updates every reference *inside* the library:
field and method descriptors, constant-pool class references, and string
constants that look like class names. Afterwards the JVM sees two unrelated
libraries:

```
lang3-v1-shaded.jar   v1/org/apache/commons/lang3/StringUtils.class
lang3-v2-shaded.jar   v2/org/apache/commons/lang3/StringUtils.class
```

Everything is loaded by the single application ClassLoader. Nothing special
happens at runtime.

### Why the wrapper modules are separate

Shading runs in the `package` phase, which comes after `compile`. Code in the
same module as the shading could never refer to `v1.org.apache...`, because
those classes don't exist yet when `javac` runs. So each version gets its own
source-less wrapper module that Maven builds first, and `shading-demo` depends
on the wrappers like ordinary libraries.

The important settings in [`shading/lang3-v1-shaded/pom.xml`](shading/lang3-v1-shaded/pom.xml):

- `<relocation>`: `org.apache.commons.lang3` → `v1.org.apache.commons.lang3`.
- `<optional>true</optional>` on the original dependency. Without it, the
  un-relocated `org.apache.commons.lang3` would also leak onto the consumer's
  classpath.
- `shadedArtifactAttached=false`. The shaded jar *replaces* the module's
  (empty) main jar, so dependents resolve to it.
- The `module-info.class` filter. lang3 3.17 ships a JPMS descriptor naming
  the original packages, and after relocation it would be wrong.
- `ServicesResourceTransformer`, which rewrites `META-INF/services` entries to
  the new names. lang3 has none, but most real libraries do.

### Using it

[`ShadingDemo.java`](shading/shading-demo/src/main/java/com/example/shading/ShadingDemo.java):

```java
import v1.org.apache.commons.lang3.math.NumberUtils;

NumberUtils.createNumber("#FADE");                               // 3.0
v2.org.apache.commons.lang3.math.NumberUtils.createNumber("#FADE"); // 3.17.0

v2.org.apache.commons.lang3.StringUtils.truncate("Hello, world", 5); // OK
// StringUtils.truncate(...)  <- compile error: 3.0 has no truncate()
```

A simple class name can only be imported once per file, so one version is
imported and the other is written fully qualified.

### Gotchas

- **IDE confusion.** IntelliJ and Eclipse may resolve the wrapper modules to
  their empty sources instead of the shaded jar, so they show errors even
  though `mvn package` succeeds. The fix is to `mvn install` the wrappers and
  build them separately, or to tell the IDE to ignore the wrapper modules.
- **Transitive dependencies.** If both versions depend on *different versions
  of another library*, include and relocate that library in each wrapper too.
- **Types don't mix.** `v1...JavaVersion` and `v2...JavaVersion` are unrelated
  types. To move data between versions, convert through JDK types (see the end
  of the demo).
- **Dynamic class names.** Names assembled at runtime (`"org.apache." + x`)
  can't be rewritten.

---

## Approach 2: Isolated ClassLoaders

### How it works

A class's runtime identity is **(fully qualified name, defining ClassLoader)**.
Two loaders can each define `org.apache.commons.lang3.StringUtils`, and the JVM
treats the results as unrelated classes, each with its own statics and
bytecode.

```
               bootstrap / platform loaders (JDK)
                             |
              application loader: classloader-demo.jar, probe-api.jar
                 /                                   \
  URLClassLoader "lang3-v1"                 URLClassLoader "lang3-v2"
    probe-lang3-v1.jar                        probe-lang3-v2.jar
    commons-lang3-3.0.jar                     commons-lang3-3.17.0.jar
```

The two rules that make this work:

1. **The library is not on the application classpath.** ClassLoaders delegate
   to their parent first. If lang3 were on the app classpath, both children
   would get the parent's single copy. The `classloader-demo` pom keeps the
   version-specific jars in `target/plugins/v1` and `target/plugins/v2`
   instead of `target/lib`.
2. **The shared interface *is* on the application classpath.** `TextProbe`
   lives in `probe-api`, which the app loader loads exactly once. Both
   plugins' `Lang3Probe` classes implement *that same* `TextProbe`, so the
   host can call them as ordinary Java objects:

```java
URLClassLoader v1 = new URLClassLoader("lang3-v1", jarsIn("plugins/v1"), appLoader);
TextProbe probe = ServiceLoader.load(TextProbe.class, v1).findFirst().orElseThrow();
probe.createNumber("#FADE");   // runs against lang3 3.0
```

Only JDK types (`String`) cross the `TextProbe` boundary. A method returning
a lang3 type would be unusable, because the host can't see lang3 and each
plugin has its own copy of it.

### What the demo shows

- `org.apache.commons.lang3.StringUtils` loaded twice: same name, `==` is false.
- `Class.forName(...)` from the app loader fails, which proves the library
  isn't on the main classpath.
- Calls through `TextProbe` (type-safe) and through raw reflection (no shared
  interface needed, but a method missing from 3.0 only fails at runtime).
- The classic `ClassCastException: Lang3Probe cannot be cast to Lang3Probe`
  when an object from one loader is cast to the other loader's class.

### Gotchas

- **Thread context ClassLoader.** Libraries that call
  `Thread.currentThread().getContextClassLoader()` (logging, JAXB, JDBC,
  many DI frameworks) look in the app loader and miss the plugin's classes.
  Set the context loader to the plugin's loader around such calls.
- **Native libraries.** A given `.so`/`.dll` can be bound to only one
  ClassLoader at a time.
- **Memory leaks.** A loader, and every class it loaded, can only be garbage
  collected once nothing references any of them. A leftover `ThreadLocal`,
  a running thread, or a registered JDBC driver pins the whole version in
  Metaspace. Close the `URLClassLoader` when you're done with it.
- **Build order trick.** `classloader-demo` declares the plugin modules as
  `test`-scoped dependencies with every transitive dependency excluded. This
  exists only so Maven builds them first; they never reach the demo's
  classpath.

---

## Which one should I use?

- **The versions are known when you build, and the library is plain Java.**
  Use shading. You get normal code and compile-time checking.
- **Versions are picked or swapped at runtime, the library uses JNI, or it
  does a lot of reflection by class name.** Use ClassLoaders.
- **JPMS modules.** The ClassLoader approach has a module-system version:
  `ModuleLayer.defineModulesWithOneLoader`, one layer per version.
