package io.github.wasabithumb.jarstrap.manifest;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public sealed interface ManifestOperation {

    @Contract("_, _ -> new")
    static @NotNull ManifestOperation put(@NotNull String key, @NotNull String value) {
        return new Put(key, value);
    }

    @Contract("_ -> new")
    static @NotNull ManifestOperation remove(@NotNull String key) {
        return new Remove(key);
    }

    //

    void apply(@NotNull ManifestFile file);

    //

    final class Put implements ManifestOperation {

        private final String key;
        private final String value;
        Put(@NotNull String key, @NotNull String value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public void apply(@NotNull ManifestFile file) {
            file.put(this.key, this.value);
        }

    }

    final class Remove implements ManifestOperation {

        private final String key;
        Remove(@NotNull String key) {
            this.key = key;
        }

        @Override
        public void apply(@NotNull ManifestFile file) {
            file.remove(key);
        }

    }

}
