package net.florianschoppmann.java.futures;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.Nullable;

public class FuturesTest {
    private static final int DEFAULT_FAILURE = 9;
    private static final int INITIALLY_FAILED = 10;
    private static final int CLOSE_FAILED = 11;
    private static final int APPLY_FAILED = 12;
    private static final int COMPOSE_FAILED = 13;
    private static final int NEW_FUTURE_FAILED = 14;
    private static final int WHEN_COMPLETE_FAILED = 15;
    private static final int DEFAULT_RESULT = 100;

    private static final Executor CALLING_THREAD_EXECUTOR = Runnable::run;

    private static final class ExpectedException extends RuntimeException {
        private static final long serialVersionUID = -7681392744759739602L;

        private final int identifier;

        private ExpectedException(int identifier) {
            super("This is an expected exception.");
            this.identifier = identifier;
        }
    }

    private static final class ExpectedError extends Error {
        private static final long serialVersionUID = -3686404494241921008L;

        private ExpectedError() {
            super("This is an expected error.");
        }
    }

    private static Throwable extractException(CompletableFuture<?> future) {
        Assert.assertTrue(future.isDone());
        AtomicReference<Throwable> failureReference = new AtomicReference<>();
        future.whenComplete((result, failure) -> {
            failureReference.set(failure);
        });
        @Nullable Throwable throwable = failureReference.get();
        Assert.assertNotNull(throwable);
        return throwable;
    }

    private static void assertSuccessfulCollect(
            Function<Iterable<? extends CompletionStage<Integer>>, CompletableFuture<List<Integer>>> function) {
        CompletableFuture<Integer> first = new CompletableFuture<>();
        CompletableFuture<Integer> second = new CompletableFuture<>();
        CompletableFuture<List<Integer>> list = function.apply(Arrays.asList(first, second));
        first.complete(1);
        Assert.assertFalse(list.isDone());
        second.complete(2);
        Assert.assertTrue(list.isDone());
        Assert.assertEquals(list.join(), Arrays.asList(1, 2));
    }

    @Test
    public void shortCircuitCollectEarlyFail() {
        CompletableFuture<Integer> first = new CompletableFuture<>();
        CompletableFuture<Integer> second = new CompletableFuture<>();
        CompletableFuture<List<Integer>> list = Futures.shortCircuitCollect(Arrays.asList(first, second));
        first.completeExceptionally(new ExpectedException(DEFAULT_FAILURE));
        Assert.assertTrue(list.isCompletedExceptionally());
        assertCompletedWithComplEx(list, DEFAULT_FAILURE);
    }

    @Test
    public void shortCircuitCollectSuccess() {
        assertSuccessfulCollect(Futures::shortCircuitCollect);
    }

    @FunctionalInterface
    private interface TriFunction<T, U, V, R> {
        R apply(T first, U second, V third);
    }

    private static <T> CompletableFuture<T> assertNoShortCircuit(
            TriFunction<CompletableFuture<Integer>, CompletableFuture<Integer>, CompletableFuture<Integer>,
                CompletableFuture<T>> function) {
        CompletableFuture<Integer> first = new CompletableFuture<>();
        CompletableFuture<Integer> second = new CompletableFuture<>();
        CompletableFuture<Integer> third = new CompletableFuture<>();
        CompletableFuture<T> combinedFuture = function.apply(first, second, third);
        second.completeExceptionally(new ExpectedException(2));
        first.completeExceptionally(new ExpectedException(1));

        Assert.assertFalse(combinedFuture.isDone());
        third.complete(3);
        Assert.assertTrue(combinedFuture.isCompletedExceptionally());
        assertCompletedWithComplEx(combinedFuture, 1);

        // Ensure that no double-wrapping in a CompletionException happens
        CompletableFuture<Integer> zeroth
            = Futures.completedExceptionally(new CompletionException(new ExpectedException(0)));
        assertCompletedWithComplEx(function.apply(zeroth, first, second), 0);

        return combinedFuture;
    }

