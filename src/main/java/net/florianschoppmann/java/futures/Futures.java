package net.florianschoppmann.java.futures;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.Nullable;

/**
 * This class consists exclusively of static methods that operate on {@link CompletionStage} and
 * {@link CompletableFuture} instances.
 */
public final class Futures {
    private Futures() {
        throw new AssertionError(String.format("No %s instances for you!", getClass().getName()));
    }

    /**
     * Returns a new {@link CompletableFuture} that will normally be completed with the list of results of all given
     * completion stages. The future will fail early if any of the given stages are completed exceptionally.
     *
     * <p>If any of the given completion stages completes exceptionally, then the returned future is completed with a
     * {@link CompletionException}. This is either the first encountered exception (in the order of the given
     * {@link Iterable}) if that is a {@link CompletionException}, or otherwise a fresh {@link CompletionException}
     * holding the first encountered exception as cause.
     *
     * <p>Unlike {@link #collect(Iterable)}, the returned future may be completed before all completion stages are
     * completed. Specifically, in case of exceptional completion of the returned future, some of the given completion
     * stages may not yet be completed.
     *
     * <p>The order of the given completion stages is preserved in the result list.
     *
     * @param completionStages completion stages supplying the elements of the list that the returned future will be
     *     completed with
     * @param <T> the element type of the list that the new future will be completed with
     * @return the new future
     */
    public static <T> CompletableFuture<List<T>> shortCircuitCollect(
            Iterable<? extends CompletionStage<? extends T>> completionStages) {
        Objects.requireNonNull(completionStages);

        CompletableFuture<List<T>> listFuture = CompletableFuture.completedFuture((List<T>) new ArrayList<T>());
        for (CompletionStage<? extends T> completionStage: completionStages) {
            CompletableFuture<List<T>> newListFuture = new CompletableFuture<>();
            listFuture.whenComplete((list, listThrowable) -> {
                assert list != null || listThrowable != null;
                if (listThrowable != null) {
                    newListFuture.completeExceptionally(listThrowable);
                } else {
                    completionStage.whenComplete((element, throwable) -> {
                        if (throwable != null) {
                            newListFuture.completeExceptionally(encodeException(throwable));
                        } else {
                            list.add(element);
                            newListFuture.complete(list);
                        }
                    });
                }
            });
            listFuture = newListFuture;
        }
        return listFuture;
    }

    /**
     * Returns a new {@link CompletableFuture} that will normally be completed with a list of results of all given
     * completion stages.
     *
     * <p>If any of the given completion stages completes exceptionally, then the returned future is completed with a
     * {@link CompletionException}. This is either the first encountered exception (in the order of the given
     * {@link Iterable}) if that is a {@link CompletionException}, or otherwise a fresh {@link CompletionException}
     * holding the first encountered exception as cause.
     *
     * <p>Similar to {@link CompletableFuture#allOf(CompletableFuture[])}, this method guarantees that the returned
     * future will only be completed <em>after</em> all given completion stages have completed.
     *
     * <p>The order of the given completion stages is preserved in the result list.
     *
     * @param completionStages completion stages supplying the elements of the list that the returned future will be
     *     completed with
     * @param <T> the element type of the list that the new future will be completed with
     * @return future that will be completed with a list of the values that the given futures will be completed with
     */
    public static <T> CompletableFuture<List<T>> collect(
            Iterable<? extends CompletionStage<? extends T>> completionStages) {
        Objects.requireNonNull(completionStages);

        CompletableFuture<List<T>> listFuture = CompletableFuture.completedFuture((List<T>) new ArrayList<T>());
        for (CompletionStage<? extends T> completionStage: completionStages) {
            CompletableFuture<List<T>> newListFuture = new CompletableFuture<>();
            listFuture.whenComplete((list, listThrowable) -> {
                assert list != null || listThrowable != null;
                completionStage.whenComplete((element, throwable) -> {
                    if (throwable != null) {
                        if (listThrowable != null) {
                            newListFuture.completeExceptionally(listThrowable);
                        } else {
                            newListFuture.completeExceptionally(encodeException(throwable));
                        }
                    } else {
                        if (listThrowable != null) {
                            newListFuture.completeExceptionally(listThrowable);
                        } else {
                            list.add(element);
                            newListFuture.complete(list);
                        }
                    }
                });
            });
            listFuture = newListFuture;
        }
        return listFuture;
    }

