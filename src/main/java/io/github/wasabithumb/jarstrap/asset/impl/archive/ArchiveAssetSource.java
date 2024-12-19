package io.github.wasabithumb.jarstrap.asset.impl.archive;

import io.github.wasabithumb.jarstrap.asset.AssetEntity;
import io.github.wasabithumb.jarstrap.asset.AssetPath;
import io.github.wasabithumb.jarstrap.asset.AssetSource;
import io.github.wasabithumb.jarstrap.util.io.StreamUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

public class ArchiveAssetSource implements AssetSource {

    private final File file;
    private final String root;

    protected ArchiveAssetSource(@NotNull File file, @NotNull String root) {
        this.file = file;
        this.root = root;
    }

    public ArchiveAssetSource(@NotNull File file) {
        this(file, "");
    }

    //

    @Override
    public @NotNull ArchiveAssetSource sub(@NotNull AssetPath path) {
        return new ArchiveAssetSource(this.file, this.root + path + "/");
    }

    @Override
    public @NotNull List<AssetEntity> list() throws IOException {
        List<AssetEntity> ret = new LinkedList<>();
        Pattern p = Pattern.compile("^" + Pattern.quote(this.root) + "([^/]+)(/?)$");
        try (FileInputStream fis = new FileInputStream(this.file);
             ZipInputStream zis = new ZipInputStream(fis)
        ) {
            ZipEntry ze;
            Matcher m;
            while ((ze = zis.getNextEntry()) != null) {
                m = p.matcher(ze.getName());
                if (!m.matches()) continue;
                if (m.groupCount() > 1 && !m.group(2).isEmpty()) {
                    ret.add(AssetEntity.directory(m.group(1)));
                } else {
                    ret.add(AssetEntity.file(m.group(1)));
                }
            }
        }
        return Collections.unmodifiableList(ret);
    }

    @Override
    public @NotNull InputStream read(@NotNull AssetPath path) throws IOException {
        boolean close = true;
        ZipFile zf = null;
        try {
            zf = new ZipFile(this.file);
            ZipEntry ze = zf.getEntry(this.root + path);
            if (ze == null) {
                throw new IOException("Path \"" + this.root + path +
                        "\" does not exist in archive (" + this.file.getAbsolutePath() + ")");
            }
            InputStream ret = zf.getInputStream(ze);
            close = false;
            return StreamUtil.closeListener(ret, zf);
        } finally {
            if (close && zf != null) zf.close();
        }
    }

}
