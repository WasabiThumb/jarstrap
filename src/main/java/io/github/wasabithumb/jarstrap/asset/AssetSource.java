package io.github.wasabithumb.jarstrap.asset;

import io.github.wasabithumb.jarstrap.asset.impl.archive.ArchiveAssetSource;
import io.github.wasabithumb.jarstrap.asset.impl.filesystem.FilesystemAssetSource;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.List;

public interface AssetSource {

    @Contract("-> new")
    static @NotNull AssetSource resources() {
        File codeSource;
        try {
            codeSource = new File(AssetSource.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (URISyntaxException e) {
            throw new AssertionError("Code source is not a valid URI", e);
        }
        if (codeSource.isDirectory()) {
            // Assume a Gradle development environment
            File buildDir = codeSource;
            for (int i=0; i < 3; i++) {
                buildDir = buildDir.getParentFile();
                if (buildDir == null)
                    throw new AssertionError("Code source does not meet expectations for dev environment");
            }
            AssetSource ret = new FilesystemAssetSource(buildDir);
            return ret.sub("resources/main");
        } else if (codeSource.isFile()) {
            return new ArchiveAssetSource(codeSource);
        } else {
            throw new AssertionError("Code source is not a regular file or directory");
        }
    }

    //

    @NotNull AssetSource sub(@NotNull AssetPath path);

    default @NotNull AssetSource sub(@NotNull String path) {
        return this.sub(AssetPath.parse(path));
    }

    @NotNull List<AssetEntity> list() throws IOException;

    @NotNull InputStream read(@NotNull AssetPath path) throws IOException;

    default @NotNull InputStream read(@NotNull String path) throws IOException {
        return this.read(AssetPath.parse(path));
    }

}
