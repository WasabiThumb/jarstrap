package io.github.wasabithumb.jarstrap.packager;

import io.github.wasabithumb.jarstrap.packager.error.PackagerException;
import io.github.wasabithumb.jarstrap.packager.error.PackagerIOException;
import io.github.wasabithumb.jarstrap.packager.stage.PackagerStage;
import io.github.wasabithumb.jarstrap.packager.stage.impl.*;
import io.github.wasabithumb.josdirs.JOSDirs;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Range;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.StampedLock;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Packager implements AutoCloseable {

    private final File workingDir;
    private final Queue<PackagerStage> stages;
    private final StampedLock stageLock;
    private final Lock execLock;
    private final StampedLock attrLock;
    private final Logger logger;
    private final PackagerState state;

    private PackagerArch arch = PackagerArch.X86_64;
    private boolean release = false;
    private String appName = null;
    private int minJavaVersion = 8;
    private int preferredJavaVersion = 21;
    private String launchFlags = "";
    private File source = null;
    private File outputDir = null;
    private String outputName = null;

    public Packager(@NotNull File workingDir, @NotNull Logger logger) {
        this.workingDir = workingDir;
        this.stages = new LinkedList<>();
        this.setupStages(this.stages);
        this.stageLock = new StampedLock();
        this.execLock = new ReentrantLock();
        this.attrLock = new StampedLock();
        this.logger = logger;
        this.state = new PackagerState();
    }

    protected void setupStages(@NotNull Queue<PackagerStage> stages) {
        stages.add(new PackagerInitStage());
        stages.add(new PackagerInjectStage());
        if (JOSDirs.platform().equals("windows")) stages.add(new PackagerMinGWStage());
        stages.add(new PackagerCmakeStage());
        stages.add(new PackagerMakeStage());
        stages.add(new PackagerExportStage());
    }

    public @NotNull File getWorkingDir() {
        return this.workingDir;
    }

    public @NotNull File getOutputFile() {
        return new File(this.getOutputDir(), this.getOutputName() + this.getExtension());
    }

    public @NotNull String getExtension() {
        if (JOSDirs.platform().equals("windows")) {
            return ".exe";
        }
        return "";
    }

    public @NotNull Logger logger() {
        return this.logger;
    }

    //

    public @NotNull PackagerArch getArch() {
        final long stamp = this.attrLock.readLock();
        try {
            return this.arch;
        } finally {
            this.attrLock.unlock(stamp);
        }
    }

    public void setArch(@NotNull PackagerArch arch) {
        final long stamp = this.attrLock.writeLock();
        try {
            this.arch = arch;
        } finally {
            this.attrLock.unlock(stamp);
        }
    }

    public boolean isRelease() {
        final long stamp = this.attrLock.readLock();
        try {
            return this.release;
        } finally {
            this.attrLock.unlock(stamp);
        }
    }

    public void setRelease(boolean release) {
        final long stamp = this.attrLock.writeLock();
        try {
            this.release = release;
        } finally {
            this.attrLock.unlock(stamp);
        }
    }

    public @NotNull String getAppName() {
        final long stamp = this.attrLock.readLock();
        try {
            if (this.appName == null) return "JARStrap";
            return this.appName;
        } finally {
            this.attrLock.unlock(stamp);
        }
    }

    public void setAppName(@Nullable String appName) {
        final long stamp = this.attrLock.writeLock();
        try {
            this.appName = appName;
        } finally {
            this.attrLock.unlock(stamp);
        }
    }

    public int getMinJavaVersion() {
        final long stamp = this.attrLock.readLock();
        try {
            return this.minJavaVersion;
        } finally {
            this.attrLock.unlock(stamp);
        }
    }

    public void setMinJavaVersion(@Range(from = 5L, to = 21L) int minJavaVersion) {
        //noinspection ConstantValue
        if (minJavaVersion < 5 || minJavaVersion > 21)
            throw new IllegalArgumentException("Java version " + minJavaVersion + " out of bounds (expected 5 - 21)");

        final long stamp = this.attrLock.writeLock();
        try {
            this.minJavaVersion = minJavaVersion;
        } finally {
            this.attrLock.unlock(stamp);
        }
    }

    public int getPreferredJavaVersion() {
        final long stamp = this.attrLock.readLock();
        try {
            return this.preferredJavaVersion;
        } finally {
            this.attrLock.unlock(stamp);
        }
    }

    public void setPreferredJavaVersion(@Range(from = 5L, to = 21L) int preferredJavaVersion) {
        //noinspection ConstantValue
        if (preferredJavaVersion < 5 || preferredJavaVersion > 21)
            throw new IllegalArgumentException("Java version " + preferredJavaVersion + " out of bounds (expected 5 - 21)");

        final long stamp = this.attrLock.writeLock();
        try {
            this.preferredJavaVersion = preferredJavaVersion;
        } finally {
            this.attrLock.unlock(stamp);
        }
    }

    public @NotNull String getLaunchFlags() {
        final long stamp = this.attrLock.readLock();
        try {
            return this.launchFlags;
        } finally {
            this.attrLock.unlock(stamp);
        }
    }

    public void setLaunchFlags(@NotNull String flags) {
        final long stamp = this.attrLock.writeLock();
        try {
            this.launchFlags = Objects.requireNonNull(flags);
        } finally {
            this.attrLock.unlock(stamp);
        }
    }

    public @NotNull File getSource() {
        final long stamp = this.attrLock.readLock();
        try {
            if (this.source == null) return new File(new File(this.workingDir, "archive"), "sample.jar");
            return this.source;
        } finally {
            this.attrLock.unlock(stamp);
        }
    }

    public void setSource(@Nullable File source) {
        final long stamp = this.attrLock.writeLock();
        try {
            this.source = source;
        } finally {
            this.attrLock.unlock(stamp);
        }
    }

    public @NotNull File getOutputDir() {
        final long stamp = this.attrLock.readLock();
        try {
            if (this.outputDir == null)
                return new File(System.getProperty("user.dir"));
            return this.outputDir;
        } finally {
            this.attrLock.unlock(stamp);
        }
    }

    public void setOutputDir(@Nullable File outputDir) {
        final long stamp = this.attrLock.writeLock();
        try {
            this.outputDir = outputDir;
        } finally {
            this.attrLock.unlock(stamp);
        }
    }

    public @NotNull String getOutputName() {
        final long stamp = this.attrLock.readLock();
        try {
            if (this.outputName == null) {
                if (this.appName == null) return "jarstrap";
                final int len = this.appName.length();
                final char[] chars = new char[len];
                char c;
                for (int i=0; i < len; i++) {
                    c = this.appName.charAt(i);
                    if (Character.isWhitespace(c)) {
                        chars[i] = '_';
                    } else {
                        chars[i] = Character.toLowerCase(c);
                    }
                }
                return new String(chars);
            }
            return this.outputName;
        } finally {
            this.attrLock.unlock(stamp);
        }
    }

    public void setOutputName(@Nullable String outputName) {
        final long stamp = this.attrLock.writeLock();
        try {
            this.outputName = outputName;
        } finally {
            this.attrLock.unlock(stamp);
        }
    }

    /**
     * Executes all packager stages in succession. For more control, use {@link #nextStage()} and {@link #executeStage()}
     */
    public void execute() throws PackagerException {
        while (true) {
            if (!this.executeStage()) break;
        }
    }

    /**
     * Return the ID of the packager stage that will execute next or is currently executing.
     * Returns null if all stages have executed.
     */
    public @Nullable String nextStage() {
        final long stamp = this.stageLock.readLock();
        try {
            PackagerStage stage = this.stages.peek();
            if (stage == null) return null;
            return stage.id();
        } finally {
            this.stageLock.unlock(stamp);
        }
    }

    /**
     * Executes the next packager stage. Returns false if there are no more stages to run.
     */
    public boolean executeStage() throws PackagerException {
        this.execLock.lock();
        try {
            long stamp = this.stageLock.readLock();
            try {
                PackagerStage stage = this.stages.peek();
                if (stage == null) return false;

                this.logger.log(Level.FINE, "executing stage: " + stage.id());
                stage.execute(this, this.state);

                stamp = this.stageLock.tryConvertToWriteLock(stamp);
                this.stages.poll();
                return true;
            } finally {
                this.stageLock.unlock(stamp);
            }
        } finally {
            this.execLock.unlock();
        }
    }

    //

    @Override
    public void close() throws PackagerException {
        try {
            if (!this.workingDir.exists()) return;
            Files.walkFileTree(this.workingDir.toPath(), new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new PackagerIOException("Failed to delete working directory", e);
        }
    }

}
