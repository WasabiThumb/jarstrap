package io.github.wasabithumb.jarstrap.packager.stage.impl;

import io.github.wasabithumb.jarstrap.packager.Packager;
import io.github.wasabithumb.jarstrap.packager.PackagerState;
import io.github.wasabithumb.jarstrap.packager.error.PackagerException;
import io.github.wasabithumb.jarstrap.packager.error.PackagerIOException;
import io.github.wasabithumb.jarstrap.packager.error.PackagerInterruptedException;
import io.github.wasabithumb.jarstrap.packager.stage.PackagerStage;
import io.github.wasabithumb.josdirs.JOSDirs;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

public class PackagerCmakeStage implements PackagerStage {

    @Override
    public @NotNull String id() {
        return "cmake";
    }

    @Override
    public void execute(@NotNull Packager packager, @NotNull PackagerState state) throws PackagerException {
        try {
            state.cmakeDir = this.execute0(packager);
        } catch (IOException e) {
            throw new PackagerIOException(e);
        } catch (InterruptedException e) {
            throw new PackagerInterruptedException(e);
        }
    }

    private @NotNull File execute0(@NotNull Packager packager) throws IOException, InterruptedException {
        final File cmakeDir = new File(packager.getWorkingDir(), "cmake");
        if (!cmakeDir.isDirectory() && !cmakeDir.mkdirs())
            throw new IOException("Path \"" + cmakeDir + "\" is not a directory and could not be created");

        String generator;
        if (JOSDirs.platform().equals("windows")) {
            generator = "MinGW Makefiles";
        } else {
            generator = "Unix Makefiles";
        }

        String buildType;
        if (packager.isRelease()) {
            buildType = "Release";
        } else {
            buildType = "Debug";
        }

        ProcessBuilder pb = new ProcessBuilder();
        pb.directory(cmakeDir);
        pb.redirectErrorStream(true);
        if (packager.getArch().is64Bit()) {
            pb.command("cmake", "-G", generator, "..", "-DCMAKE_BUILD_TYPE=" + buildType);
        } else {
            pb.command("cmake", "-G", generator, "..", "-DCMAKE_BUILD_TYPE=" + buildType, "-DCMAKE_C_FLAGS=\"-m32\"");
        }

        Process p = pb.start();

        try (BufferedReader br = p.inputReader(StandardCharsets.UTF_8)) {
            String line;
            while ((line = br.readLine()) != null) {
                packager.logger().log(Level.INFO, "[cmake] " + line);
            }
        }

        int exit = p.waitFor();
        if (exit != 0) {
            throw new PackagerException("cmake exited with non-zero exit code: " + exit);
        }

        return cmakeDir;
    }

}
