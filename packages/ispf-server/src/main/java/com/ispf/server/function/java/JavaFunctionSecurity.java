package com.ispf.server.function.java;

import java.util.List;
import java.util.regex.Pattern;

final class JavaFunctionSecurity {

    private static final List<Pattern> FORBIDDEN = List.of(
            Pattern.compile("\\bRuntime\\s*\\.\\s*getRuntime\\b"),
            Pattern.compile("\\bRuntime\\s*\\.\\s*exec\\b"),
            Pattern.compile("\\bProcessBuilder\\b"),
            Pattern.compile("\\bProcessHandle\\b"),
            Pattern.compile("\\bSystem\\s*\\.\\s*exit\\b"),
            Pattern.compile("\\bSystem\\s*\\.\\s*load(?:Library)?\\b"),
            Pattern.compile("\\bClass\\s*\\.\\s*forName\\b"),
            Pattern.compile("\\bMethodHandles\\b"),
            Pattern.compile("\\bVarHandle\\b"),
            Pattern.compile("\\bsun\\.misc\\.Unsafe\\b"),
            Pattern.compile("\\bjdk\\.internal\\b"),
            Pattern.compile("\\bjavax\\.script\\b"),
            Pattern.compile("\\borg\\.springframework\\b"),
            Pattern.compile("\\bjava\\.lang\\.reflect\\b"),
            Pattern.compile("\\bjava\\.lang\\.invoke\\b"),
            Pattern.compile("\\bjava\\.lang\\.instrument\\b"),
            Pattern.compile("\\bjava\\.lang\\.Process\\b"),
            Pattern.compile("\\bjava\\.net\\.(?!InetAddress\\b)"),
            Pattern.compile("\\bjava\\.nio\\.file\\b"),
            Pattern.compile("\\bjava\\.io\\.File\\b"),
            Pattern.compile("\\bURLClassLoader\\b"),
            Pattern.compile("\\bClassLoader\\s*\\.\\s*defineClass\\b"),
            Pattern.compile("\\bThread\\s*\\.\\s*(?:start|stop|suspend|resume)\\b"),
            Pattern.compile("\\bnew\\s+Thread\\b"),
            Pattern.compile("\\bNative\\b"),
            Pattern.compile("\\bJNI\\b"),
            Pattern.compile("\\bjavax\\.naming\\b"),
            Pattern.compile("\\bcom\\.sun\\.jndi\\b"),
            Pattern.compile("\\bjava\\.awt\\.Robot\\b"),
            Pattern.compile("\\bDesktop\\s*\\.\\s*getDesktop\\b")
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
