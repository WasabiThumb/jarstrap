package io.github.wasabithumb.jarstrap.packager;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.UnknownNullability;

import java.io.File;

@ApiStatus.Internal
public class PackagerState {

    public @UnknownNullability File cmakeDir = null;

    public @UnknownNullability File mingwMake = null;

}
