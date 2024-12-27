package io.github.wasabithumb.jarstrap;

import io.github.wasabithumb.jarstrap.packager.Packager;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class JARStrapTest {

    @Test
    void test() {
        final Logger logger = Logger.getLogger("JARStrapTest");
        logger.setLevel(Level.FINER);

        final ConsoleHandler ch = new ConsoleHandler();
        ch.setLevel(Level.FINER);
        logger.addHandler(ch);

        File output;
        try (Packager p = JARStrap.createPackager(logger)) {
            p.setAppName("Sample App");
            assertEquals(p.getOutputName(), "sample_app");
            assertEquals(p.getSource().getName(), "sample.jar");
            output = p.getOutputFile();
            p.execute();
        }
        assertTrue(output.isFile());
        System.out.println("Created executable: " + output.getAbsolutePath());
    }

}