    /**
     * Returns a new {@link CompletableFuture} that is already completed with the value obtained by calling the given
     * {@link CheckedSupplier}.
     *
     * <p>While calling this method is normally equivalent to calling {@link CompletableFuture#completedFuture(Object)}
     * with the value returned by the given supplier, this method can be helpful when chaining multiple futures, and the
     * exception handling for the initial computation stage is supposed to be identical with the one in later stages.
     * Just like {@link CompletableFuture#supplyAsync(Supplier, Executor)}, the returned future will be completed
     * with a {@link CompletionException} if the given supplier throws.
     *
     * @param supplier a function returning the value to be used to complete the returned {@link CompletableFuture}
     * @param <T> the function's return type
     * @return the new future
     */
    public static <T> CompletableFuture<T> supply(CheckedSupplier<T> supplier) {
        return supplyAsync(supplier, Runnable::run);
    }

    private static CompletionException rewrap(CompletionException exception) {
        Throwable cause = exception.getCause();
        return cause instanceof WrappedException
            ? new CompletionException(cause.getCause())
            : exception;
    }

    /**
     * Returns a new {@link CompletableFuture} that is asynchronously completed by a task running in the given executor
     * with the value obtained by calling the given {@link CheckedSupplier}.
     *
     * <p>Just like {@link CompletableFuture#supplyAsync(Supplier, Executor)}, the returned future will be completed
     * with a {@link CompletionException} if the given supplier throws.
     *
     * @param supplier a function returning the value to be used to complete the returned {@link CompletableFuture}
     * @param executor the executor to use for asynchronous execution
     * @param <T> the function's return type
     * @return the new future
     *
     * @see CompletableFuture#supplyAsync(Supplier, Executor)
     */
    public static <T> CompletableFuture<T> supplyAsync(CheckedSupplier<T> supplier, Executor executor) {
        CompletionStage<T> completionStage = CompletableFuture.supplyAsync(() -> {
            try {
                return supplier.checkedGet();
            } catch (Exception exception) {
                throw new WrappedException(exception);
            }
        }, executor);

        CompletableFuture<T> future = new CompletableFuture<>();
        completionStage.whenComplete((result, failure) -> {
            if (failure != null) {
                assert failure instanceof CompletionException;
                future.completeExceptionally(rewrap((CompletionException) failure));
            } else {
                future.complete(result);
            }
        });
        return future;
    }

    /**
     * Returns a new {@link CompletableFuture} that is already exceptionally completed with the given {@link Throwable}.
     *
     * @param throwable the failure
     * @param <T> type of the value (if the future had completed normally)
     * @return the exceptionally completed future
     */
    public static <T> CompletableFuture<T> completedExceptionally(Throwable throwable) {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(throwable);
        return future;
    }

    /**
     * When the given {@link CompletionStage} completes either normally or exceptionally, completes the given
     * {@link CompletableFuture} with its result.
     *
     * @param completableFuture future that will be completed with the result of {@code completionStage}
     * @param completionStage completion stage providing the result for {@code completableFuture}
     * @param <T> type of the result value if the stage completes normally
     */
    public static <T> void completeWith(CompletableFuture<T> completableFuture, CompletionStage<T> completionStage) {
        completionStage.whenComplete((result, throwable) -> {
            if (throwable != null) {
                completableFuture.completeExceptionally(throwable);
            } else {
                completableFuture.complete(result);
            }
        });
    }

    /**
     * Returns a new {@link CompletableFuture} that will be completed in the same way as the given stage. However, in
     * case of exceptional completion with a {@link CompletionException}, the returned future will be exceptionally
     * completed with the <em>cause</em> held in the {@link CompletionException}.
     *
     * @param completionStage the completion stage that may be completed with a {@link CompletionException}
     * @param <T> the type of result of the completion stage
     * @return the new future
     */
    public static <T> CompletableFuture<T> unwrapCompletionException(CompletionStage<T> completionStage) {
        CompletableFuture<T> future = new CompletableFuture<>();
        completionStage.whenComplete((value, failure) -> {
            if (failure instanceof CompletionException) {
                @Nullable Throwable cause = failure.getCause();
                if (cause != null) {
                    future.completeExceptionally(cause);
                } else {
                    future.completeExceptionally(failure);
                }
            } else if (failure != null) {
                future.completeExceptionally(failure);
            } else {
                future.complete(value);
            }
        });
        return future;
    }

