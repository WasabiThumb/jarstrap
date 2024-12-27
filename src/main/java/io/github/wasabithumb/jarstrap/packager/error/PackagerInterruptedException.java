package io.github.wasabithumb.jarstrap.packager.error;

import org.jetbrains.annotations.NotNull;

public class PackagerInterruptedException extends PackagerException {

    public PackagerInterruptedException(@NotNull InterruptedException cause) {
        this("Packager interrupted while waiting on subprocess", cause);
    }

    public PackagerInterruptedException(@NotNull String message, @NotNull InterruptedException cause) {
        super(message, cause);
    }

    @Override
    public @NotNull InterruptedException getCause() {
        return (InterruptedException) super.getCause();
    }

}
