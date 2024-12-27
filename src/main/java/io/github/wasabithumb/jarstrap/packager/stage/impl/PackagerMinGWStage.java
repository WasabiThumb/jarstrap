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
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class PackagerMinGWStage implements PackagerStage {

    private static final String DOWNLOAD_URL = "https://github.com/mstorsjo/llvm-mingw/releases/download/20241217/llvm-mingw-20241217-ucrt-x86_64.zip";
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
            packager.logger().log(Level.INFO, "[mingw] installing...");
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
                new File(dest, "bin"),
                amd64 ? GCC_64 : GCC_32
        );
        if (compiler.exists()) return compiler;
        if (!dest.isDirectory() && !dest.mkdirs()) {
            throw new IOException("Failed to create directory \"" + dest.getAbsolutePath() + "\"");
        }

        final File temp = Files.createTempDirectory("autoInstall").toFile();
        try {
            this.autoInstall0(dest, temp);
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

    private void autoInstall0(@NotNull File dest, @NotNull File temp) throws IOException {
        final Path destPath = dest.toPath();
        final File archive = this.fetchPackage(temp);
        try (FileInputStream fis = new FileInputStream(archive);
             ZipInputStream zis = new ZipInputStream(fis)
        ) {
            ZipEntry ze;
            while ((ze = zis.getNextEntry()) != null) {
                String name = ze.getName();
                int whereSlash = name.indexOf('/');
                if (whereSlash == -1 || whereSlash == (name.length() - 1)) continue;
                name = name.substring(whereSlash + 1);

                if (ze.isDirectory()) {
                    Files.createDirectory(destPath.resolve(name));
                    continue;
                }

                File create = destPath.resolve(name).toFile();
                File parent = create.getParentFile();
                if (!parent.isDirectory() && !parent.mkdirs())
                    throw new IOException("Path \"" + parent.getAbsolutePath() + "\" is not a directory and could not be created");

                try (FileOutputStream fos = new FileOutputStream(create, false)) {
                    StreamUtil.pipe(zis, fos);
                }
            }
        }
    }

    private @NotNull File fetchPackage(
            @NotNull File dir
    ) throws IOException {
        URL source = URI.create(DOWNLOAD_URL).toURL();
        HttpURLConnection c = (HttpURLConnection) source.openConnection();
        c.setRequestProperty("Accept", "application/zip");
        c.setRequestProperty("User-Agent", "jarstrap; wasabithumbs@gmail.com");

        int status = c.getResponseCode();
        if (status < 200 || status > 299) {
            throw new IOException("Non-2XX HTTP response code " + status + " (" + c.getResponseMessage() + ")");
        }

        final File dest = new File(dir, "mingw.zip");
        try (InputStream is = c.getInputStream();
             FileOutputStream fos = new FileOutputStream(dest, false)
        ) {
            StreamUtil.pipe(is, fos);
        }
        return dest;
    }

}
