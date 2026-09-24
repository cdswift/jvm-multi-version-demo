package com.example.classloader;

import com.example.probe.TextProbe;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.ServiceLoader;
import java.util.stream.Stream;

/**
 * Loads commons-lang3 3.0 and 3.17.0 into one JVM under their ORIGINAL
 * package names, each in its own {@link URLClassLoader}.
 *
 * <p>The JVM identifies a class by (fully qualified name, defining ClassLoader),
 * not by name alone. So {@code org.apache.commons.lang3.StringUtils} from
 * loader A and the one from loader B are two distinct, unrelated classes.
 *
 * <pre>
 *            bootstrap / platform loaders (JDK)
 *                          |
 *               application loader:  classloader-demo.jar, probe-api.jar
 *                  /                              \
 *   URLClassLoader "lang3-v1"            URLClassLoader "lang3-v2"
 *     probe-lang3-v1.jar                   probe-lang3-v2.jar
 *     commons-lang3-3.0.jar                commons-lang3-3.17.0.jar
 * </pre>
 *
 * Both child loaders delegate to the application loader first (normal
 * parent-first delegation). That is safe here because the application
 * loader has no commons-lang3 classes. The request falls through to the
 * child, which loads its own copy. The shared {@code TextProbe} interface,
 * on the other hand, IS found in the parent, so both plugins implement the
 * same interface. That's what lets this class call them without reflection.
 *
 * <p>Every result is printed through {@link #show}, which prints the source
 * line and expression that produced it.
 */
public class ClassLoaderDemo {

    public static void main(String[] args) throws Exception {
        Path pluginsDir = args.length > 0 ? Path.of(args[0]) : defaultPluginsDir();

        try (URLClassLoader v1 = loaderFor("lang3-v1", pluginsDir.resolve("v1"));
             URLClassLoader v2 = loaderFor("lang3-v2", pluginsDir.resolve("v2"))) {

            classIdentity(v1, v2);
            viaSharedInterface(v1, v2);
            viaReflection(v1, v2);
            typesDoNotMix(v1, v2);
        }
    }

    /** Same name, different Class objects. */
    static void classIdentity(ClassLoader v1, ClassLoader v2) {
        section("Class identity = name + ClassLoader");
        String name = "org.apache.commons.lang3.StringUtils";

        Class<?> c1 = show("Class<?> c1 = v1.loadClass(name)", () -> v1.loadClass(name));
        Class<?> c2 = show("Class<?> c2 = v2.loadClass(name)", () -> v2.loadClass(name));
        show("c1.getName().equals(c2.getName())", () -> c1.getName().equals(c2.getName()));
        show("c1 == c2", () -> c1 == c2);

        // The app's own loader can't see commons-lang3 at all:
        show("Class.forName(name)  // app loader", () -> Class.forName(name));
    }

    /** The clean way: plugins implement an interface both sides share. */
    static void viaSharedInterface(ClassLoader v1, ClassLoader v2) {
        section("Calling each version through the shared TextProbe interface");
        TextProbe p1 = show("TextProbe p1 = ServiceLoader.load(TextProbe.class, v1).findFirst().orElseThrow()",
                () -> ServiceLoader.load(TextProbe.class, v1).findFirst().orElseThrow());
        TextProbe p2 = show("TextProbe p2 = ServiceLoader.load(TextProbe.class, v2).findFirst().orElseThrow()",
                () -> ServiceLoader.load(TextProbe.class, v2).findFirst().orElseThrow());
        show("p1.getClass() == p2.getClass()", () -> p1.getClass() == p2.getClass());

        show("p1.libraryVersion()", p1::libraryVersion);
        show("p2.libraryVersion()", p2::libraryVersion);

        show("p1.createNumber(\"#FADE\")", () -> p1.createNumber("#FADE"));
        show("p2.createNumber(\"#FADE\")", () -> p2.createNumber("#FADE"));

        show("p1.isJavaAtLeast17()", p1::isJavaAtLeast17);
        show("p2.isJavaAtLeast17()", p2::isJavaAtLeast17);

        show("p1.abbreviate(\"Hello, multi-version world\", 12)", () -> p1.abbreviate("Hello, multi-version world", 12));
        show("p2.abbreviate(\"Hello, multi-version world\", 12)", () -> p2.abbreviate("Hello, multi-version world", 12));
    }

    /** The no-shared-interface way: works, but every call is stringly typed. */
    static void viaReflection(ClassLoader v1, ClassLoader v2) throws Exception {
        section("Calling each version directly via reflection (no shared interface)");
        Class<?> stringUtilsV1 = v1.loadClass("org.apache.commons.lang3.StringUtils");
        Class<?> stringUtilsV2 = v2.loadClass("org.apache.commons.lang3.StringUtils");

        show("stringUtilsV1.getMethod(\"capitalize\", String.class).invoke(null, \"reflection\")",
                () -> stringUtilsV1.getMethod("capitalize", String.class).invoke(null, "reflection"));
        show("stringUtilsV2.getMethod(\"capitalize\", String.class).invoke(null, \"reflection\")",
                () -> stringUtilsV2.getMethod("capitalize", String.class).invoke(null, "reflection"));

        // truncate() was added in 3.5. Via reflection, calling it on 3.0 fails at
        // RUNTIME here, where the shading approach would fail at compile time:
        show("stringUtilsV1.getMethod(\"truncate\", String.class, int.class).invoke(null, \"Hello, world\", 5)",
                () -> stringUtilsV1.getMethod("truncate", String.class, int.class).invoke(null, "Hello, world", 5));
        show("stringUtilsV2.getMethod(\"truncate\", String.class, int.class).invoke(null, \"Hello, world\", 5)",
                () -> stringUtilsV2.getMethod("truncate", String.class, int.class).invoke(null, "Hello, world", 5));
    }

