package io.chrisdavenport.mules.http4s

import cats.implicits._
import cats.data._
import cats.effect._
import cats.effect.concurrent._
import cats.effect.specs2.CatsIO
import org.http4s._
import org.http4s.implicits._
import org.http4s.headers._
// import org.http4s.dsl.io._
import scala.concurrent.duration._
import io.chrisdavenport.cats.effect.time.JavaTime

class ResponseCachingSpec extends org.specs2.mutable.Specification with CatsIO {
  "Caching Responses" should {
    "never cache a response that should never be cached" in {
      for {
        cache <- io.chrisdavenport.mules.MemoryCache.ofConcurrentHashMap[IO,(Method, Uri), CacheItem](None)
        ref <- Ref[IO].of(0)
        now <- JavaTime[IO].getInstant.map(HttpDate.fromInstant).rethrow
        httpApp = HttpRoutes.of[IO]{
          case _ => ref.modify(i => (i+1, i)).map{i => 
            Response[IO](Status.Ok)
              .withEntity(i.toString())
              .withHeaders(
                `Cache-Control`(
                  NonEmptyList.of(CacheDirective.`no-store`)
                ),
                Date(now),
                Expires(now),
                Header("Pragma", "no-cache")
              )
          }
        }.orNotFound
        cached = Caching.httpApp(cache, CacheType.Private)
        newApp = cached(httpApp)
        request = Request[IO]()

        firstResp <- newApp.run(request)
        first <- firstResp.as[String]

        secondResp <- newApp.run(request)
        second <- secondResp.as[String]
      } yield {
        (first, second) must_===(("0","1"))
      }
    }

    "cache a public cache response" in {
      for {
        cache <- io.chrisdavenport.mules.MemoryCache.ofConcurrentHashMap[IO,(Method, Uri), CacheItem](None)
        ref <- Ref[IO].of(0)
        now <- JavaTime[IO].getInstant.map(HttpDate.fromInstant).rethrow
        lifetime = 24.hours
        httpApp = HttpRoutes.of[IO]{
          case _ => ref.modify(i => (i+1, i)).map{i => 
            Response[IO](Status.Ok)
              .withEntity(i.toString())
              .withHeaders(
                `Cache-Control`(
                  NonEmptyList.of(
                    CacheDirective.public,
                    CacheDirective.`max-age`(lifetime)
                  )
                ),
                Date(now),
                Expires(HttpDate.unsafeFromEpochSecond(now.epochSecond + lifetime.toSeconds)),
              )
          }
        }.orNotFound
        cached = Caching.httpApp(cache, CacheType.Public)
        newApp = cached(httpApp)
        request = Request[IO]()

        firstResp <- newApp.run(request)
        first <- firstResp.as[String]

        secondResp <- newApp.run(request)
        second <- secondResp.as[String]
      } yield {
        (first, second) must_===(("0","0"))
      }
    }

    "public cache does not cache private response" in {
      for {
        cache <- io.chrisdavenport.mules.MemoryCache.ofConcurrentHashMap[IO,(Method, Uri), CacheItem](None)
        ref <- Ref[IO].of(0)
        now <- JavaTime[IO].getInstant.map(HttpDate.fromInstant).rethrow
        lifetime = 24.hours
        httpApp = HttpRoutes.of[IO]{
          case _ => ref.modify(i => (i+1, i)).map{i => 
            Response[IO](Status.Ok)
              .withEntity(i.toString())
              .withHeaders(
                `Cache-Control`(
                  NonEmptyList.of(
                    CacheDirective.`private`(List.empty),
                    CacheDirective.`max-age`(lifetime)
                  )
                ),
                Date(now),
                Expires(HttpDate.unsafeFromEpochSecond(now.epochSecond + lifetime.toSeconds)),
              )
          }
        }.orNotFound
        cached = Caching.httpApp(cache, CacheType.Public)
        newApp = cached(httpApp)
        request = Request[IO]()

        firstResp <- newApp.run(request)
        first <- firstResp.as[String]

        secondResp <- newApp.run(request)
        second <- secondResp.as[String]
      } yield {
        (first, second) must_===(("0","1"))
      }
    }


    "private cache does cache private response" in {
      for {
        cache <- io.chrisdavenport.mules.MemoryCache.ofConcurrentHashMap[IO,(Method, Uri), CacheItem](None)
        ref <- Ref[IO].of(0)
        now <- JavaTime[IO].getInstant.map(HttpDate.fromInstant).rethrow
        lifetime = 24.hours
        httpApp = HttpRoutes.of[IO]{
          case _ => ref.modify(i => (i+1, i)).map{i => 
            Response[IO](Status.Ok)
              .withEntity(i.toString())
              .withHeaders(
                `Cache-Control`(
                  NonEmptyList.of(
                    CacheDirective.`private`(List.empty),
                    CacheDirective.`max-age`(lifetime)
                  )
                ),
                Date(now),
                Expires(HttpDate.unsafeFromEpochSecond(now.epochSecond + lifetime.toSeconds)),
              )
          }
        }.orNotFound
        cached = Caching.httpApp(cache, CacheType.Private)
        newApp = cached(httpApp)
        request = Request[IO]()

        firstResp <- newApp.run(request)
        first <- firstResp.as[String]

        secondResp <- newApp.run(request)
        second <- secondResp.as[String]
      } yield {
        (first, second) must_===(("0","0"))
      }
    }

    "cached value expires after time" in {
      for {
        cache <- io.chrisdavenport.mules.MemoryCache.ofConcurrentHashMap[IO,(Method, Uri), CacheItem](None)
        ref <- Ref[IO].of(0)
        now <- JavaTime[IO].getInstant.map(HttpDate.fromInstant).rethrow
        lifetime = 1.second
        httpApp = HttpRoutes.of[IO]{
          case _ => ref.modify(i => (i+1, i)).map{i => 
            Response[IO](Status.Ok)
              .withEntity(i.toString())
              .withHeaders(
                `Cache-Control`(
                  NonEmptyList.of(
                    CacheDirective.`private`(List.empty),
                    CacheDirective.`max-age`(lifetime)
                  )
                ),
                Date(now),
                Expires(HttpDate.unsafeFromEpochSecond(now.epochSecond + lifetime.toSeconds)),
              )
          }
        }.orNotFound
        cached = Caching.httpApp(cache, CacheType.Private)
        newApp = cached(httpApp)
        request = Request[IO]()

        firstResp <- newApp.run(request)
        first <- firstResp.as[String]

        _ <- Timer[IO].sleep(2.seconds)

        secondResp <- newApp.run(request)
        second <- secondResp.as[String]
      } yield {
        (first, second) must_===(("0","1"))
      }
    }

  }
}