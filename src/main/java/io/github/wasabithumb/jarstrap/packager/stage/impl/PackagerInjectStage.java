package io.github.wasabithumb.jarstrap.packager.stage.impl;

import io.github.wasabithumb.jarstrap.packager.Packager;
import io.github.wasabithumb.jarstrap.packager.PackagerState;
import io.github.wasabithumb.jarstrap.packager.error.PackagerException;
import io.github.wasabithumb.jarstrap.packager.error.PackagerIOException;
import io.github.wasabithumb.jarstrap.packager.stage.PackagerStage;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class PackagerInjectStage implements PackagerStage {

    @Override
    public @NotNull String id() {
        return "inject";
    }

    @Override
    public void execute(@NotNull Packager packager, @NotNull PackagerState state) throws PackagerException {
        final File dest = new File(new File(packager.getWorkingDir(), "archive"), "archive.jar");
        final File src = packager.getSource();

        if (dest.exists()) {
            try {
                Files.delete(dest.toPath());
            } catch (IOException e) {
                throw new PackagerIOException("Failed to delete existing source in working directory", e);
            }
        }

        if (!src.isFile()) {
            throw new PackagerException("Bad configuration (source \"" + src.getAbsolutePath() + "\" is not a file)");
        }

        try {
            Files.createSymbolicLink(
                    dest.toPath(),
                    src.toPath()
            );
        } catch (IOException e) {
            throw new PackagerIOException("Failed to create symlink to path \"" + src.getAbsolutePath() + "\"", e);
        }
    }

}
