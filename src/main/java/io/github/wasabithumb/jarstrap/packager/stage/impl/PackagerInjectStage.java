package io.github.wasabithumb.jarstrap.packager.stage.impl;

import io.github.wasabithumb.jarstrap.packager.Packager;
import io.github.wasabithumb.jarstrap.packager.PackagerState;
import io.github.wasabithumb.jarstrap.packager.error.PackagerException;
import io.github.wasabithumb.jarstrap.packager.error.PackagerIOException;
import io.github.wasabithumb.jarstrap.packager.stage.PackagerStage;
import io.github.wasabithumb.jarstrap.util.io.StreamUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.logging.Level;

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
            packager.logger().log(Level.INFO, "[inject] failed to create symlink, making full copy");
            try (
                    FileInputStream fis = new FileInputStream(src);
                    FileOutputStream fos = new FileOutputStream(dest, false)
            ) {
                StreamUtil.pipe(fis, fos);
            } catch (IOException e2) {
                e2.addSuppressed(e);
                throw new PackagerIOException("Failed to move file @ \"" + src.getAbsolutePath() + "\"", e2);
            }
        }
    }

}
