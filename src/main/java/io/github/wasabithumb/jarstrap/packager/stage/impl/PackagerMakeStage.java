package io.github.wasabithumb.jarstrap.packager.stage.impl;

import io.github.wasabithumb.jarstrap.packager.Packager;
import io.github.wasabithumb.jarstrap.packager.PackagerState;
import io.github.wasabithumb.jarstrap.packager.error.PackagerException;
import io.github.wasabithumb.jarstrap.packager.error.PackagerIOException;
import io.github.wasabithumb.jarstrap.packager.error.PackagerInterruptedException;
import io.github.wasabithumb.jarstrap.packager.stage.PackagerStage;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PackagerMakeStage implements PackagerStage {

    @Override
    public @NotNull String id() {
        return "make";
    }

    @Override
    public void execute(@NotNull Packager packager, @NotNull PackagerState state) throws PackagerException {
        try {
            this.execute0(state, packager.logger());
        } catch (IOException e) {
            throw new PackagerIOException("Failed to run make", e);
        } catch (InterruptedException e) {
            throw new PackagerInterruptedException(e);
        }
    }

    private void execute0(@NotNull PackagerState state, @NotNull Logger logger) throws IOException, InterruptedException {
        String makeCmd;
        if (state.mingwMake != null) {
            makeCmd = state.mingwMake.getAbsolutePath();
        } else {
            makeCmd = "make";
        }

        ProcessBuilder pb = new ProcessBuilder();
        pb.directory(state.cmakeDir);
        pb.command(makeCmd);
        pb.redirectErrorStream(true);

        Process p = pb.start();

        try (BufferedReader br = p.inputReader(StandardCharsets.UTF_8)) {
            String line;
            while ((line = br.readLine()) != null) {
                logger.log(Level.INFO, "[make] " + line);
            }
        }

        int exit = p.waitFor();
        if (exit != 0) {
            throw new PackagerException("make exited with non-zero exit code: " + exit);
        }
    }

}
