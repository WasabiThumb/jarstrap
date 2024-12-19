package io.github.wasabithumb.jarstrap.packager;

public enum PackagerArch {
    X86_64(true),
    X86(false);

    private final boolean amd64;
    PackagerArch(boolean amd64) {
        this.amd64 = amd64;
    }

    public boolean is64Bit() {
        return this.amd64;
    }
}
