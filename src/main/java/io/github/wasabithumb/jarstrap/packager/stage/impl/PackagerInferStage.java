package io.github.wasabithumb.jarstrap.packager.stage.impl;

import io.github.wasabithumb.jarstrap.packager.Packager;
import io.github.wasabithumb.jarstrap.packager.PackagerState;
import io.github.wasabithumb.jarstrap.packager.error.PackagerException;
import io.github.wasabithumb.jarstrap.packager.error.PackagerIOException;
import io.github.wasabithumb.jarstrap.packager.stage.PackagerStage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class PackagerInferStage implements PackagerStage {

    private static final String MAIN_CLASS_KEY = "Main-Class";
    private static final byte[] CLASS_MAGIC = new byte[] {
            (byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE
    };

    @Override
    public @NotNull String id() {
        return "infer";
    }

    @Override
    public void execute(@NotNull Packager packager, @NotNull PackagerState state) throws PackagerException {
        final File file = new File(new File(packager.getWorkingDir(), "archive"), "archive.jar");
        int major;
        try {
            major = this.readMajor(file);
        } catch (IOException e) {
            throw new PackagerIOException(e);
        }

        if (major < 49)
            throw new PackagerException("Main class of JAR has unsupported class file major version " + major);

        int minJavaVersion = major - 44;
        int curMinJavaVersion = packager.getMinJavaVersion();
        if (minJavaVersion <= curMinJavaVersion) return;

        int preferredJavaVersion = packager.getPreferredJavaVersion();
        if (minJavaVersion > preferredJavaVersion)
            throw new PackagerException("Main class of JAR file was compiled against Java " + minJavaVersion + ", but preferred Java version is " + preferredJavaVersion);
        packager.setMinJavaVersion(minJavaVersion);
    }

    private int readMajor(@NotNull File file) throws IOException, PackagerException {
        final CharSequence mainClass = this.readMainClass(file);
        if (mainClass == null)
            throw new PackagerException("JAR has no Main-Class set in its manifest (is not runnable)");

        final StringBuilder entryName = new StringBuilder(mainClass.length() + 6);
        char c;
        for (int i=0; i < mainClass.length(); i++) {
            c = mainClass.charAt(i);
            if (c == '.') c = '/';
            entryName.append(c);
        }
        entryName.append(".class");

        try (ZipFile zf = new ZipFile(file)) {
            ZipEntry ze = zf.getEntry(entryName.toString());
            if (ze == null)
                throw new PackagerException("Main class of JAR (" + mainClass + ") is not contained in the file");

            try (InputStream is = zf.getInputStream(ze); DataInputStream dis = new DataInputStream(is)) {
                byte[] header = dis.readNBytes(CLASS_MAGIC.length);
                if (!Arrays.equals(header, CLASS_MAGIC))
                    throw new PackagerException("Main class of JAR (" + mainClass + ") has corrupted header");

                dis.skipNBytes(Short.BYTES);
                return dis.readShort();
            }
        }
    }

    private @Nullable CharSequence readMainClass(@NotNull File file) throws IOException, PackagerException {
        CharSequence mainClass = null;
        try (ZipFile zf = new ZipFile(file)) {
            ZipEntry ze = zf.getEntry("META-INF/MANIFEST.MF");
            if (ze == null) throw new PackagerException("Source JAR has no manifest");

            try (InputStream is = zf.getInputStream(ze);
                 Reader r = new InputStreamReader(is, StandardCharsets.UTF_8);
                 BufferedReader br = new BufferedReader(r)
            ) {
                String line;
                while ((line = br.readLine()) != null) {
                    int whereSep = line.indexOf(':');
                    if (whereSep == -1) continue;
                    if (whereSep != MAIN_CLASS_KEY.length()) continue;

                    int match = 2;
                    char c;
                    boolean shouldBeUpper;
                    for (int i=0; i < MAIN_CLASS_KEY.length(); i++) {
                        c = line.charAt(i);
                        shouldBeUpper = (i == 0) || (i == 5);
                        if (Character.isUpperCase(c)) {
                            if (!shouldBeUpper) {
                                match = 1;
                                c = Character.toLowerCase(c);
                            }
                        } else if (shouldBeUpper) {
                            match = 1;
                            c = Character.toUpperCase(c);
                        }
                        if (c != MAIN_CLASS_KEY.charAt(i)) {
                            match = 0;
                            break;
                        }
                    }
                    if (match == 0) continue;

                    int whereContent = whereSep + 1;
                    while (whereContent < line.length()) {
                        if (Character.isWhitespace(line.charAt(whereContent))) {
                            whereContent++;
                        } else {
                            break;
                        }
                    }
                    mainClass = CharBuffer.wrap(line)
                            .subSequence(whereContent, line.length());
                    if (match == 2) return mainClass;
                }
            }
        }
        return mainClass;
    }

}
