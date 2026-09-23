package com.example.classloader;

import com.example.probe.TextProbe;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
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
    static void classIdentity(ClassLoader v1, ClassLoader v2) throws Exception {
        section("Class identity = name + ClassLoader");
        String name = "org.apache.commons.lang3.StringUtils";

        Class<?> c1 = v1.loadClass(name);
        Class<?> c2 = v2.loadClass(name);

        System.out.println("v1: " + c1.getName() + " loaded by '" + c1.getClassLoader().getName()
                + "' from " + jarOf(c1));
        System.out.println("v2: " + c2.getName() + " loaded by '" + c2.getClassLoader().getName()
                + "' from " + jarOf(c2));
        System.out.println("same name?  " + c1.getName().equals(c2.getName()));
        System.out.println("same class? " + (c1 == c2));

        // The app's own loader can't see commons-lang3 at all:
        try {
            Class.forName(name);
            System.out.println("app loader: found it (unexpected!)");
        } catch (ClassNotFoundException e) {
            System.out.println("app loader: ClassNotFoundException, not on the main classpath (as intended)");
        }
    }

    /** The clean way: plugins implement an interface both sides share. */
    static void viaSharedInterface(ClassLoader v1, ClassLoader v2) {
        section("Calling each version through the shared TextProbe interface");
        TextProbe p1 = ServiceLoader.load(TextProbe.class, v1).findFirst().orElseThrow();
        TextProbe p2 = ServiceLoader.load(TextProbe.class, v2).findFirst().orElseThrow();

        System.out.println("plugin classes: " + p1.getClass().getName() + " vs " + p2.getClass().getName()
                + " (same name, different class: " + (p1.getClass() != p2.getClass()) + ")");
        System.out.println();

        String v1Label = p1.libraryVersion();
        String v2Label = p2.libraryVersion();
        compare("NumberUtils.createNumber(\"#FADE\")", v1Label, p1.createNumber("#FADE"),
                v2Label, p2.createNumber("#FADE"));
        compare("SystemUtils.isJavaVersionAtLeast(JAVA_1_7)", v1Label, p1.isJavaAtLeast17(),
                v2Label, p2.isJavaAtLeast17());
        compare("StringUtils.abbreviate(\"Hello, multi-version world\", 12)",
                v1Label, p1.abbreviate("Hello, multi-version world", 12),
                v2Label, p2.abbreviate("Hello, multi-version world", 12));
    }

    /** The no-shared-interface way: works, but every call is stringly typed. */
    static void viaReflection(ClassLoader v1, ClassLoader v2) throws Exception {
        section("Calling each version directly via reflection (no shared interface)");
        for (ClassLoader loader : new ClassLoader[] {v1, v2}) {
            Class<?> stringUtils = loader.loadClass("org.apache.commons.lang3.StringUtils");
            Object result = stringUtils.getMethod("capitalize", String.class).invoke(null, "reflection");
            System.out.printf("  %-8s StringUtils.capitalize(\"reflection\") -> %s%n", loader.getName(), result);

            // A method that only exists in the newer version fails at RUNTIME here,
            // where the shading approach would have failed at compile time:
            try {
                Object truncated = stringUtils.getMethod("truncate", String.class, int.class)
                        .invoke(null, "Hello, world", 5);
                System.out.printf("  %-8s StringUtils.truncate(\"Hello, world\", 5) -> %s%n", loader.getName(), truncated);
            } catch (NoSuchMethodException e) {
                System.out.printf("  %-8s StringUtils.truncate(...) -> NoSuchMethodException (added in 3.5)%n", loader.getName());
            }
        }
    }

    /** The classic error: "X cannot be cast to X". */
    static void typesDoNotMix(ClassLoader v1, ClassLoader v2) throws Exception {
        section("Objects from one version can't be used as the other's type");
        Class<?> probeV1 = v1.loadClass("com.example.probe.impl.Lang3Probe");
        Class<?> probeV2 = v2.loadClass("com.example.probe.impl.Lang3Probe");
        Object instanceFromV1 = probeV1.getDeclaredConstructor().newInstance();
        try {
            probeV2.cast(instanceFromV1);
        } catch (ClassCastException e) {
            System.out.println("ClassCastException: " + e.getMessage());
            System.out.println("  instance's class was defined by '" + probeV1.getClassLoader().getName()
                    + "', target class by '" + probeV2.getClassLoader().getName() + "'");
        }
        System.out.println("...but as the shared interface it's fine: "
                + (instanceFromV1 instanceof TextProbe));
    }

    // ---------------------------------------------------------------------

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

    static void compare(String call, String v1Label, String v1Result, String v2Label, String v2Result) {
        System.out.println(call);
        System.out.printf("  %-6s -> %s%n", v1Label, v1Result);
        System.out.printf("  %-6s -> %s%n", v2Label, v2Result);
    }

    static void section(String title) {
        System.out.println();
        System.out.println("=== " + title + " ===");
    }
}
