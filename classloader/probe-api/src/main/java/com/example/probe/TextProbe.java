package com.example.probe;

/**
 * Contract between the host application and each version-specific plugin.
 *
 * <p>Only JDK types ({@code String}) cross this boundary. Returning a
 * commons-lang3 type here would not work: the host can't see those classes,
 * and each plugin has its own copy of them.
 */
public interface TextProbe {

    /** Version of commons-lang3 this plugin was loaded with. */
    String libraryVersion();

    /** Result of {@code NumberUtils.createNumber(input)}. Library exceptions propagate. */
    String createNumber(String input);

    /** Result of {@code SystemUtils.isJavaVersionAtLeast(JAVA_1_7)}. Library exceptions propagate. */
    String isJavaAtLeast17();

    /** Result of {@code StringUtils.abbreviate(text, maxWidth)}. */
    String abbreviate(String text, int maxWidth);
}
