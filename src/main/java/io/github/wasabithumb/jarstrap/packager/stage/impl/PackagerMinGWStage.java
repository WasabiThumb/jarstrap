package io.github.wasabithumb.jarstrap.packager.stage.impl;

import io.github.wasabithumb.jarstrap.packager.Packager;
import io.github.wasabithumb.jarstrap.packager.PackagerState;
import io.github.wasabithumb.jarstrap.packager.error.PackagerException;
import io.github.wasabithumb.jarstrap.packager.error.PackagerIOException;
import io.github.wasabithumb.jarstrap.packager.stage.PackagerStage;
import io.github.wasabithumb.jarstrap.util.io.StreamUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;

public class PackagerMinGWStage implements PackagerStage {

    private static final String URL_XZ = "https://repo1.maven.org/maven2/org/tukaani/xz/1.10/xz-1.10.jar";
    private static final String URL_COMPRESS = "https://repo1.maven.org/maven2/org/apache/commons/commons-compress/1.27.1/commons-compress-1.27.1.jar";
    private static final String URL_64 = "https://github.com/niXman/mingw-builds-binaries/releases/download/14.2.0-rt_v12-rev0/x86_64-14.2.0-release-posix-seh-ucrt-rt_v12-rev0.7z";
    private static final String URL_32 = "https://github.com/niXman/mingw-builds-binaries/releases/download/14.2.0-rt_v12-rev0/i686-14.2.0-release-posix-dwarf-ucrt-rt_v12-rev0.7z";
    private static final String GCC_64 = "x86_64-w64-mingw32-gcc.exe";
    private static final String GCC_32 = "i686-w64-mingw32-gcc.exe";

    @Override
    public @NotNull String id() {
        return "mingw";
    }

    @Override
    public void execute(@NotNull Packager packager, @NotNull PackagerState state) throws PackagerException {
        final boolean amd64 = packager.getArch().is64Bit();
        File gcc = this.where(amd64 ? GCC_64 : GCC_32);
        if (gcc == null) {
            PackagerException base = new PackagerException("MinGW GCC not found on PATH");
            if (!packager.isAutoInstall()) throw base;
            packager.logger().log(Level.INFO, "[mingw] installing (" + (amd64 ? "64" : "32") + "-bit)...");
            try {
                gcc = this.autoInstall(packager.getWorkingDir(), amd64);
            } catch (IOException e) {
                PackagerException io = new PackagerIOException("Failed to automatically install MinGW", e);
                io.addSuppressed(base);
                throw io;
            }
            packager.logger().log(Level.INFO, "[mingw] installed @ " + gcc.getAbsolutePath());
        }

        File make = new File(gcc.getParentFile(), "mingw32-make.exe");
        if (!make.isFile())
            throw new PackagerException("MinGW Make not found (found " + gcc.getAbsolutePath() + ")!");

        state.mingwMake = make;
    }

    private @Nullable File where(@NotNull String executable) throws PackagerException {
        try {
            Process process = Runtime.getRuntime().exec(new String[]{ "where", executable });
            try (BufferedReader in = process.inputReader(StandardCharsets.UTF_8)) {
                String line = in.readLine();
                if (line == null || line.isBlank()) return null;

                File file = new File(line);
                if (!file.isFile()) return null;

                return file;
            }
        } catch (IOException e) {
            throw new PackagerIOException("Failed to identify location of \"" + executable + "\"", e);
        }
    }

    private @NotNull File autoInstall(@NotNull File workingDir, boolean amd64) throws IOException {
        final File dest = new File(workingDir.getParentFile(), "mingw");
        final File compiler = new File(
                new File(new File(dest, amd64 ? "mingw64" : "mingw32"), "bin"),
                amd64 ? GCC_64 : GCC_32
        );
        if (compiler.exists()) return compiler;
        if (!dest.mkdirs()) {
            throw new IOException("Failed to created directory \"" + dest.getAbsolutePath() + "\"");
        }

        final File temp = Files.createTempDirectory("autoInstall").toFile();
        try {
            this.autoInstall0(dest, temp, amd64 ? URL_64 : URL_32);
            return compiler;
        } finally {
            File[] list = temp.listFiles();
            if (list != null) {
                for (File file : list)
                    Files.delete(file.toPath());
            }
            Files.delete(temp.toPath());
        }
    }

