package io.github.wasabithumb.jarstrap.packager.stage.impl;

import io.github.wasabithumb.jarstrap.manifest.ManifestFile;
import io.github.wasabithumb.jarstrap.packager.Packager;
import io.github.wasabithumb.jarstrap.packager.PackagerState;
import io.github.wasabithumb.jarstrap.packager.error.PackagerException;
import io.github.wasabithumb.jarstrap.packager.error.PackagerIOException;
import io.github.wasabithumb.jarstrap.packager.stage.PackagerStage;
import io.github.wasabithumb.jarstrap.util.io.StreamUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.net.URI;
import java.nio.file.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class PackagerManifestStage implements PackagerStage {

    private static final byte[] CLASS_MAGIC = new byte[] {
            (byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE
    };

    @Override
    public @NotNull String id() {
        return "manifest";
    }

    @Override
    public void execute(@NotNull Packager packager, @NotNull PackagerState state) throws PackagerException {
        final File file = new File(new File(packager.getWorkingDir(), "archive"), "archive.jar");
        ManifestFile mf;
        try {
            mf = this.readManifest(file);
        } catch (IOException e) {
            throw new PackagerIOException(e);
        }

        boolean hadManifest = true;
        if (mf == null) {
            hadManifest = false;
            mf = new ManifestFile();
        }

        byte[] preHash = mf.md5();
        packager.getManifest().apply(mf);
        byte[] postHash = mf.md5();
        boolean modified = !Arrays.equals(preHash, postHash);

        final String mainClass = mf.get("Main-Class");
        if (mainClass == null) {
            throw new PackagerException("Main class is not in JAR manifest and was not specified in packager options");
        }

        int major;
        try {
            major = this.readMajor(file, mainClass);
        } catch (IOException e) {
            throw new PackagerIOException(e);
        }

        if (major < 49)
            throw new PackagerException("Main class of JAR has unsupported class file major version " + major);

        int minJavaVersion = major - 44;
        int curMinJavaVersion = packager.getMinJavaVersion();
        if (minJavaVersion > curMinJavaVersion) {
            int preferredJavaVersion = packager.getPreferredJavaVersion();
            if (minJavaVersion > preferredJavaVersion)
                throw new PackagerException("Main class of JAR file was compiled against Java " + minJavaVersion + ", but preferred Java version is " + preferredJavaVersion);
            packager.setMinJavaVersion(minJavaVersion);
        }

        if (!modified) return;
        try {
            if (hadManifest) {
                this.updateExistingManifest(file, mf);
            } else {
                this.addNewManifest(file, mf);
            }
        } catch (IOException e) {
            throw new PackagerIOException("Failed to update JAR manifest", e);
        }
    }

    private void addNewManifest(@NotNull File file, @NotNull ManifestFile mf) throws IOException {
        final URI uri = URI.create("jar:" + file.toPath().toUri());
        try (FileSystem fs = FileSystems.newFileSystem(uri, Collections.emptyMap())) {
            Path p = fs.getPath("META-INF" + fs.getSeparator() + "MANIFEST.MF");
            try (OutputStream os = Files.newOutputStream(p, StandardOpenOption.CREATE)) {
                mf.write(os);
            }
        } catch (IOException e) {
            throw new PackagerIOException("Failed to write new manifest to JAR", e);
        }
    }

    private void updateExistingManifest(@NotNull File file, @NotNull ManifestFile mf) throws IOException {
        final File temp = new File(file.getParentFile(), file.getName() + ".tmp");
        try (InputStream is = new FileInputStream(file);
             ZipInputStream src = new ZipInputStream(is);
             OutputStream os = new FileOutputStream(temp);
             ZipOutputStream dest = new ZipOutputStream(os)
        ) {
            this.updateExistingManifest0(src, dest, mf);
        } catch (IOException e) {
            try {
                Files.deleteIfExists(temp.toPath());
            } catch (IOException e1) {
                e.addSuppressed(e1);
            }
            throw e;
        }
        Files.move(
                temp.toPath(),
                file.toPath(),
                StandardCopyOption.REPLACE_EXISTING
        );
    }

    private void updateExistingManifest0(
            @NotNull ZipInputStream src,
            @NotNull ZipOutputStream dest,
            @NotNull ManifestFile mf
    ) throws IOException {
        ZipEntry ze;
        String name;
        boolean hasMetaDir = false;

        while ((ze = src.getNextEntry()) != null) {
            name = ze.getName();
            if (name.equals("META-INF/MANIFEST.MF")) {
                continue;
            } else if (name.equals("META-INF/")) {
                hasMetaDir = true;
            }
            dest.putNextEntry(ze);
            if (ze.isDirectory()) {
                dest.closeEntry();
                continue;
            }
            StreamUtil.pipe(src, dest);
        }

        if (!hasMetaDir) {
            ze = new ZipEntry("META-INF/");
            ze.setMethod(ZipEntry.STORED);
            ze.setSize(0L);
            ze.setCrc(0L);
            dest.putNextEntry(ze);
            dest.closeEntry();
        }

        ze = new ZipEntry("META-INF/MANIFEST.MF");
        dest.putNextEntry(ze);
        mf.write(dest);
    }

    private int readMajor(@NotNull File file, @NotNull String mainClass) throws IOException, PackagerException {
        final StringBuilder entryName = new StringBuilder(mainClass.length() + 6);
        char c;
        for (int i=0; i < mainClass.length(); i++) {
            c = mainClass.charAt(i);
            if (c == '.') c = '/';
            entryName.append(c);
        }
        entryName.append(".class");

        try (ZipFile zf = new ZipFile(file)) {
            ZipEntry ze = zf.getEntry(entryName.toString());
            if (ze == null)
                throw new PackagerException("Main class of JAR (" + mainClass + ") is not contained in the file");

            try (InputStream is = zf.getInputStream(ze); DataInputStream dis = new DataInputStream(is)) {
                byte[] header = dis.readNBytes(CLASS_MAGIC.length);
                if (!Arrays.equals(header, CLASS_MAGIC))
                    throw new PackagerException("Main class of JAR (" + mainClass + ") has corrupted header");

                dis.skipNBytes(Short.BYTES);
                return dis.readShort();
            } catch (EOFException e) {
                throw new PackagerIOException("Main class of JAR (" + mainClass + ") has been truncated", e);
            }
        }
    }

    private @Nullable ManifestFile readManifest(@NotNull File file) throws IOException {
        try (ZipFile zf = new ZipFile(file)) {
            ZipEntry ze = zf.getEntry("META-INF/MANIFEST.MF");
            if (ze == null) return null;

            try (InputStream is = zf.getInputStream(ze)) {
                ManifestFile mf = new ManifestFile();
                mf.read(is);
                return mf;
            }
        }
    }

}
