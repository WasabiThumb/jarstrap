package io.github.wasabithumb.jarstrap.manifest;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.AbstractCollection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class ManifestMutator extends AbstractCollection<ManifestOperation> {

    private final List<ManifestOperation> backing = new LinkedList<>();

    //

    @Override
    public @NotNull Iterator<ManifestOperation> iterator() {
        return this.backing.iterator();
    }

    @Override
    public int size() {
        return this.backing.size();
    }

    @Override
    public boolean add(@NotNull ManifestOperation op) {
        this.backing.add(op);
        return true;
    }

    //

    @Contract("_, _ -> this")
    public @NotNull ManifestMutator put(@NotNull String key, @NotNull String value) {
        this.backing.add(ManifestOperation.put(key, value));
        return this;
    }

    @Contract("_ -> this")
    public @NotNull ManifestMutator remove(@NotNull String key) {
        this.backing.add(ManifestOperation.remove(key));
        return this;
    }

    @ApiStatus.Internal
    public void apply(@NotNull ManifestFile file) {
        for (ManifestOperation op : this.backing) {
            op.apply(file);
        }
    }

}
