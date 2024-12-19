package io.github.wasabithumb.jarstrap.packager.stage.impl;

import io.github.wasabithumb.jarstrap.packager.Packager;
import io.github.wasabithumb.jarstrap.packager.PackagerState;
import io.github.wasabithumb.jarstrap.packager.error.PackagerException;
import io.github.wasabithumb.jarstrap.packager.error.PackagerIOException;
import io.github.wasabithumb.jarstrap.packager.stage.PackagerStage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class PackagerMinGWStage implements PackagerStage {

    @Override
    public @NotNull String id() {
        return "mingw";
    }

    @Override
    public void execute(@NotNull Packager packager, @NotNull PackagerState state) throws PackagerException {
        File gcc = this.where(
                packager.getArch().is64Bit() ?
                        "x86_64-w64-mingw32-gcc.exe" :
                        "i686-w64-mingw32-gcc.exe"
        );
        if (gcc == null)
            throw new PackagerException("MinGW GCC not found on PATH!");

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

}
