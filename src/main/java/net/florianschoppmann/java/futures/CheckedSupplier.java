package net.florianschoppmann.java.futures;

import javax.annotation.Nullable;

/**
 * Represents a supplier of results. The supplier may throw a checked exception.
 *
 * <p>There is no requirement that a new or distinct result be returned each time the supplier is invoked.
 *
 * <p>This method is similar to {@link java.util.function.Supplier}, except that the functional method may throw a
 * checked exception.
 *
 * @param <T> the type of results supplied by this supplier
 *
 * @see java.util.function.Supplier
 */
@FunctionalInterface
public interface CheckedSupplier<T> {
    /**
     * Gets a result.
     *
     * @return a result
     * @throws Exception if unable to supply a value
     */
    @Nullable
    T checkedGet() throws Exception;
}