    @Test
    public void collectFailure() {
        // Sanity check: CompletableFuture#allOf must not short-circuit, either
        assertNoShortCircuit((first, second, third) -> CompletableFuture.allOf(first, second, third));
        assertNoShortCircuit((first, second, third) -> Futures.collect(Arrays.asList(first, second, third)));
    }

    @Test
    public void collectSuccess() {
        assertSuccessfulCollect(Futures::collect);
    }

    private static Integer throwExpectedError() {
        throw new ExpectedError();
    }

    private static Integer throwExpectedException() {
        throw new ExpectedException(DEFAULT_FAILURE);
    }

    private static void assertSupplyError(CompletableFuture<?> future) {
        Assert.assertTrue(future.isCompletedExceptionally());
        Throwable actualThrowable = extractException(future);
        Assert.assertTrue(actualThrowable instanceof CompletionException);
        Throwable cause = actualThrowable.getCause();
        Assert.assertTrue(cause instanceof ExpectedError);
    }

    /**
     * Verifies that {@link Futures#supply(CheckedSupplier)} behaves identically to
     * {@link CompletableFuture#supplyAsync(Supplier, Executor)} when an {@link Error} is thrown in the supplier.
     */
    @Test
    public void supplyError() {
        assertSupplyError(CompletableFuture.supplyAsync(FuturesTest::throwExpectedError, Runnable::run));
        assertSupplyError(Futures.supply(FuturesTest::throwExpectedError));
    }

    private static void assertIsExpectedException(Throwable throwable, int id, int... suppressedIds) {
        Assert.assertTrue(throwable instanceof ExpectedException);
        Assert.assertEquals(((ExpectedException) throwable).identifier, id);
        Throwable[] suppressedExceptions = throwable.getSuppressed();
        Assert.assertEquals(suppressedExceptions.length, suppressedIds.length);
        for (int i = 0; i < suppressedIds.length; ++i) {
            Assert.assertTrue(suppressedExceptions[i] instanceof ExpectedException);
            Assert.assertEquals(((ExpectedException) suppressedExceptions[i]).identifier, suppressedIds[i]);
        }
    }

    private static void assertCompletedWithExpEx(CompletableFuture<?> future, int id, int... suppressedIds) {
        Assert.assertTrue(future.isCompletedExceptionally());
        assertIsExpectedException(extractException(future), id, suppressedIds);
    }

    private static void assertIsCompletionException(Throwable throwable, int id, int... suppressedIds) {
        Assert.assertTrue(throwable instanceof CompletionException);
        assertIsExpectedException(throwable.getCause(), id, suppressedIds);
    }

    private static void assertCompletedWithComplEx(CompletableFuture<?> future, int id, int... suppressedIds) {
        Assert.assertTrue(future.isCompletedExceptionally());
        assertIsCompletionException(extractException(future), id, suppressedIds);
    }

    @Test
    public void supplyException() {
        assertCompletedWithComplEx(
            CompletableFuture.supplyAsync(FuturesTest::throwExpectedException, CALLING_THREAD_EXECUTOR),
            DEFAULT_FAILURE
        );
        assertCompletedWithComplEx(Futures.supply(FuturesTest::throwExpectedException), DEFAULT_FAILURE);
    }

    @Test
    public void supplySuccess() {
        CompletableFuture<Integer> future = Futures.supply(() -> 24);
        Assert.assertTrue(future.isDone());
        Assert.assertEquals((int) future.join(), 24);
    }

    @Test
    public void completedExceptionally() {
        CompletableFuture<Integer> future = Futures.completedExceptionally(new ExpectedException(DEFAULT_FAILURE));
        assertCompletedWithExpEx(future, DEFAULT_FAILURE);
    }