    /** The classic error: "X cannot be cast to X". */
    static void typesDoNotMix(ClassLoader v1, ClassLoader v2) throws Exception {
        section("Objects from one version can't be used as the other's type");
        Class<?> probeV1 = v1.loadClass("com.example.probe.impl.Lang3Probe");
        Class<?> probeV2 = v2.loadClass("com.example.probe.impl.Lang3Probe");
        Object fromV1 = probeV1.getDeclaredConstructor().newInstance();

        show("fromV1.getClass()", fromV1::getClass);
        show("probeV2", () -> probeV2);
        show("probeV2.cast(fromV1)", () -> probeV2.cast(fromV1));
        show("fromV1 instanceof TextProbe  // shared interface", () -> fromV1 instanceof TextProbe);
    }

    // ---------------------------------------------------------------------

    /** Like Supplier, but may throw, so failures can be shown too. */
    @FunctionalInterface
    interface Code<T> {
        T run() throws Exception;
    }

    /** Numbering for the output: [section.step], e.g. [2.3]. */
    static int sectionNo = 0;
    static int stepNo = 0;

    /**
     * Prints a numbered block with the calling line ({@code ClassLoaderDemo.java:NN})
     * and {@code source}, then runs {@code code} and prints its result or the
     * exception it threw. Returns the result, or null if it threw.
     */
    static <T> T show(String source, Code<T> code) {
        String where = StackWalker.getInstance()
                .walk(frames -> frames.skip(1).findFirst())
                .map(f -> f.getFileName() + ":" + f.getLineNumber())
                .orElse("?");
        System.out.println();
        System.out.println("[" + sectionNo + "." + (++stepNo) + "] " + where);
        System.out.println("    code:      " + source);
        try {
            T result = code.run();
            System.out.println("    result:    " + format(result));
            return result;
        } catch (Exception e) {
            Throwable t = e instanceof InvocationTargetException ite ? ite.getCause() : e;
            System.out.println("    threw:     " + t.getClass().getName() + ": " + t.getMessage());
            printPluginFrames(t);
            return null;
        }
    }

    /**
     * If {@code t} came from inside a plugin, prints where the library threw it
     * and which plugin line called the library. Stack frames include the
     * ClassLoader name, e.g. {@code lang3-v1//org.apache.commons.lang3...}.
     */
    static void printPluginFrames(Throwable t) {
        StackTraceElement[] stack = t.getStackTrace();
        for (StackTraceElement frame : stack) {
            if (frame.getClassName().startsWith("com.example.probe.impl.")) {
                System.out.println("    thrown at: " + stack[0]);
                System.out.println("    called by: " + frame);
                return;
            }
        }
    }

    static String format(Object value) {
        if (value instanceof String s) {
            return '"' + s + '"';
        }
        if (value instanceof Class<?> c) {
            return "class " + c.getName() + " " + origin(c);
        }
        if (value == null || value instanceof Boolean || value instanceof Number) {
            return String.valueOf(value);
        }
        return "instance of " + value.getClass().getName() + " " + origin(value.getClass());
    }

    /** Where a class came from, e.g. {@code [loader 'lang3-v1', commons-lang3-3.0.jar]}. */
    static String origin(Class<?> c) {
        ClassLoader loader = c.getClassLoader();
        String loaderName = loader == null ? "bootstrap" : loader.getName();
        CodeSource src = c.getProtectionDomain().getCodeSource();
        return src == null
                ? "[loader '" + loaderName + "']"
                : "[loader '" + loaderName + "', " + jarOf(c) + "]";
    }

    /**
     * A fresh loader over every jar in {@code dir}. The parent is the app loader
     * so the plugins can see {@code TextProbe}. Loaders are named (Java 9+),
     * which makes ClassCastException messages much easier to read.
     */
    static URLClassLoader loaderFor(String name, Path dir) throws IOException {
        URL[] jars;
        try (Stream<Path> files = Files.list(dir)) {
            jars = files.filter(p -> p.toString().endsWith(".jar"))
                    .sorted()
                    .map(ClassLoaderDemo::toUrl)
                    .toArray(URL[]::new);
        }
        if (jars.length == 0) {
            throw new IllegalStateException("No jars in " + dir + " (did you run `mvn package`?)");
        }
        return new URLClassLoader(name, jars, ClassLoaderDemo.class.getClassLoader());
    }

    static Path defaultPluginsDir() throws URISyntaxException {
        // target/classloader-demo.jar -> target/plugins
        Path self = Path.of(ClassLoaderDemo.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        return self.getParent().resolve("plugins");
    }

    static URL toUrl(Path p) {
        try {
            return p.toUri().toURL();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static String jarOf(Class<?> c) {
        String path = c.getProtectionDomain().getCodeSource().getLocation().getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }

    static void section(String title) {
        sectionNo++;
        stepNo = 0;
        System.out.println();
        System.out.println();
        System.out.println("=".repeat(90));
        System.out.println(sectionNo + ". " + title);
        System.out.println("=".repeat(90));
    }
}
