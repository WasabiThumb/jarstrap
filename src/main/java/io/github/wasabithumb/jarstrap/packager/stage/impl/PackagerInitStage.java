package io.github.wasabithumb.jarstrap.packager.stage.impl;

import io.github.wasabithumb.jarstrap.asset.AssetEntity;
import io.github.wasabithumb.jarstrap.asset.AssetPath;
import io.github.wasabithumb.jarstrap.asset.AssetSource;
import io.github.wasabithumb.jarstrap.packager.Packager;
import io.github.wasabithumb.jarstrap.packager.PackagerState;
import io.github.wasabithumb.jarstrap.packager.error.PackagerException;
import io.github.wasabithumb.jarstrap.packager.error.PackagerIOException;
import io.github.wasabithumb.jarstrap.packager.stage.PackagerStage;
import io.github.wasabithumb.jarstrap.util.io.StreamUtil;
import org.jetbrains.annotations.NotNull;

import java.io.*;

public class PackagerInitStage implements PackagerStage {

    private static final AssetPath P_TOOL = AssetPath.of("tool");

    @Override
    public @NotNull String id() {
        return "init";
    }

    @Override
    public void execute(@NotNull Packager packager, @NotNull PackagerState state) throws PackagerException {
        final AssetSource tool = AssetSource.resources().sub(P_TOOL);
        final File dest = packager.getWorkingDir();
        try {
            this.copyFromTo(tool, dest);
        } catch (IOException e) {
            throw new PackagerIOException("Failed to extract tool to working directory", e);
        }
    }

    private void copyFromTo(@NotNull AssetSource a, @NotNull File b) throws IOException {
        if (!b.isDirectory() && !b.mkdirs())
            throw new IOException("Path \"" + b.getAbsolutePath() + "\" is not a directory and could not be created");

        for (AssetEntity ent : a.list()) {
            if (ent.isDirectory()) {
                this.copyFromTo(a.sub(ent.name()), new File(b, ent.name()));
            }
            if (ent.isFile()) {
                try (InputStream is = a.read(ent.name());
                     OutputStream os = new FileOutputStream(new File(b, ent.name()), false)
                ) {
                    StreamUtil.pipe(is, os);
                }
            }
        }
    }

}