    @Test
    public void completeWithFailure() {
        CompletableFuture<Integer> completionStage = new CompletableFuture<>();
        CompletableFuture<Integer> future = new CompletableFuture<>();
        Futures.completeWith(future, completionStage);
        Assert.assertFalse(future.isDone());
        completionStage.completeExceptionally(new ExpectedException(DEFAULT_FAILURE));
        assertCompletedWithExpEx(future, DEFAULT_FAILURE);
    }


    @Test
    public void completeWithSuccess() {
        CompletableFuture<Integer> completionStage = new CompletableFuture<>();
        CompletableFuture<Integer> future = new CompletableFuture<>();
        Futures.completeWith(future, completionStage);
        Assert.assertFalse(future.isDone());
        completionStage.complete(24);
        Assert.assertTrue(future.isDone());
        Assert.assertEquals((int) future.join(), 24);
    }

    @Test
    public void unwrapCompletionExceptionWithComplEx() {
        CompletableFuture<Integer> completionStage = new CompletableFuture<>();
        CompletableFuture<Integer> future = Futures.unwrapCompletionException(completionStage);
        Assert.assertFalse(future.isDone());
        completionStage.completeExceptionally(new CompletionException(new ExpectedException(DEFAULT_FAILURE)));
        assertCompletedWithExpEx(future, DEFAULT_FAILURE);
    }

    @Test
    public void unwrapCompletionExceptionNoCause() {
        CompletableFuture<Integer> completionStage = new CompletableFuture<>();
        CompletableFuture<Integer> future = Futures.unwrapCompletionException(completionStage);
        Assert.assertFalse(future.isDone());
        completionStage.completeExceptionally(new CompletionException(null));
        Assert.assertTrue(future.isCompletedExceptionally());
        Throwable throwable = extractException(future);
        Assert.assertTrue(throwable instanceof CompletionException);
        Assert.assertNull(throwable.getCause());
    }

    @Test
    public void unwrapCompletionExceptionFailure() {
        CompletableFuture<Integer> completionStage = new CompletableFuture<>();
        CompletableFuture<Integer> future = Futures.unwrapCompletionException(completionStage);
        Assert.assertFalse(future.isDone());
        completionStage.completeExceptionally(new ExpectedException(DEFAULT_FAILURE));
        assertCompletedWithExpEx(future, DEFAULT_FAILURE);
    }

    @Test
    public void unwrapCompletionExceptionSuccess() {
        CompletableFuture<Integer> completionStage = new CompletableFuture<>();
        CompletableFuture<Integer> future = Futures.unwrapCompletionException(completionStage);
        Assert.assertFalse(future.isDone());
        completionStage.complete(25);
        Assert.assertTrue(future.isDone());
        Assert.assertEquals((int) future.join(), 25);
    }

    private static int identity(int number) {
        return number;
    }

    private static int increment(int number) {
        return number + 1;
    }

    private static <T> T takeIntAndThrow(int number) {
        throw new ExpectedException(DEFAULT_FAILURE);
    }

    @Test
    public void thenApplyFailure() {
        CompletableFuture<Integer> failedFuture = Futures.completedExceptionally(new ExpectedException(1));
        assertCompletedWithComplEx(failedFuture.thenApply(FuturesTest::identity), 1);
        assertCompletedWithComplEx(Futures.thenApply(failedFuture, FuturesTest::identity), 1);
        assertCompletedWithComplEx(
            Futures.thenApplyAsync(failedFuture, FuturesTest::identity, CALLING_THREAD_EXECUTOR), 1);

        CompletableFuture<Integer> oneFuture = CompletableFuture.completedFuture(1);
        assertCompletedWithComplEx(oneFuture.thenApply(FuturesTest::takeIntAndThrow), DEFAULT_FAILURE);
        assertCompletedWithComplEx(Futures.thenApply(oneFuture, FuturesTest::takeIntAndThrow), DEFAULT_FAILURE);
        assertCompletedWithComplEx(
            Futures.thenApplyAsync(oneFuture, FuturesTest::takeIntAndThrow, CALLING_THREAD_EXECUTOR), DEFAULT_FAILURE);
    }

