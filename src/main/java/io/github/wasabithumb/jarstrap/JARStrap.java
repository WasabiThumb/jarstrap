package io.github.wasabithumb.jarstrap;

import io.github.wasabithumb.jarstrap.packager.Packager;
import io.github.wasabithumb.jarstrap.util.Optimus;
import io.github.wasabithumb.josdirs.JOSDirs;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class JARStrap {

    /**
     * Creates a new packager instance. Each packager has exclusive access to its resources and is thread safe.
     * @param logger The logger for the packager to use
     * @see #createPackager() 
     */
    @Contract("_ -> new")
    public static @NotNull Packager createPackager(@NotNull Logger logger) {
        return new Packager(nextInstanceDir(), logger);
    }

    /**
     * Creates a new packager instance with an anonymous logger of level {@link Level#WARNING}
     * @see #createPackager(Logger)
     */
    @Contract(" -> new")
    public static @NotNull Packager createPackager() {
        Logger logger = Logger.getAnonymousLogger();
        logger.setLevel(Level.WARNING);
        return createPackager(logger);
    }

    // Determine a constant filesystem to use for JARStrap work.
    private static File DATA_DIR = null;
    static synchronized @NotNull File dataDir() {
        if (DATA_DIR == null) {
            DATA_DIR = JOSDirs.osDirs()
                    .userData()
                    .roaming(false)
                    .appAuthor("Wasabi Codes")
                    .appName("jarstrap")
                    .toFile();
        }
        return DATA_DIR;
    }

    // Shuffles the sequential IDs used for instances.
    private static final Optimus OPTIMUS = Optimus.generate();

    // Determine a temporary filesystem within the data filesystem.
    private static int INSTANCE_COUNTER = 1;
    static synchronized @NotNull File nextInstanceDir() {
        final int id = OPTIMUS.encode(INSTANCE_COUNTER++);
        final char[] name = new char[9];
        name[0] = 'I';
        for (int i=0; i < 8; i++) name[8 - i] = Character.forDigit((id >> (i << 2)) & 0xF, 16);
        return new File(dataDir(), new String(name));
    }

}
