package io.github.wasabithumb.jarstrap.asset;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public interface AssetEntity {

    @Contract("_ -> new")
    static @NotNull AssetEntity file(@NotNull String name) {
        return new File(name);
    }

    @Contract("_ -> new")
    static @NotNull AssetEntity directory(@NotNull String name) {
        return new Directory(name);
    }

    //

    @NotNull String name();

    boolean isFile();

    boolean isDirectory();

    //

    record File(@NotNull String name) implements AssetEntity {

        @Override
        public boolean isFile() {
            return true;
        }

        @Override
        public boolean isDirectory() {
            return false;
        }

    }

    record Directory(@NotNull String name) implements AssetEntity {

        @Override
        public boolean isFile() {
            return false;
        }

        @Override
        public boolean isDirectory() {
            return true;
        }

    }

}