    @Test
    public void thenApplySuccess() {
        CompletableFuture<Integer> oneFuture = CompletableFuture.completedFuture(1);
        Assert.assertEquals((int) Futures.thenApply(oneFuture, FuturesTest::increment).join(), 2);
    }

    private static CompletableFuture<Integer> identityFuture(int number) {
        return CompletableFuture.completedFuture(number);
    }

    private static CompletableFuture<Integer> incrementFuture(int number) {
        return CompletableFuture.completedFuture(increment(number));
    }

    private static CompletableFuture<Integer> intToFutureCompletedWithExpEx(int number) {
        return Futures.completedExceptionally(new ExpectedException(NEW_FUTURE_FAILED));
    }

    private static CompletableFuture<Integer> intToFutureCompletedWithComplEx(int number) {
        return Futures.completedExceptionally(new CompletionException(new ExpectedException(NEW_FUTURE_FAILED)));
    }

    @Test
    public void thenComposeFailure() {
        CompletableFuture<Integer> failedFuture = Futures.completedExceptionally(new ExpectedException(1));
        assertCompletedWithComplEx(failedFuture.thenCompose(FuturesTest::identityFuture), 1);
        assertCompletedWithComplEx(Futures.thenCompose(failedFuture, FuturesTest::identityFuture), 1);
        assertCompletedWithComplEx(
            Futures.thenComposeAsync(failedFuture, FuturesTest::identityFuture, CALLING_THREAD_EXECUTOR), 1);

        // Unfortunately, Java's type inference is pretty limited, so the following explicit type casts are necessary
        // (at least with javac 1.8.0_73)
        CompletableFuture<Integer> oneFuture = CompletableFuture.completedFuture(1);
        assertCompletedWithComplEx(
            oneFuture.thenCompose((Function<Integer, CompletableFuture<Integer>>) FuturesTest::takeIntAndThrow),
            DEFAULT_FAILURE
        );
        assertCompletedWithComplEx(
            Futures.thenCompose(
                oneFuture,
                (CheckedFunction<Integer, CompletableFuture<Integer>>) FuturesTest::takeIntAndThrow
            ),
            DEFAULT_FAILURE
        );
        assertCompletedWithComplEx(
            Futures.thenComposeAsync(
                oneFuture,
                (CheckedFunction<Integer, CompletableFuture<Integer>>) FuturesTest::takeIntAndThrow,
                CALLING_THREAD_EXECUTOR
            ),
            DEFAULT_FAILURE
        );

        // Earlier versions of the JDK (before 1.8.0_60?) had a bug, which could occur in the following line:
        // https://bugs.openjdk.java.net/browse/JDK-8068432
        assertCompletedWithComplEx(
            oneFuture.thenCompose(FuturesTest::intToFutureCompletedWithExpEx), NEW_FUTURE_FAILED);
        assertCompletedWithComplEx(
            Futures.thenCompose(oneFuture, FuturesTest::intToFutureCompletedWithExpEx), NEW_FUTURE_FAILED);
        assertCompletedWithComplEx(
            Futures.thenComposeAsync(oneFuture, FuturesTest::intToFutureCompletedWithExpEx, CALLING_THREAD_EXECUTOR),
            NEW_FUTURE_FAILED
        );

        assertCompletedWithComplEx(
            oneFuture.thenCompose(FuturesTest::intToFutureCompletedWithComplEx), NEW_FUTURE_FAILED);
        assertCompletedWithComplEx(
            Futures.thenCompose(oneFuture, FuturesTest::intToFutureCompletedWithComplEx), NEW_FUTURE_FAILED);
        assertCompletedWithComplEx(
            Futures.thenComposeAsync(oneFuture, FuturesTest::intToFutureCompletedWithComplEx, CALLING_THREAD_EXECUTOR),
            NEW_FUTURE_FAILED
        );
    }