    private void autoInstall0(@NotNull File dest, @NotNull File temp, @NotNull String archiveURL) throws IOException {
        File archive = this.fetchBinary(temp, archiveURL, "mingw.7z", "application/x-7z-compressed");
        URL[] libraries = new URL[2];
        libraries[0] = this.fetchLibrary(temp, URL_XZ, "xz.jar");
        libraries[1] = this.fetchLibrary(temp, URL_COMPRESS, "compress.jar");
        try (URLClassLoader cl = new URLClassLoader(libraries, PackagerMinGWStage.class.getClassLoader())) {
            this.autoInstall00(dest, archive, cl);
        } catch (ReflectiveOperationException e) {
            throw new PackagerException("Unexpected dynamic linkage error", e);
        }
    }

    private void autoInstall00(
            @NotNull File dest,
            @NotNull File archive,
            @NotNull ClassLoader cl
    ) throws IOException, ReflectiveOperationException {
        Class<?> cSevenZFile = Class.forName(
                "org.apache.commons.compress.archivers.sevenz.SevenZFile",
                true,
                cl
        );
        Method mSevenZFileBuilder = cSevenZFile.getDeclaredMethod("builder");
        Object builder = mSevenZFileBuilder.invoke(null);
        Class<?> cBuilder = builder.getClass();

        Method mBuilderSetSeekableByteChannel = cBuilder.getMethod(
                "setSeekableByteChannel",
                SeekableByteChannel.class
        );
        mBuilderSetSeekableByteChannel.invoke(builder, Files.newByteChannel(archive.toPath()));
        Method mBuilderGet = cBuilder.getMethod("get");
        Object szf = mBuilderGet.invoke(builder);

        Class<?> cSevenZArchiveEntry = Class.forName(
                "org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry",
                true,
                cl
        );
        Method mSevenZArchiveEntryIsDirectory = cSevenZArchiveEntry.getMethod("isDirectory");
        Method mSevenZArchiveEntryGetName = cSevenZArchiveEntry.getMethod("getName");
        Method mSevenZFileGetNextEntry = cSevenZFile.getMethod("getNextEntry");
        Method mSevenZFileGetInputStream = cSevenZFile.getMethod("getInputStream", cSevenZArchiveEntry);

        final Path destPath = dest.toPath();
        Object entry;
        Boolean isDirectory;
        String name;
        Path path;

        while (true) {
            entry = mSevenZFileGetNextEntry.invoke(szf);
            if (entry == null) break;

            isDirectory = (Boolean) mSevenZArchiveEntryIsDirectory.invoke(entry);
            name = (String) mSevenZArchiveEntryGetName.invoke(entry);
            path = destPath.resolve(name);

            if (isDirectory) {
                Files.createDirectory(path);
                continue;
            }

            File create = path.toFile();
            File parent = create.getParentFile();
            if (!parent.isDirectory() && !parent.mkdirs())
                throw new IOException("Path \"" + parent.getAbsolutePath() + "\" is not a directory and could not be created");

            try (InputStream is = (InputStream) mSevenZFileGetInputStream.invoke(szf, entry);
                 OutputStream os = new FileOutputStream(create, false)
            ) {
                StreamUtil.pipe(is, os);
            }
        }
    }

    private @NotNull URL fetchLibrary(@NotNull File dir, @NotNull String url, @NotNull String name) throws IOException {
        final File file = this.fetchBinary(dir, url, name, "application/java-archive");
        return URI.create(
                "jar:file:/" + file.getAbsolutePath()
        ).toURL();
    }

    private @NotNull File fetchBinary(
            @NotNull File dir,
            @NotNull String url,
            @NotNull String name,
            @NotNull String accept
    ) throws IOException {
        URL source = URI.create(url).toURL();
        HttpURLConnection c = (HttpURLConnection) source.openConnection();
        c.setRequestProperty("Accept", accept);
        c.setRequestProperty("User-Agent", "jarstrap; wasabithumbs@gmail.com");

        int status = c.getResponseCode();
        if (status < 200 || status > 299) {
            throw new IOException("Non-2XX HTTP response code " + status + " (" + c.getResponseMessage() + ")");
        }

        final File dest = new File(dir, name);

        try (InputStream is = c.getInputStream();
             FileOutputStream fos = new FileOutputStream(dest, false)
        ) {
            StreamUtil.pipe(is, fos);
        }
        return dest;
    }

}
