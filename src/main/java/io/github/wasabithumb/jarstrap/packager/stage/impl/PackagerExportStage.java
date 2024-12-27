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

public class PackagerExportStage implements PackagerStage {

    @Override
    public @NotNull String id() {
        return "export";
    }

    @Override
    public void execute(@NotNull Packager packager, @NotNull PackagerState state) throws PackagerException {
        final File src = new File(state.cmakeDir, "jarstrap" + packager.getExtension());
        final File dest = packager.getOutputFile();

        if (!src.isFile()) {
            throw new PackagerException("Output file \"" + src.getAbsolutePath() + "\" not found");
        }

        try (FileInputStream fis = new FileInputStream(src);
             FileOutputStream fos = new FileOutputStream(dest, false)
        ) {
            StreamUtil.pipe(fis, fos);
        } catch (IOException e) {
            throw new PackagerIOException("Failed to export output file", e);
        }
    }

}