    @Test
    public void thenComposeSuccess() {
        CompletableFuture<Integer> oneFuture = CompletableFuture.completedFuture(1);
        Assert.assertEquals((int) Futures.thenCompose(oneFuture, FuturesTest::incrementFuture).join(), 2);
    }

    private abstract static class Try<T> {
        private Try() {
            assert getClass().equals(Success.class) || getClass().equals(Failure.class);
        }
    }

    private static final class Success<T> extends Try<T> {
        private final T result;

        private Success(T result) {
            this.result = result;
        }

        @Override
        public boolean equals(@Nullable Object otherObject) {
            return this == otherObject
                || (otherObject instanceof Success<?> && result.equals(((Success<?>) otherObject).result));
        }

        @Override
        public int hashCode() {
            return result.hashCode();
        }
    }

    private static final class Failure<T> extends Try<T> {
        private final Throwable throwable;

        private Failure(Throwable throwable) {
            this.throwable = throwable;
        }
    }

    private static final class TryContainer {
        @Nullable private volatile Try<Integer> integerTry;

        private void consume(@Nullable Integer result, @Nullable Throwable throwable) {
            assert result != null || throwable != null;
            integerTry = throwable != null
                ? new Failure<>(throwable)
                : new Success<>(result);
        }

        private void consumeAndThrow(@Nullable Integer result, @Nullable Throwable throwable) {
            consume(result, throwable);
            throw new ExpectedException(WHEN_COMPLETE_FAILED);
        }
    }

    private static void assertWrappedExpectedExceptionCallback(
            Function<TryContainer, CompletableFuture<Integer>> function, int id) {
        TryContainer tryContainer = new TryContainer();
        CompletableFuture<Integer> whenCompleteFuture = function.apply(tryContainer);
        assertCompletedWithComplEx(whenCompleteFuture, id);

        @Nullable Try<Integer> integerTry = tryContainer.integerTry;
        Assert.assertNotNull(integerTry);
        Assert.assertTrue(integerTry instanceof Failure);
        Throwable throwable = ((Failure<?>) integerTry).throwable;
        assertIsExpectedException(throwable, id);
    }

    private static void assertThrowingSuccessCallback(
            Function<TryContainer, CompletableFuture<Integer>> function, int id) {
        TryContainer tryContainer = new TryContainer();
        CompletableFuture<Integer> whenCompleteFuture = function.apply(tryContainer);
        assertCompletedWithComplEx(whenCompleteFuture, id);

        @Nullable Try<Integer> integerTry = tryContainer.integerTry;
        Assert.assertNotNull(integerTry);
        Assert.assertEquals(integerTry, new Success<>(1));
    }

    @Test
    public void whenCompleteFailure() {
        CompletableFuture<Integer> failedFuture
            = Futures.completedExceptionally(new ExpectedException(INITIALLY_FAILED));
        assertWrappedExpectedExceptionCallback(
            container -> failedFuture.whenComplete(container::consume), INITIALLY_FAILED);
        assertWrappedExpectedExceptionCallback(
            container -> Futures.whenComplete(failedFuture, container::consume), INITIALLY_FAILED);
        assertWrappedExpectedExceptionCallback(
            container -> Futures.whenCompleteAsync(failedFuture, container::consume, CALLING_THREAD_EXECUTOR),
            INITIALLY_FAILED
        );

        CompletableFuture<Integer> oneFuture = CompletableFuture.completedFuture(1);
        assertThrowingSuccessCallback(
            container -> oneFuture.whenComplete(container::consumeAndThrow), WHEN_COMPLETE_FAILED);
        assertThrowingSuccessCallback(
            container -> Futures.whenComplete(oneFuture, container::consumeAndThrow), WHEN_COMPLETE_FAILED);
        assertThrowingSuccessCallback(
            container -> Futures.whenCompleteAsync(oneFuture, container::consumeAndThrow, CALLING_THREAD_EXECUTOR),
            WHEN_COMPLETE_FAILED
        );
    }

