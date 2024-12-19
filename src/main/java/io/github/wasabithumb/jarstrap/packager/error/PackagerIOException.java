package io.github.wasabithumb.jarstrap.packager.error;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class PackagerIOException extends PackagerException {

    public PackagerIOException(@NotNull IOException cause) {
        this("Unexpected IO exception in packager stage", cause);
    }

    public PackagerIOException(@NotNull String message, @NotNull IOException cause) {
        super(message, cause);
    }

    @Override
    public @NotNull IOException getCause() {
        return (IOException) super.getCause();
    }

}
