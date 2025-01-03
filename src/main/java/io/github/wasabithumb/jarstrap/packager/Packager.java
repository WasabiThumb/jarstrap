package io.github.wasabithumb.jarstrap.packager;

import io.github.wasabithumb.jarstrap.manifest.ManifestMutator;
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
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * <p>
 *     A {@link io.github.wasabithumb.jarstrap.JARStrap JARStrap} packager. Each packager owns its own working directory
 *     that is cleared when {@link #close()} is called. The packager will execute the required stages in turn.
 * </p>
 * <p>
 *     Each packager is comprised of {@link PackagerStage stages}. Every stage must execute in turn in order to produce
 *     a valid artifact. For a simple way to do this, use {@link #execute()}.
 * </p>
 * <p>
 *     The final executable will be copied to the {@link #setOutputDir(File) output directory} with the given
 *     {@link #setOutputName(String) output name}.
 *     This file can be accessed at {@link #getOutputFile()} and will have the appropriate
 *     {@link #getExtension() extension} for the current platform.
 * </p>
 */
public class Packager implements AutoCloseable {

    public static final String DEFAULT_INSTALL_PROMPT = "This application requires Java %d or greater, which could not be found. Install now? The download may take a few moments.";

    private final File workingDir;
    private final Queue<PackagerStage> stages;
    private final StampedLock stageLock;
    private final Lock execLock;
    private final StampedLock attrLock;
    private final Logger logger;
    private final PackagerState state;
    private final ManifestMutator manifest;

    private PackagerArch arch = PackagerArch.X86_64;
    private boolean release = false;
    private String appName = null;
    private int minJavaVersion = 8;
    private int preferredJavaVersion = 21;
    private String launchFlags = "";
    private String installPrompt = DEFAULT_INSTALL_PROMPT;
    private File source = null;
    private File outputDir = null;
    private String outputName = null;
    private boolean autoInstall = false;
    private boolean attributionEnabled = true;

    public Packager(@NotNull File workingDir, @NotNull Logger logger) {
        this.workingDir = workingDir;
        this.stages = new LinkedList<>();
        this.setupStages(this.stages);
        this.stageLock = new StampedLock();
        this.execLock = new ReentrantLock();
        this.attrLock = new StampedLock();
        this.logger = logger;
        this.state = new PackagerState();
        this.manifest = new ManifestMutator();
    }

    protected void setupStages(@NotNull Queue<PackagerStage> stages) {
        stages.add(new PackagerInitStage());
        stages.add(new PackagerInjectStage());
        stages.add(new PackagerManifestStage());
        stages.add(new PackagerVarsStage());
        if (JOSDirs.platform().equals("windows")) stages.add(new PackagerMinGWStage());
        stages.add(new PackagerCmakeStage());
        stages.add(new PackagerMakeStage());
        stages.add(new PackagerExportStage());
    }

    /**
     * @return The temporary working directory for this packager
     */
    public @NotNull File getWorkingDir() {
        return this.workingDir;
    }

    /**
     * The file that will receive the executable during the last stage
     */
    public @NotNull File getOutputFile() {
        return new File(this.getOutputDir(), this.getOutputName() + this.getExtension());
    }

    /**
     * The file extension for executables, determined by the current platform.
     * {@code .exe} for Windows, {@code (empty string)} for UNIX.
     */
    public @NotNull String getExtension() {
        if (JOSDirs.platform().equals("windows")) {
            return ".exe";
        }
        return "";
    }

    /**
     * The logger used by this packager
     */
    public @NotNull Logger logger() {
        return this.logger;
    }

    //

    /**
     * The architecture to build for, {@link PackagerArch#X86_64 X86_64} by default.
     */
    public @NotNull PackagerArch getArch() {
        final long stamp = this.attrLock.readLock();
        try {
            return this.arch;
        } finally {
            this.attrLock.unlock(stamp);
        }
    }

    /**
     * @see #getArch()
     */
    public void setArch(@NotNull PackagerArch arch) {
        final long stamp = this.attrLock.writeLock();
        try {
            this.arch = arch;
        } finally {
            this.attrLock.unlock(stamp);
        }
    }

    /**
     * True if building a release binary (smaller size, less output, hard to debug the bootstrap)
     */
    public boolean isRelease() {
        final long stamp = this.attrLock.readLock();
        try {
            return this.release;
        } finally {
            this.attrLock.unlock(stamp);
        }
    }

    /**
     * @see #isRelease()
     */
    public void setRelease(boolean release) {
        final long stamp = this.attrLock.writeLock();
        try {
            this.release = release;
        } finally {
            this.attrLock.unlock(stamp);
        }
    }

    /**
     * The app name, or {@code JARStrap} if none set. When the app name is equal to {@code JARStrap}
     * (via hash comparison), no app name is reported on startup.
     */
    public @NotNull String getAppName() {
        final long stamp = this.attrLock.readLock();
        try {
            if (this.appName == null) return "JARStrap";
            return this.appName;
        } finally {
            this.attrLock.unlock(stamp);
        }
    }

    /**
     * @see #getAppName()
     */
    public void setAppName(@Nullable String appName) {
        final long stamp = this.attrLock.writeLock();
        try {
            this.appName = appName;
        } finally {
            this.attrLock.unlock(stamp);
        }
    }

    /**
     * The minimum Java version; the {@link #getPreferredJavaVersion() preferred Java version} will be
     * installed if no Java on the system is at least this version
     */
    public int getMinJavaVersion() {
        final long stamp = this.attrLock.readLock();
        try {
            return this.minJavaVersion;
        } finally {
            this.attrLock.unlock(stamp);
        }
    }

    /**
     * @see #getMinJavaVersion()
     */
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

    /**
     * The preferred Java version; installed when the {@link #getMinJavaVersion() minimum Java version} is not present
     * on the system.
     */
    public int getPreferredJavaVersion() {
        final long stamp = this.attrLock.readLock();
        try {
            return this.preferredJavaVersion;
        } finally {
            this.attrLock.unlock(stamp);
        }
    }

    /**
     * @see #getPreferredJavaVersion()
     */
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

    /**
     * Additional flags to set when launching the JAR, placed after the {@code -jar} switch.
     */
    public @NotNull String getLaunchFlags() {
        final long stamp = this.attrLock.readLock();
        try {
            return this.launchFlags;
        } finally {
            this.attrLock.unlock(stamp);
        }
    }

    /**
     * @see #getLaunchFlags()
     */
    public void setLaunchFlags(@NotNull String flags) {
        final long stamp = this.attrLock.writeLock();
        try {
            this.launchFlags = Objects.requireNonNull(flags);
        } finally {
            this.attrLock.unlock(stamp);
        }
    }

    /**
     * <p>
     *     The prompt to show when the user must install Java to proceed. The {@code %d} template can be used up to 1
     *     time and will be replaced with the target Java version. To write a literal percent char,
     *     instead write {@code %%}.
     * </p>
     * <p>
     *     The default prompt can be found at {@link #DEFAULT_INSTALL_PROMPT}.
     * </p>
     */
    public @NotNull String getInstallPrompt() {
        final long stamp = this.attrLock.readLock();
        try {
            return this.installPrompt;
        } finally {
            this.attrLock.unlock(stamp);
        }
    }

    /**
     * @see #getInstallPrompt()
     * @throws IllegalArgumentException The given prompt has more than one occurrence of {@code %d}, or has a {@code %}
     * followed by the end of the string or a char that is not {@code %} or {@code d}.
     */
    public void setInstallPrompt(@Nullable String prompt) throws IllegalArgumentException {
        if (prompt == null) {
            prompt = "";
        } else {
            int len = prompt.length();
            int counter = 0;
            char c;
            for (int i=0; i < len; i++) {
                c = prompt.charAt(i);
                if (c != '%') continue;
                if (i == (len - 1))
                    throw new IllegalArgumentException("Starting escape '%' may not be last char in string");
                c = prompt.charAt(++i);
                if (c == '%') continue;
                if (c != 'd')
                    throw new IllegalArgumentException("Escape char '%' must be followed by '%' or 'd'");
                if (counter++ != 0)
                    throw new IllegalArgumentException("String may only have up to 1 \"%d\" template symbol");
            }
        }

        final long stamp = this.attrLock.writeLock();
        try {
            this.installPrompt = prompt;
        } finally {
            this.attrLock.unlock(stamp);
        }
    }

    /**
     * The JAR file to wrap into an executable. If none is set, a sample JAR included with JARStrap is used. This
     * sample program is a Java 5 stub which reports the JRE version in use; e.g. {@code Hello from Java 21.0.3+9}.
     */
    public @NotNull File getSource() {
        final long stamp = this.attrLock.readLock();
        try {
            if (this.source == null) return new File(new File(this.workingDir, "archive"), "sample.jar");
            return this.source;
        } finally {
            this.attrLock.unlock(stamp);
        }
    }

    /**
     * @see #getSource()
     */
    public void setSource(@Nullable File source) {
        final long stamp = this.attrLock.writeLock();
        try {
            this.source = source;
        } finally {
            this.attrLock.unlock(stamp);
        }
    }

    /**
     * The directory to copy the final executable into. If not set, returns the current working directory.
     */
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

    /**
     * @see #getOutputDir()
     */
    public void setOutputDir(@Nullable File outputDir) {
        final long stamp = this.attrLock.writeLock();
        try {
            this.outputDir = outputDir;
        } finally {
            this.attrLock.unlock(stamp);
        }
    }

    /**
     * The name to use for the final executable. If not set, this returns a variation of the
     * {@link #getAppName() app name}. Specifically, it will be a lowercase version with all whitespace
     * replaced with {@code _}.
     */
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

    /**
     * @see #getOutputName()
     */
    public void setOutputName(@Nullable String outputName) {
        final long stamp = this.attrLock.writeLock();
        try {
            this.outputName = outputName;
        } finally {
            this.attrLock.unlock(stamp);
        }
    }

    /**
     * If true, certain build components may be auto-installed. Currently only used for MinGW on Windows hosts.
     */
    public boolean isAutoInstall() {
        final long stamp = this.attrLock.readLock();
        try {
            return this.autoInstall;
        } finally {
            this.attrLock.unlock(stamp);
        }
    }

    /**
     * @see #isAutoInstall()
     */
    public void setAutoInstall(boolean autoInstall) {
        final long stamp = this.attrLock.writeLock();
        try {
            this.autoInstall = autoInstall;
        } finally {
            this.attrLock.unlock(stamp);
        }
    }

    /**
     * If true, the command-line output of the application will include info about JARStrap. If attribution is disabled,
     * the application distributor should propagate the license information in another way.
     */
    public boolean isAttributionEnabled() {
        final long stamp = this.attrLock.readLock();
        try {
            return this.attributionEnabled;
        } finally {
            this.attrLock.unlock(stamp);
        }
    }

    /**
     * @see #isAttributionEnabled()
     */
    public void setAttributionEnabled(boolean attributionEnabled) {
        final long stamp = this.attrLock.writeLock();
        try {
            this.attributionEnabled = attributionEnabled;
        } finally {
            this.attrLock.unlock(stamp);
        }
    }

    /**
     * Returns an object that can be used to add/remove manifest entries to the source JAR before bootstrapping.
     * @since 0.2.0
     */
    public @NotNull ManifestMutator getManifest() {
        return this.manifest;
    }

    /**
     * Configures the manifest mutator. Alias for {@code arg1.accept(getManifest())}.
     * @since 0.2.0
     * @see #getManifest()
     */
    public void manifest(@NotNull Consumer<ManifestMutator> configure) {
        configure.accept(this.manifest);
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