    private static void assertSuccessCallback(Function<TryContainer, CompletableFuture<Integer>> function, int result) {
        TryContainer tryContainer = new TryContainer();
        CompletableFuture<Integer> whenCompleteFuture = function.apply(tryContainer);
        Assert.assertTrue(whenCompleteFuture.isDone());
        Assert.assertEquals((int) whenCompleteFuture.join(), result);

        @Nullable Try<Integer> localResultAttempt = tryContainer.integerTry;
        Assert.assertNotNull(localResultAttempt);
        Assert.assertEquals(localResultAttempt, new Success<>(result));
    }

    @Test
    public void whenCompleteSuccess() {
        CompletableFuture<Integer> oneFuture = CompletableFuture.completedFuture(1);
        assertSuccessCallback(blabla -> oneFuture.whenComplete(blabla::consume), 1);
        assertSuccessCallback(blabla -> Futures.whenComplete(oneFuture, blabla::consume), 1);
        assertSuccessCallback(
            blabla -> Futures.whenCompleteAsync(oneFuture, blabla::consume, CALLING_THREAD_EXECUTOR), 1);
    }

    @Test
    public void translateException() {
        CompletableFuture<Integer> failedFuture
            = Futures.completedExceptionally(new ExpectedException(INITIALLY_FAILED));
        AtomicReference<Integer> idReference = new AtomicReference<>();
        assertCompletedWithExpEx(Futures.translateException(failedFuture, throwable -> {
            idReference.set(((ExpectedException) throwable).identifier);
            return new ExpectedException(2);
        }), 2);
        Assert.assertEquals((int) idReference.get(), INITIALLY_FAILED);

        // If the exception is thrown instead of returned, it would be wrapped in a CompletionException
        idReference.set(null);
        CompletableFuture<Integer> throwingTranslatedFuture = Futures.translateException(failedFuture, throwable -> {
            idReference.set(((ExpectedException) throwable).identifier);
            throw new ExpectedException(3);
        });
        assertCompletedWithComplEx(throwingTranslatedFuture, 3);
        Assert.assertEquals((int) idReference.get(), INITIALLY_FAILED);

        idReference.set(null);
        CompletableFuture<Integer> nullFuture = Futures.translateException(failedFuture, throwable -> {
            idReference.set(((ExpectedException) throwable).identifier);
            return null;
        });
        Assert.assertTrue(nullFuture.isCompletedExceptionally());
        Throwable throwable = extractException(nullFuture);
        Assert.assertTrue(throwable instanceof CompletionException);
        Assert.assertTrue(throwable.getCause() instanceof NullPointerException);

        CompletableFuture<Integer> oneFuture = CompletableFuture.completedFuture(1);
        CompletableFuture<Integer> translatedFuture = Futures.translateException(oneFuture, Function.identity());
        Assert.assertTrue(translatedFuture.isDone());
        Assert.assertEquals((int) translatedFuture.join(), 1);
    }

    @Test
    public void transformCompletionStage() {
        CompletableFuture<Integer> completionStage
            = Futures.completedExceptionally(new ExpectedException(INITIALLY_FAILED));
        assertCompletedWithExpEx(
            Futures.transformCompletionStage(
                ignored -> completionStage,
                (Integer result, Throwable failure, CompletableFuture<Integer> future) -> { }
            ),
            INITIALLY_FAILED
        );
    }

    private static final class AutoClosableImpl implements AutoCloseable {
        private final Behavior closeBehavior;
        private boolean open = true;

        private AutoClosableImpl(Behavior closeBehavior) {
            this.closeBehavior = closeBehavior;
        }

        @Override
        public void close() {
            open = false;
            if (closeBehavior == Behavior.EXCEPTION) {
                throw new ExpectedException(CLOSE_FAILED);
            }
        }
    }

    enum InitialStageKind {
        NULL {
            @Override
            CompletableFuture<AutoClosableImpl> newFuture(AutoClosableImpl autoClosable) {
                return CompletableFuture.completedFuture(null);
            }
        },

