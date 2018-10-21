package com.github.kxfeng.logs;

/**
 * Represents a supplier of results.
 * <p>
 * Note: SAM-conversion is not supported in Kotlin interface, so use this java interface.
 */
public interface Supplier<T> {
    /**
     * Gets a result.
     *
     * @return a result
     */
    T get();
}
