package io.github.wasabithumb.jarstrap.packager.stage;

import io.github.wasabithumb.jarstrap.packager.Packager;
import io.github.wasabithumb.jarstrap.packager.PackagerState;
import io.github.wasabithumb.jarstrap.packager.error.PackagerException;
import org.jetbrains.annotations.NotNull;

public interface PackagerStage {

    @NotNull String id();

    void execute(@NotNull Packager packager, @NotNull PackagerState state) throws PackagerException;

}
