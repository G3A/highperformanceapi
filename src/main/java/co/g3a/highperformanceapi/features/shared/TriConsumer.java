package co.g3a.highperformanceapi.features.shared;

@FunctionalInterface
    public interface TriConsumer<T, U, V> {
        void accept(T t, U u, V v);
    }