    private static final class WrappedException extends RuntimeException {
        private static final long serialVersionUID = -3891858012985643515L;

        /**
         * Constructs a new wrapped exception with the specified cause.
         *
         * @param cause the cause (which is saved for later retrieval by the {@link #getCause()} method)
         */
        private WrappedException(Throwable cause) {
            super(cause);
        }
    }

    /**
     * Returns a new {@link CompletableFuture} that, when the given stage completes normally, is executed with that
     * stage's result as the argument to the supplied function. See the {@link CompletionStage} documentation for rules
     * covering exceptional completion.
     *
     * <p>If {@code function} was a {@link Function} instance identical to {@code checkedFunction}, except for not
     * throwing checked exceptions, then this method is equivalent to {@code completionStage.thenApply(function)}.
     *
     * @param completionStage the completion stage providing the result for the given function
     * @param checkedFunction the function to use to compute the value of the returned {@link CompletableFuture}
     * @param <T> the function's argument type
     * @param <U> the function's return type
     * @return the new future
     */
    public static <T, U> CompletableFuture<U> thenApply(CompletionStage<T> completionStage,
            CheckedFunction<? super T, ? extends U> checkedFunction) {
        // Oddly, javac 1.8.0_73 fails with the type inference, so we need to do an explicit cast.
        return thenApplyInternal(
            (Function<BiConsumer<? super T, ? super Throwable>, CompletionStage<T>>) completionStage::whenComplete,
            checkedFunction
        );
    }

    /**
     * Returns a new {@link CompletableFuture} that, when the given stage completes normally, is executed using the
     * supplied Executor, with the given stage's result as the argument to the supplied function. See the
     * {@link CompletionStage} documentation for rules covering exceptional completion.
     *
     * <p>If {@code function} was a {@link Function} instance identical to {@code checkedFunction}, except for not
     * throwing checked exceptions, then this method is equivalent to
     * {@code completionStage.thenApplyAsync(function, executor)}.
     *
     * @param completionStage the completion stage providing the result for the given function
     * @param checkedFunction the function to use to compute the value of the returned {@link CompletableFuture}
     * @param executor the executor to use for asynchronous execution
     * @param <T> the function's argument type
     * @param <U> the function's return type
     * @return the new future
     */
    public static <T, U> CompletableFuture<U> thenApplyAsync(CompletionStage<T> completionStage,
            CheckedFunction<? super T, ? extends U> checkedFunction, Executor executor) {
        return thenApplyInternal(action -> completionStage.whenCompleteAsync(action, executor), checkedFunction);
    }

    private static <T, U> CompletableFuture<U> thenApplyInternal(
            Function<BiConsumer<? super T, ? super Throwable>, CompletionStage<T>> whenCompleteFunction,
            CheckedFunction<? super T, ? extends U> checkedFunction) {
        return transformCompletionStage(
            whenCompleteFunction,
            (result, failure, future) -> {
                if (failure == null) {
                    try {
                        future.complete(checkedFunction.checkedApply(result));
                    } catch (Exception exception) {
                        throw new WrappedException(exception);
                    }
                }
            }
        );
    }

    /**
     * Returns a new {@link CompletableFuture} that, when the given stage completes normally, is executed with that
     * stage as the argument to the supplied function. See the {@link CompletionStage} documentation for rules covering
     * exceptional completion.
     *
     * <p>If {@code function} was a {@link Function} instance identical to {@code checkedFunction}, except for not
     * throwing checked exceptions, then this method is equivalent to {@code completionStage.thenCompose(function)}.
     *
     * @param completionStage the completion stage providing the result for the given function
     * @param checkedFunction the function returning a new {@link CompletionStage}
     * @param <T> the function's argument type
     * @param <U> the type of the returned {@link CompletionStage}'s result
     * @return the new future
     */
    public static <T, U> CompletableFuture<U> thenCompose(CompletionStage<T> completionStage,
            CheckedFunction<? super T, ? extends CompletionStage<U>> checkedFunction) {
        // Oddly, javac 1.8.0_73 fails with the type inference, so we need to do an explicit cast.
        return thenComposeInternal(
            (Function<BiConsumer<? super T, ? super Throwable>, CompletionStage<T>>) completionStage::whenComplete,
            checkedFunction
        );
    }

