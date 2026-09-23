package com.example.shading;

import java.util.concurrent.Callable;

// One simple name can only be imported once, so the v1 classes are imported
// and the v2 classes are written fully qualified below.
import v1.org.apache.commons.lang3.JavaVersion;
import v1.org.apache.commons.lang3.StringUtils;
import v1.org.apache.commons.lang3.SystemUtils;
import v1.org.apache.commons.lang3.math.NumberUtils;

/**
 * Calls commons-lang3 3.0 and 3.17.0 in the same JVM, with normal
 * compile-time-checked code: no reflection, no ClassLoaders.
 *
 * <p>This works because the shade plugin physically renamed the classes in
 * each wrapper jar at build time:
 * <pre>
 *   lang3-v1-shaded.jar: v1/org/apache/commons/lang3/StringUtils.class  (was 3.0)
 *   lang3-v2-shaded.jar: v2/org/apache/commons/lang3/StringUtils.class  (was 3.17.0)
 * </pre>
 * As far as the JVM is concerned these are simply two unrelated libraries.
 */
public class ShadingDemo {

    public static void main(String[] args) {
        section("Where each class came from");
        System.out.println("v1: " + StringUtils.class.getName());
        System.out.println("    loaded from " + location(StringUtils.class));
        System.out.println("v2: " + v2.org.apache.commons.lang3.StringUtils.class.getName());
        System.out.println("    loaded from " + location(v2.org.apache.commons.lang3.StringUtils.class));
        System.out.println("Same ClassLoader for both? "
                + (StringUtils.class.getClassLoader()
                   == v2.org.apache.commons.lang3.StringUtils.class.getClassLoader()));

        section("Same calls against both versions");
        compare("NumberUtils.createNumber(\"#FADE\")",
                () -> NumberUtils.createNumber("#FADE"),
                () -> v2.org.apache.commons.lang3.math.NumberUtils.createNumber("#FADE"));

        // 3.0 was written before Java 8 existed. It can't parse the running JVM's
        // version string, leaves an internal field null, and this call throws.
        compare("SystemUtils.isJavaVersionAtLeast(JAVA_1_7)",
                () -> SystemUtils.isJavaVersionAtLeast(JavaVersion.JAVA_1_7),
                () -> v2.org.apache.commons.lang3.SystemUtils.isJavaVersionAtLeast(
                        v2.org.apache.commons.lang3.JavaVersion.JAVA_1_7));

        compare("StringUtils.abbreviate(\"Hello, multi-version world\", 12)",
                () -> StringUtils.abbreviate("Hello, multi-version world", 12),
                () -> v2.org.apache.commons.lang3.StringUtils.abbreviate("Hello, multi-version world", 12));

        section("API that only exists in the newer version");
        // StringUtils.truncate was added in 3.5. The compiler checks each call
        // against the version it targets:
        System.out.println("v2 StringUtils.truncate(\"Hello, world\", 5) -> "
                + v2.org.apache.commons.lang3.StringUtils.truncate("Hello, world", 5));
        // Uncomment to get a COMPILE error; 3.0 has no truncate method:
        // StringUtils.truncate("Hello, world", 5);

        section("The two versions' types don't mix");
        // v1.JavaVersion and v2.JavaVersion are unrelated types, so this line
        // would not compile ("incompatible types"):
        //   JavaVersion oops = v2.org.apache.commons.lang3.JavaVersion.JAVA_1_7;
        // To move data between versions, convert through a neutral type:
        JavaVersion converted = JavaVersion.valueOf(v2.org.apache.commons.lang3.JavaVersion.JAVA_1_7.name());
        System.out.println("v2 JAVA_1_7 converted to v1 via name(): " + converted.getClass().getName() + "." + converted);
    }

    private static void compare(String call, Callable<Object> v1, Callable<Object> v2) {
        System.out.println(call);
        System.out.println("  3.0    -> " + run(v1));
        System.out.println("  3.17.0 -> " + run(v2));
    }

    static String run(Callable<Object> call) {
        try {
            return String.valueOf(call.call());
        } catch (Throwable t) {
            return "THREW " + t.getClass().getSimpleName() + ": " + t.getMessage();
        }
    }

    static String location(Class<?> c) {
        String path = c.getProtectionDomain().getCodeSource().getLocation().getPath();
        return path.substring(path.lastIndexOf('/', path.length() - 2) + 1);
    }

    static void section(String title) {
        System.out.println();
        System.out.println("=== " + title + " ===");
    }
}
