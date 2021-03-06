Bridge gaps and help overcome inconveniences with
[`CompletableFuture`](http://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletableFuture.html).

## Status
[![Build Status](https://travis-ci.org/fschopp/java-futures.svg?branch=master)](https://travis-ci.org/fschopp/java-futures)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/net.florianschoppmann.java/java-futures/badge.svg?style=flat)](http://search.maven.org/#search|gav|1|g:net.florianschoppmann.java%20AND%20a:java-futures)

## Overview

- requires Java 8
- methods that collect the results of multiple completion stages into a `List`
  future, both with short-circuit semantics (fail early if an input completion
  stage fails) and without (guaranteed not to be completed while there are
  uncompleted input stages)
- equivalent methods for
  [`supplyAsync`](http://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletableFuture.html#supplyAsync-java.util.function.Supplier-),
  [`thenApply`](http://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletableFuture.html#thenApply-java.util.function.Function-),
  [`thenCompose`](http://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletableFuture.html#thenCompose-java.util.function.Function-),
  etc. that accept functions throwing checked exceptions
- method similar to Scala’s
  [`Promise#completeWith`](http://www.scala-lang.org/api/current/index.html#scala.concurrent.Promise@completeWith(other:scala.concurrent.Future[T]):Promise.this.type)
- method for unwrapping
  [`CompletionException`](http://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletionException.html)
- method for exception mapping
- methods for dealing with asynchronous
  “[try-with-resources](https://docs.oracle.com/javase/specs/jls/se8/html/jls-14.html#jls-14.20.3)”
  scenarios
- tests have virtually
  [full code coverage](http://fschopp.github.io/java-futures/snapshot/jacoco)

## License

[Revised BSD (3-Clause) License](LICENSE)

## Binary Releases

Published releases (compiled for Java 8 and up) are available on Maven Central.

```
<dependency>
    <groupId>net.florianschoppmann.java</groupId>
    <artifactId>java-futures</artifactId>
    <version>1.1.0</version>
</dependency>
```

## Documentation

For current snapshot:

- [API documentation](http://fschopp.github.io/java-futures/snapshot/apidocs)
- [Maven-generated project documentation](http://fschopp.github.io/java-futures/snapshot)

Release documentation is available by replacing “snapshot” in the URL by the
respective version number (such as “1.1.0”).

## Usage Examples

The following examples show some use cases of class `Futures` provided by this
project.

### Collect the results of multiple completion stages into a `List` future

```java
CompletableFuture<Integer> sum(Set<CompletionStage<Integer>> set) {
    return Futures.shortCircuitCollect(set)
        .thenApply(list -> list.stream().mapToInt(Integer::intValue).sum());
}
```

### Equivalent methods allowing for checked exceptions

```java
CompletableFuture<Long> fileSize(CompletionStage<Path> filePathStage,
        Executor executor) {
    // Note that Files#size(Path) throws an IOException, hence it would have to
    // be wrapped in the lambda expression passed to
    // CompletionStage#thenApply(Function).
    return Futures.thenApplyAsync(filePathStage, Files::size, executor);
}
```