    /**
     * Returns a new {@link CompletableFuture} that, when the given stage completes normally, is executed using the
     * supplied {@link Executor}, with the given stage's result as the argument to the supplied function. See the
     * {@link CompletionStage} documentation for rules covering exceptional completion.
     *
     * <p>If {@code function} was a {@link Function} instance identical to {@code checkedFunction}, except for not
     * throwing checked exceptions, then this method is equivalent to
     * {@code completionStage.thenComposeAsync(function, executor)}.
     *
     * @param completionStage the completion stage providing the result for the given function
     * @param checkedFunction the function returning a new {@link CompletionStage}
     * @param executor the executor to use for asynchronous execution
     * @param <T> the function's argument type
     * @param <U> the type of the returned {@link CompletionStage}'s result
     * @return the new future
     */
    public static <T, U> CompletableFuture<U> thenComposeAsync(CompletionStage<T> completionStage,
            CheckedFunction<? super T, ? extends CompletionStage<U>> checkedFunction, Executor executor) {
        return thenComposeInternal(action -> completionStage.whenCompleteAsync(action, executor), checkedFunction);
    }

    /**
     * Returns a {@link CompletionException} containing the given {@link Throwable} as cause, unless the given
     * {@link Throwable} is a {@link CompletionException}, in which case it itself is returned.
     *
     * <p>This function exists to match the behavior of {@link CompletableFuture}.
     *
     * @param throwable the exception or error
     * @return the completion exception
     */
    private static CompletionException encodeException(Throwable throwable) {
        return throwable instanceof CompletionException
            ? (CompletionException) throwable
            : new CompletionException(throwable);
    }

    /**
     * If the given {@link Throwable} is a {@link CompletionException} with cause, returns this. Otherwise, returns the
     * given throwable.
     *
     * <p>See also {@link #encodeException(Throwable)}.
     *
     * @param throwable the exception or error
     * @return the cause
     */
    static Throwable decode(Throwable throwable) {
        if (throwable instanceof CompletionException) {
            @Nullable Throwable cause = throwable.getCause();
            if (cause != null) {
                return cause;
            }
        }
        return throwable;
    }

    private static <T, U> CompletableFuture<U> thenComposeInternal(
            Function<BiConsumer<? super T, ? super Throwable>, CompletionStage<T>> whenCompleteStage,
            CheckedFunction<? super T, ? extends CompletionStage<U>> checkedFunction) {
        return transformCompletionStage(
            whenCompleteStage,
            (result, failure, future) -> {
                if (failure == null) {
                    try {
                        CompletionStage<U> nextStage = Objects.requireNonNull(checkedFunction.checkedApply(result));
                        nextStage.whenComplete((nextResult, nextFailure) -> {
                            if (nextFailure != null) {
                                future.completeExceptionally(encodeException(nextFailure));
                            } else {
                                future.complete(nextResult);
                            }
                        });
                    } catch (Exception exception) {
                        throw new WrappedException(exception);
                    }
                }
            }
        );
    }

    /**
     * Returns a new {@link CompletableFuture} with the same result or exception as the given stage, that executes the
     * given action when the given stage completes.
     *
     * <p>When the given stage is complete, the given action is invoked with the result (or {@code null} if none) and
     * the exception (or {@code null} if none) of the given stage as arguments. The returned future is completed when
     * the action returns. If the supplied action itself encounters an exception, then the returned future exceptionally
     * completes with this exception unless the given stage also completed exceptionally.
     *
     * @param completionStage the completion stage providing the result or exception for the given action
     * @param action the action to perform
     * @param <T> the action's first argument type
     * @return the new future
     */
    public static <T> CompletableFuture<T> whenComplete(CompletionStage<T> completionStage,
            CheckedBiConsumer<? super T, ? super Throwable> action) {
        // Oddly, javac 1.8.0_73 fails with the type inference, so we need to do an explicit cast.
        return whenCompleteInternal(
            (Function<BiConsumer<? super T, ? super Throwable>, CompletionStage<T>>) completionStage::whenComplete,
            action
        );
    }

