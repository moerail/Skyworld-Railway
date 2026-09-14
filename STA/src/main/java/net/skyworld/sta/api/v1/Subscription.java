package net.skyworld.sta.api.v1;

@FunctionalInterface
public interface Subscription extends AutoCloseable {
    @Override
    void close();
}