        SUCCESS {
            @Override
            CompletableFuture<AutoClosableImpl> newFuture(AutoClosableImpl autoClosable) {
                return CompletableFuture.completedFuture(autoClosable);
            }
        },

        FAILED {
            @Override
            CompletableFuture<AutoClosableImpl> newFuture(AutoClosableImpl autoClosable) {
                return Futures.completedExceptionally(new ExpectedException(INITIALLY_FAILED));
            }
        };

        abstract CompletableFuture<AutoClosableImpl> newFuture(AutoClosableImpl autoClosable);
    }

    enum Behavior {
        SUCCESS,
        EXCEPTION
    }

    private static void assertTryWithResourceResult(CompletableFuture<Integer> future, AutoClosableImpl autoClosable,
            InitialStageKind initialStageKind, @Nullable int[] expectedExceptionIds) {
        if (expectedExceptionIds == null) {
            Assert.assertTrue(future.isDone());
            Assert.assertEquals((int) future.join(), DEFAULT_RESULT);
        } else {
            assert expectedExceptionIds.length > 0;
            int[] suppressedIds = Arrays.copyOfRange(expectedExceptionIds, 1, expectedExceptionIds.length);
            assertCompletedWithComplEx(future, expectedExceptionIds[0], suppressedIds);
        }
        if (initialStageKind == InitialStageKind.SUCCESS) {
            Assert.assertFalse(autoClosable.open);
        }
    }

    @DataProvider
    Object[][] dataForThenApplyWithResource() {
        return new Object[][] {
            { InitialStageKind.NULL, Behavior.SUCCESS, Behavior.SUCCESS, null },
            { InitialStageKind.NULL, Behavior.EXCEPTION, Behavior.SUCCESS, new int[] { APPLY_FAILED } },
            { InitialStageKind.FAILED, Behavior.SUCCESS, Behavior.SUCCESS, new int[] { INITIALLY_FAILED } },
            { InitialStageKind.SUCCESS, Behavior.SUCCESS, Behavior.SUCCESS, null },
            { InitialStageKind.SUCCESS, Behavior.SUCCESS, Behavior.EXCEPTION, new int[] { CLOSE_FAILED } },
            { InitialStageKind.SUCCESS, Behavior.EXCEPTION, Behavior.SUCCESS, new int[] { APPLY_FAILED } },
            { InitialStageKind.SUCCESS, Behavior.EXCEPTION, Behavior.EXCEPTION,
                new int[] { APPLY_FAILED, CLOSE_FAILED } }
        };
    }

    @Test(dataProvider = "dataForThenApplyWithResource")
    public void thenApplyWithResourceAsync(
            InitialStageKind initialStageKind, Behavior applyBehavior, Behavior closeBehavior,
            @Nullable int[] expectedExceptionIds) {
        AutoClosableImpl autoClosable = new AutoClosableImpl(closeBehavior);
        CompletableFuture<AutoClosableImpl> initialStage = initialStageKind.newFuture(autoClosable);
        CompletableFuture<Integer> future = Futures.thenApplyWithResourceAsync(
            initialStage,
            applyBehavior == Behavior.EXCEPTION
                ? ignored -> { throw new ExpectedException(APPLY_FAILED); }
                : ignored -> DEFAULT_RESULT,
            CALLING_THREAD_EXECUTOR
        );
        assertTryWithResourceResult(future, autoClosable, initialStageKind, expectedExceptionIds);
    }

    enum WrapKind {
        WRAP,
        DONT_WRAP
    }

