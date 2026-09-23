package com.example.probe.impl;

import com.example.probe.TextProbe;
import org.apache.commons.lang3.JavaVersion;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.lang3.math.NumberUtils;

/**
 * commons-lang3 3.17.0 implementation of {@link TextProbe}.
 *
 * <p>The other plugin module has a class with this exact same fully qualified
 * name, {@code com.example.probe.impl.Lang3Probe}. Both are loaded into one
 * JVM at the same time, each by its own ClassLoader.
 */
public class Lang3Probe implements TextProbe {

    @Override
    public String libraryVersion() {
        // Read from the commons-lang3 jar's manifest (Implementation-Version)
        return StringUtils.class.getPackage().getImplementationVersion();
    }

    @Override
    public String createNumber(String input) {
        try {
            return String.valueOf(NumberUtils.createNumber(input));
        } catch (RuntimeException e) {
            return "THREW " + e.getClass().getSimpleName() + ": " + e.getMessage();
        }
    }

    @Override
    public String isJavaAtLeast17() {
        try {
            return String.valueOf(SystemUtils.isJavaVersionAtLeast(JavaVersion.JAVA_1_7));
        } catch (RuntimeException e) {
            return "THREW " + e.getClass().getSimpleName() + ": " + e.getMessage();
        }
    }

    @Override
    public String abbreviate(String text, int maxWidth) {
        return StringUtils.abbreviate(text, maxWidth);
    }
}
