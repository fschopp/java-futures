package net.florianschoppmann.java.futures;

import javax.annotation.Nullable;

/**
 * Represents a function that accepts one argument and produces a result. The function may throw a checked exception.
 *
 * <p>This is a functional interface whose functional method is {@link #checkedApply(Object)}. It is similar to
 * {@link java.util.function.Function}, except that the functional method may throw a checked exception.
 *
 * @param <T> the type of the input to the function
 * @param <R> the type of the result of the function
 *
 * @see java.util.function.Function
 */
@FunctionalInterface
public interface CheckedFunction<T, R> {
    /**
     * Applies this function to the given argument.
     *
     * @param argument the function argument
     * @return the function result
     * @throws Exception if unable to compute a result
     */
    @Nullable
    R checkedApply(@Nullable T argument) throws Exception;
}
