package net.florianschoppmann.java.futures;

import javax.annotation.Nullable;

/**
 * Represents an operation that accepts two input arguments and returns no result. Unlike most other functional
 * interfaces, {@code CheckedBiConsumer} is expected to operate via side-effects.
 *
 * <p>This is a functional interface whose functional method is {@link #checkedAccept(Object, Object)}. It is similar to
 * {@link java.util.function.BiConsumer}, except that the functional method may throw a checked exception.
 *
 * @param <T> the type of the first argument to the operation
 * @param <U> the type of the second argument to the operation
 *
 * @see java.util.function.BiConsumer
 */
@FunctionalInterface
public interface CheckedBiConsumer<T, U> {
    /**
     * Performs this operation on the given arguments.
     *
     * @param first the first input argument
     * @param second the second input argument
     * @throws Exception if unable to process the input
     */
    void checkedAccept(@Nullable T first, @Nullable U second) throws Exception;
}
