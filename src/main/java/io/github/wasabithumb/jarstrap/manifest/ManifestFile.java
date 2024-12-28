package io.github.wasabithumb.jarstrap.manifest;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

public class ManifestFile {

    private final SortedMap<String, String> map = new TreeMap<>();

    //

    public @Nullable String get(@NotNull String key) {
        String value = this.map.get(key);
        if (value != null) return value;

        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (key.equalsIgnoreCase(entry.getKey()))
                return entry.getValue();
        }

        return null;
    }

    public void put(@NotNull String key, @NotNull String value) {
        this.map.put(key, value);
    }

    public void remove(@NotNull String key) {
        this.map.remove(key);
    }

    public void read(@NotNull InputStream in) throws IOException {
        try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8);
             BufferedReader br = new BufferedReader(r, 256)
        ) {
            String line;
            int head = 0;
            while ((line = br.readLine()) != null) {
                this.useLine(line, ++head);
            }
        }
    }

    private void useLine(@NotNull String line, int index) throws IOException {
        final int len = line.length();
        if (len == 0) return;

        int whereSep = line.indexOf(':');
        if (whereSep == -1)
            throw new IOException("Invalid entry @ line #" + index + " of manifest");

        int whereValue = whereSep + 1;
        while (whereValue < len && Character.isWhitespace(line.charAt(whereValue))) {
            whereValue++;
        }

        this.map.put(
                line.substring(0, whereSep),
                line.substring(whereValue, len)
        );
    }

    public void write(@NotNull OutputStream os) throws IOException {
        try (Writer w = new OutputStreamWriter(os, StandardCharsets.UTF_8);
             BufferedWriter bw = new BufferedWriter(w)
        ) {
            for (Map.Entry<String, String> entry : this.map.entrySet()) {
                bw.write(entry.getKey());
                bw.write(": ");
                bw.write(entry.getValue());
                bw.write('\n');
            }
        }
        os.flush();
    }

    public byte[] md5() {
        if (this.map.isEmpty()) return new byte[16];

        MessageDigest md;
        try {
            md = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError("MD5 algorithm not available", e);
        }

        for (Map.Entry<String, String> entry : this.map.entrySet()) {
            this.hashString(md, entry.getKey());
            md.update((byte) ':');
            this.hashString(md, entry.getValue());
            md.update((byte) '\t');
        }

        return md.digest();
    }

    private void hashString(@NotNull MessageDigest md, @NotNull String s) {
        ByteBuffer buf = ByteBuffer.allocate(Character.BYTES);
        buf.order(ByteOrder.LITTLE_ENDIAN);

        for (int i=0; i < s.length(); i++) {
            buf.putChar(0, s.charAt(i));
            md.update(buf);
        }
    }

}
