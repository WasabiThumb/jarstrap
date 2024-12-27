package io.github.wasabithumb.jarstrap.packager.stage.impl;

import io.github.wasabithumb.jarstrap.packager.Packager;
import io.github.wasabithumb.jarstrap.packager.PackagerState;
import io.github.wasabithumb.jarstrap.packager.error.PackagerException;
import io.github.wasabithumb.jarstrap.packager.error.PackagerIOException;
import io.github.wasabithumb.jarstrap.packager.stage.PackagerStage;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PackagerVarsStage implements PackagerStage {

    private static final Pattern DECL_PATTERN = Pattern.compile("^static const (char|unsigned int|bool) ([A-Z_]+(?:\\[])?)\\s*=\\s*[^;]+;$");
    private static final String SYMBOL_START = "// CONFIG START";
    private static final String SYMBOL_END = "// CONFIG END";

    @Override
    public @NotNull String id() {
        return "vars";
    }

    @Override
    public void execute(@NotNull Packager packager, @NotNull PackagerState state) throws PackagerException {
        File main = new File(packager.getWorkingDir(), "main.c");
        File tmp = new File(packager.getWorkingDir(), "main.c.bak");
        try {
            Files.move(main.toPath(), tmp.toPath());
        } catch (IOException e) {
            throw new PackagerIOException("Failed to move main.c", e);
        }
        try {
            this.inject(packager, tmp, main);
        } catch (IOException e) {
            throw new PackagerIOException("Failed to inject variables into main.c", e);
        }
        try {
            Files.delete(tmp.toPath());
        } catch (IOException e) {
            throw new PackagerIOException("Failed to clean main.c.bak", e);
        }
    }

    private void inject(@NotNull Packager p, @NotNull File src, @NotNull File dest) throws IOException {
        try (Reader r = new InputStreamReader(new FileInputStream(src), StandardCharsets.UTF_8);
             Writer w = new OutputStreamWriter(new FileOutputStream(dest, false), StandardCharsets.UTF_8)
        ) {
            this.inject0(p, r, w);
        }
    }

    private void inject0(@NotNull Packager p, @NotNull Reader r, @NotNull Writer w) throws IOException {
        char[] buf = new char[512];
        int len = 0;
        int read;
        boolean inConfig = false;
        char c;

        while ((read = r.read()) != -1) {
            c = (char) read;
            if (c != '\n') {
                buf[len++] = c;
                continue;
            }
            boolean crlf = false;
            if (len != 0 && buf[len - 1] == '\r') {
                crlf = true;
                len--;
            }
            if (len == 0) {
                if (crlf) w.write('\r');
                w.write('\n');
                continue;
            }

            CharSequence line = CharBuffer.wrap(buf, 0, len);
            boolean end = false;
            if (inConfig) {
                end = CharSequence.compare(line, SYMBOL_END) == 0;
                if (end) {
                    w.write(buf, 0, len);
                } else {
                    w.write(this.transformDeclaration(p, line));
                }
            } else {
                if (CharSequence.compare(line, SYMBOL_START) == 0)
                    inConfig = true;
                w.write(buf, 0, len);
            }
            if (crlf) w.write('\r');
            w.write('\n');
            if (end) break;
            len = 0;
        }

        while ((read = r.read(buf)) != -1) {
            w.write(buf, 0, read);
        }
    }

    private @NotNull String transformDeclaration(@NotNull Packager p, @NotNull CharSequence decl) {
        Matcher m = DECL_PATTERN.matcher(decl);
        if (!m.matches()) return decl.toString();

        String type = m.group(1);
        String key = m.group(2);
        String value = switch (key) {
            case "APP_NAME[]" -> this.cQuote(p.getAppName());
            case "MIN_JAVA_VERSION" -> Integer.toString(p.getMinJavaVersion());
            case "PREFERRED_JAVA_VERSION" -> Integer.toString(p.getPreferredJavaVersion());
            case "INSTALL_PROMPT[]" -> this.cQuote(p.getInstallPrompt());
            case "LAUNCH_FLAGS[]" -> this.cQuote(p.getLaunchFlags());
            case "ATTRIBUTION" -> Boolean.toString(p.isAttributionEnabled());
            default -> throw new AssertionError("No rule to populate key \"" + key + "\"");
        };

        return "static const " + type + " " + key + " = " + value + ";";
    }

    private @NotNull String cQuote(@NotNull CharSequence input) {
        int len = input.length();
        StringBuilder ret = new StringBuilder(len + 2);
        char c;

        ret.append('"');
        for (int i=0; i < len; i++) {
            c = input.charAt(i);
            if (c == '\\' || c == '"')
                ret.append('\\');
            ret.append(c);
        }
        ret.append('"');

        return ret.toString();
    }

}
