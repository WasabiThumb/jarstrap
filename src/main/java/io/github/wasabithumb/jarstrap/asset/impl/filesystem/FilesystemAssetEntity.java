package io.github.wasabithumb.jarstrap.asset.impl.filesystem;

import io.github.wasabithumb.jarstrap.asset.AssetEntity;
import org.jetbrains.annotations.NotNull;

class FilesystemAssetEntity implements AssetEntity {

    private final java.io.File file;
    public FilesystemAssetEntity(@NotNull java.io.File file) {
        this.file = file;
    }

    @Override
    public @NotNull String name() {
        return this.file.getName();
    }

    @Override
    public boolean isFile() {
        return this.file.isFile();
    }

    @Override
    public boolean isDirectory() {
        return this.file.isDirectory();
    }

}
