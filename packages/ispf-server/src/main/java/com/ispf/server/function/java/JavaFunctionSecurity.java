package com.ispf.server.function.java;

import java.util.List;
import java.util.regex.Pattern;

final class JavaFunctionSecurity {

    private static final List<Pattern> FORBIDDEN = List.of(
            Pattern.compile("\\bRuntime\\s*\\.\\s*getRuntime\\b"),
            Pattern.compile("\\bProcessBuilder\\b"),
            Pattern.compile("\\bSystem\\s*\\.\\s*exit\\b"),
            Pattern.compile("\\bClass\\s*\\.\\s*forName\\b"),
            Pattern.compile("\\bMethodHandles\\b"),
            Pattern.compile("\\bsun\\.misc\\.Unsafe\\b"),
            Pattern.compile("\\bjavax\\.script\\b"),
            Pattern.compile("\\borg\\.springframework\\b"),
            Pattern.compile("\\bjava\\.lang\\.reflect\\b"),
            Pattern.compile("\\bjava\\.net\\.(?!InetAddress\\b)"),
            Pattern.compile("\\bjava\\.nio\\.file\\b"),
            Pattern.compile("\\bjava\\.io\\.File\\b")
    );

    private JavaFunctionSecurity() {
    }

    static void validate(String source) {
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("Java function source is empty");
        }
        for (Pattern pattern : FORBIDDEN) {
            if (pattern.matcher(source).find()) {
                throw new IllegalArgumentException(
                        "Java function source uses a forbidden construct: " + pattern.pattern()
                );
            }
        }
    }
}