    /**
     * Returns a new {@link CompletableFuture} with the same result or exception as the given stage, that executes the
     * given action using the supplied {@link Executor} when the given stage completes.
     *
     * <p>When the given stage is complete, the given action is invoked with the result (or {@code null} if none) and
     * the exception (or {@code null} if none) of the given stage as arguments. The returned future is completed when
     * the action returns. If the supplied action itself encounters an exception, then the returned future exceptionally
     * completes with this exception unless the given stage also completed exceptionally.
     *
     * @param completionStage the completion stage providing the result or exception for the given action
     * @param executor the executor to use for asynchronous execution
     * @param action the action to perform
     * @param <T> the action's first argument type
     * @return the new future
     */
    public static <T> CompletableFuture<T> whenCompleteAsync(CompletionStage<T> completionStage,
            CheckedBiConsumer<? super T, ? super Throwable> action, Executor executor) {
        return whenCompleteInternal(actionArg -> completionStage.whenCompleteAsync(actionArg, executor), action);
    }

    private static <T> CompletableFuture<T> whenCompleteInternal(
            Function<BiConsumer<? super T, ? super Throwable>, CompletionStage<T>> whenCompleteStage,
            CheckedBiConsumer<? super T, ? super Throwable> action) {
        return transformCompletionStage(
            whenCompleteStage,
            (result, failure, future) -> {
                try {
                    action.checkedAccept(result, failure);
                    if (failure == null) {
                        future.complete(result);
                    }
                } catch (Exception exception) {
                    throw new WrappedException(exception);
                }
            }
        );
    }

    /**
     * Returns a new {@link CompletableFuture} that is normally completed with the same result as the given stage. If
     * the given stage completed exceptionally, the given function is called with the exception as argument and the
     * returned future is completed exceptionally with the result.
     *
     * <p>The exception returned by {@code function} is <em>not</em> wrapped in a {@link CompletionException}. However,
     * if an exception <em>E</em> occurs in the given function, the returned future will be completed exceptionally with
     * a {@link CompletionException} containing <em>E</em> as cause.
     *
     * @param completionStage the completion stage providing the exception for the given function
     * @param function the exception-translation function
     * @param <T> the type of the completion stage
     * @return the new future
     */
    public static <T> CompletableFuture<T> translateException(CompletionStage<T> completionStage,
            Function<Throwable, Throwable> function) {
        CompletableFuture<T> future = new CompletableFuture<>();
        completionStage
            .handle((result, failure) -> {
                if (failure != null) {
                    future.completeExceptionally(Objects.requireNonNull(function.apply(failure)));
                } else {
                    future.complete(result);
                }
                return null;
            })
            .exceptionally(future::completeExceptionally);
        return future;
    }

    @FunctionalInterface
    interface TriConsumer<T, U, V> {
        /**
         * Performs this operation on the given arguments.
         *
         * @param first the first input argument
         * @param second the second input argument
         * @param third the third input argument
         */
        void accept(@Nullable T first, @Nullable U second, @Nullable V third);
    }

    /**
     * Returns a new {@link CompletableFuture} that is normally completed by the given {@code resultConsumer}, and that
     * is exceptionally completed if a failure occurs.
     *
     * <p>This method factors out common code in order to implement <em>thenApply</em>, <em>thenCompose</em>, etc.
     * methods that that have corresponding methods in {@link CompletionStage}. However, the static methods in this
     * class allow transformations that throw checked exceptions.
     */
    static <T, U> CompletableFuture<U> transformCompletionStage(
        Function<BiConsumer<? super T, ? super Throwable>, CompletionStage<T>> whenCompleteFunction,
        TriConsumer<? super T, Throwable, CompletableFuture<U>> resultConsumer
    ) {
        CompletableFuture<U> future = new CompletableFuture<>();
        whenCompleteFunction
            .apply((result, failure) -> resultConsumer.accept(result, failure, future))
            .exceptionally(exception -> {
                // Assuming that whenCompleteFunction is indeed method whenComplete or whenCompleteAsync, then we expect
                // a CompletionException here. However, we are extra cautious here, because a failed assertion or a
                // ClassCastException here could potentially create a debugging nightmare (it would be caught in
                // CompletableFuture#exceptionally() and then ignored).
                future.completeExceptionally(
                    exception instanceof CompletionException
                        ? rewrap((CompletionException) exception)
                        : exception
                );
                // Result of the new completion stage created by exceptionally() is ignored
                return null;
            });
        return future;
    }

