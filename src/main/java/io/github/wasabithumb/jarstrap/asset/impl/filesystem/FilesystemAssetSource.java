package io.github.wasabithumb.jarstrap.asset.impl.filesystem;

import io.github.wasabithumb.jarstrap.asset.AssetEntity;
import io.github.wasabithumb.jarstrap.asset.AssetPath;
import io.github.wasabithumb.jarstrap.asset.AssetSource;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FilesystemAssetSource implements AssetSource {

    private final File root;
    public FilesystemAssetSource(@NotNull File root) {
        this.root = root;
    }

    private @NotNull File resolve(@NotNull AssetPath path) {
        File ret = this.root;
        for (CharSequence part : path.parts()) {
            ret = new File(ret, part.toString());
        }
        return ret;
    }

    @Override
    public @NotNull FilesystemAssetSource sub(@NotNull AssetPath path) {
        return new FilesystemAssetSource(this.resolve(path));
    }

    @Override
    public @NotNull List<AssetEntity> list() throws IOException {
        File[] files = this.root.listFiles();
        if (files == null)
            throw new IOException("Path \"" + this.root.getAbsolutePath() + "\" is not a directory");

        int len = files.length;
        List<AssetEntity> ret = new ArrayList<>(len);

        //noinspection ForLoopReplaceableByForEach
        for (int i=0; i < files.length; i++) {
            ret.add(new FilesystemAssetEntity(files[i]));
        }

        return Collections.unmodifiableList(ret);
    }

    @Override
    public @NotNull InputStream read(@NotNull AssetPath path) throws IOException {
        return new FileInputStream(this.resolve(path));
    }

}