    @DataProvider
    public Object[][] dataForThenComposeWithResource() {
        return new Object[][] {
            { InitialStageKind.NULL, Behavior.SUCCESS, Behavior.SUCCESS, WrapKind.DONT_WRAP, Behavior.SUCCESS,
                null },
            { InitialStageKind.NULL, Behavior.SUCCESS, Behavior.EXCEPTION, WrapKind.DONT_WRAP, Behavior.SUCCESS,
                new int[] { NEW_FUTURE_FAILED } },
            { InitialStageKind.NULL, Behavior.EXCEPTION, Behavior.SUCCESS, WrapKind.DONT_WRAP, Behavior.SUCCESS,
                new int[] { COMPOSE_FAILED } },
            { InitialStageKind.FAILED, Behavior.SUCCESS, Behavior.SUCCESS, WrapKind.DONT_WRAP, Behavior.SUCCESS,
                new int[] { INITIALLY_FAILED } },
            { InitialStageKind.SUCCESS, Behavior.SUCCESS, Behavior.SUCCESS, WrapKind.DONT_WRAP, Behavior.SUCCESS,
                null },
            { InitialStageKind.SUCCESS, Behavior.SUCCESS, Behavior.SUCCESS, WrapKind.DONT_WRAP, Behavior.EXCEPTION,
                new int[] { CLOSE_FAILED } },
            { InitialStageKind.SUCCESS, Behavior.SUCCESS, Behavior.EXCEPTION, WrapKind.DONT_WRAP, Behavior.SUCCESS,
                new int[] { NEW_FUTURE_FAILED } },
            { InitialStageKind.SUCCESS, Behavior.SUCCESS, Behavior.EXCEPTION, WrapKind.DONT_WRAP, Behavior.EXCEPTION,
                new int[] { NEW_FUTURE_FAILED, CLOSE_FAILED } },
            { InitialStageKind.SUCCESS, Behavior.SUCCESS, Behavior.EXCEPTION, WrapKind.WRAP, Behavior.EXCEPTION,
                new int[] { NEW_FUTURE_FAILED, CLOSE_FAILED } },
            { InitialStageKind.SUCCESS, Behavior.EXCEPTION, Behavior.SUCCESS, WrapKind.DONT_WRAP, Behavior.SUCCESS,
                new int[] { COMPOSE_FAILED } },
            { InitialStageKind.SUCCESS, Behavior.EXCEPTION, Behavior.SUCCESS, WrapKind.DONT_WRAP, Behavior.EXCEPTION,
                new int[] { COMPOSE_FAILED, CLOSE_FAILED }},
        };
    }

    private static CompletableFuture<Integer> newComposeWithResourceFuture(
            Behavior futureBehavior, WrapKind wrapKind) {
        if (futureBehavior == Behavior.EXCEPTION) {
            Throwable throwable = wrapKind == WrapKind.WRAP
                ? new CompletionException(new ExpectedException(NEW_FUTURE_FAILED))
                : new ExpectedException(NEW_FUTURE_FAILED);
            return Futures.completedExceptionally(throwable);
        } else {
            return CompletableFuture.completedFuture(DEFAULT_RESULT);
        }
    }

    @Test(dataProvider = "dataForThenComposeWithResource")
    public void thenComposeWithResource(InitialStageKind initialStageKind, Behavior composeBehavior,
            Behavior futureBehavior, WrapKind wrapFutureException,
            Behavior closeBehavior, @Nullable int[] expectedExceptionIds) {
        AutoClosableImpl autoClosable = new AutoClosableImpl(closeBehavior);
        CompletableFuture<AutoClosableImpl> initialStage = initialStageKind.newFuture(autoClosable);
        CompletableFuture<Integer> future = Futures.thenComposeWithResource(
            initialStage,
            composeBehavior == Behavior.EXCEPTION
                ? ignored -> { throw new ExpectedException(COMPOSE_FAILED); }
                : ignored -> newComposeWithResourceFuture(futureBehavior, wrapFutureException)
        );
        assertTryWithResourceResult(future, autoClosable, initialStageKind, expectedExceptionIds);
    }

    @Test
    public void decode() {
        CompletionException exception = new CompletionException(null);
        Assert.assertSame(Futures.unwrapCompletionException(exception), exception);
    }
}