    /**
     * Returns a new {@link CompletableFuture} that, when the given stage completes normally, is executed using the
     * supplied Executor, with the given stage's result as the argument to the supplied function. See the
     * {@link CompletionStage} documentation for rules covering exceptional completion.
     *
     * <p>This method behaves like {@link #thenApplyAsync(CompletionStage, CheckedFunction, Executor)}, but it ensures
     * that the resource passed to {@code function} is properly closed, even if an exception occurs at any stage. This
     * method may thus be regarded as an asynchronous try-with-resources implementation (with just one resource: The
     * {@code R} instance that {@code resourceStage} will is completed with).
     *
     * <p>Similar to try-with-resources, if an exception occurs after the {@link AutoCloseable} resource has been
     * opened, then any further exception while automatically closing the resource will be added as suppressed
     * exception. Suppressed exceptions are added to the original exception, not the {@link CompletionException} that
     * the returned future would be completed with and that contains the original exception as cause.
     *
     * @param resourceStage completion stage that will be completed with an {@link AutoCloseable}
     * @param function function computing the result for the new future
     * @param executor the executor to use for asynchronous execution
     * @param <R> the type of the resource returned by the given completion stage
     * @param <T> the type of the value returned by the given function
     * @return the new future
     */
    public static <R extends AutoCloseable, T> CompletableFuture<T> thenApplyWithResourceAsync(
            CompletionStage<R> resourceStage, CheckedFunction<? super R, ? extends T> function,
            Executor executor) {
        return thenApplyAsync(resourceStage, resource -> {
            try (R ignored = resource) {
                return function.checkedApply(resource);
            }
        }, executor);
    }

    /**
     * Returns a new {@link CompletableFuture} that, when the given stage completes normally, is executed with that
     * stage as the argument to the supplied function. See the {@link CompletionStage} documentation for rules covering
     * exceptional completion.
     *
     * <p>This method behaves like {@link #thenCompose(CompletionStage, CheckedFunction)}, but it ensures that the
     * resource passed to {@code function} is properly closed, even if an exception occurs at any stage. This method may
     * thus be regarded as an asynchronous try-with-resources implementation (with just one resource: The {@code R}
     * instance that {@code resourceStage} will is completed with).
     *
     * <p>Similar to try-with-resources, if an exception occurs after the {@link AutoCloseable} resource has been
     * opened, then any further exception while automatically closing the resource will be added as suppressed
     * exception. Suppressed exceptions are added to the original exception, not the {@link CompletionException} that
     * the returned future would be completed with and that contains the original exception as cause.
     *
     * @param resourceStage completion stage that will be completed with an {@link AutoCloseable}
     * @param function Function returning a completion stage that will compute the result for the new future.
     * @param <R> the type of the resource returned by the given completion stage
     * @param <T> the result type of the future returned by the given function
     * @return the new future
     */
    public static <R extends AutoCloseable, T> CompletableFuture<T> thenComposeWithResource(
            CompletionStage<R> resourceStage,
            CheckedFunction<? super R, ? extends CompletionStage<T>> function) {
        return thenCompose(resourceStage, resource -> {
            try (CloseableWrapper closeableWrapper = new CloseableWrapper(resource)) {
                CompletionStage<T> nextStage = Objects.requireNonNull(function.checkedApply(resource));
                closeableWrapper.ignoreClose = true;
                return whenComplete(nextStage, (result, failure) -> {
                    // According to JLS8 §14.20.3.1, if a primary exception has occurred, then whatever exception occurs
                    // during close() is added as suppressed exception, even in case of a Throwable that is not an
                    // Exception.
                    if (resource != null) {
                        if (failure != null) {
                            try {
                                resource.close();
                            } catch (Throwable throwable) {
                                decode(failure).addSuppressed(throwable);
                            }
                        } else {
                            resource.close();
                        }
                    }
                });
            }
        });
    }

    private static final class CloseableWrapper implements AutoCloseable {
        @Nullable private final AutoCloseable closeable;

        /**
         * No need to synchronize access to this field. The read from {@link #close()} here will always happen either
         * before or after it is written to, never concurrent to a write in
         * {@link #thenComposeWithResource(CompletionStage, CheckedFunction)}. However, the field has to be volatile
         * for cache coherence.
         */
        private volatile boolean ignoreClose = false;

        private CloseableWrapper(@Nullable AutoCloseable closeable) {
            this.closeable = closeable;
        }

        @Override
        public void close() throws Exception {
            if (closeable != null && !ignoreClose) {
                closeable.close();
            }
        }
    }
}
