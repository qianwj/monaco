package cn.elvis.monaco.core.port;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Ordered collection of mutations to be committed atomically.
 */
public final class MutationBatch {

    private final List<Mutation> mutations;

    private MutationBatch(List<Mutation> mutations) {
        this.mutations = List.copyOf(mutations);
    }

    public static MutationBatch of(List<Mutation> mutations) {
        return new MutationBatch(mutations);
    }

    public static MutationBatch empty() {
        return new MutationBatch(List.of());
    }

    public List<Mutation> mutations() {
        return mutations;
    }

    public boolean isEmpty() {
        return mutations.isEmpty();
    }

    public int size() {
        return mutations.size();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final List<Mutation> items = new ArrayList<>();

        public Builder add(Mutation mutation) {
            items.add(mutation);
            return this;
        }

        public Builder addAll(List<Mutation> mutations) {
            items.addAll(mutations);
            return this;
        }

        public MutationBatch build() {
            return new MutationBatch(items);
        }
    }
}
