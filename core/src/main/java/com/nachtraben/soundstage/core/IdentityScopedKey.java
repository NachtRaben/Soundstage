package com.nachtraben.soundstage.core;

import java.util.Objects;

public final class IdentityScopedKey<S, L> {
    private final S scope;
    private final L local;

    private IdentityScopedKey(S scope, L local) {
        this.scope = Objects.requireNonNull(scope, "scope");
        this.local = Objects.requireNonNull(local, "local");
    }

    public static <S, L> IdentityScopedKey<S, L> of(S scope, L local) {
        return new IdentityScopedKey<>(scope, local);
    }

    public S scope() {
        return scope;
    }

    public L local() {
        return local;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof IdentityScopedKey<?, ?> key
                && scope == key.scope
                && local.equals(key.local);
    }

    @Override
    public int hashCode() {
        return 31 * System.identityHashCode(scope) + local.hashCode();
    }
}
