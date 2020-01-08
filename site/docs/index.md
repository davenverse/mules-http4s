---
layout: home

---

# mules-http4s - Http4s Caching Implementation [![Build Status](https://travis-ci.com/ChristopherDavenport/mules-http4s.svg?branch=master)](https://travis-ci.com/ChristopherDavenport/mules-http4s) [![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.chrisdavenport/mules-http4s_2.12/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.chrisdavenport/mules-http4s_2.12)

## Quick Start

To use mules-http4s in an existing SBT project with Scala 2.11 or a later version, add the following dependencies to your
`build.sbt` depending on your needs:

```scala
libraryDependencies ++= Seq(
  "io.chrisdavenport" %% "mules-http4s" % "<version>"
)
```


## Basic Use 

```scala mdoc
import cats._
import cats.implicits._
import cats.effect._
import cats.effect.concurrent.Ref
import io.chrisdavenport.mules._
import io.chrisdavenport.mules.caffeine._
import io.chrisdavenport.mules.http4s._
import org.http4s._
import org.http4s.implicits._
import org.http4s.client.Client
import org.http4s.client.asynchttpclient._

implicit val T = IO.timer(scala.concurrent.ExecutionContext.global)
implicit val CS = IO.contextShift(scala.concurrent.ExecutionContext.global)

def testMiddleware[F[_]: Bracket[*[_], Throwable]](c: Client[F], ref: Ref[F, Int]): Client[F] = {
  Client{req => c.run(req).evalMap(resp => ref.update(_ + 1).as(resp))}
}

val jQueryRequest = Request[IO](Method.GET, uri"https://code.jquery.com/jquery-3.4.1.slim.min.js")

val exampleCached = AsyncHttpClient.resource[IO]().use{ client => 
    for {
    cache <- CaffeineCache.build[IO, (Method, Uri), CacheItem](None, None, 10000L.some)
    counter <- Ref[IO].of(0)
    cacheMiddleware = CacheMiddleware.client(cache, CacheType.Public)
    finalClient = cacheMiddleware(testMiddleware(client, counter))
    _ <- finalClient.run(jQueryRequest).use(_.as[String])
    count1 <- counter.get
    _ <- finalClient.run(jQueryRequest).use(_.as[String])  
    count2 <- counter.get
  } yield (count1, count2)
}

exampleCached.unsafeRunSync

val dadJokesRequest = Request[IO](Method.GET, uri"https://icanhazdadjoke.com/")

val exampleUnCached = AsyncHttpClient.resource[IO]().use{ client => 
    for {
    cache <- CaffeineCache.build[IO, (Method, Uri), CacheItem](None, None, 10000L.some)
    counter <- Ref[IO].of(0)
    cacheMiddleware = CacheMiddleware.client(cache, CacheType.Public)
    finalClient = cacheMiddleware(testMiddleware(client, counter))
    _ <- finalClient.run(dadJokesRequest).use(_.as[String])
    count1 <- counter.get
    _ <- finalClient.run(dadJokesRequest).use(_.as[String])  
    count2 <- counter.get
  } yield (count1, count2)
}

exampleUnCached.unsafeRunSync
```