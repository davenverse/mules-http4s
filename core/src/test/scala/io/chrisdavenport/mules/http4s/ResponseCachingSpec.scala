package io.chrisdavenport.mules.http4s

import cats.implicits._
import cats.data._
import cats.effect._
import cats.effect.testing.specs2.CatsEffect
import org.http4s._
import org.http4s.implicits._
import org.http4s.headers._
// import org.http4s.dsl.io._
import scala.concurrent.duration._

class ResponseCachingSpec extends org.specs2.mutable.Specification with CatsEffect {
  "Caching Responses" should {
    "never cache a response that should never be cached" in {
      for {
        cache <- io.chrisdavenport.mules.MemoryCache.ofConcurrentHashMap[IO,(Method, Uri), CacheItem](None)
        ref <- Ref[IO].of(0)
        now <- HttpDate.current[IO]
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
                ("Pragma", "no-cache")
              )
          }
        }.orNotFound
        cached = CacheMiddleware.httpApp(cache, CacheType.Private)
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
        now <- HttpDate.current[IO]
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
        cached = CacheMiddleware.httpApp(cache, CacheType.Public)
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
        now <- HttpDate.current[IO]
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
        cached = CacheMiddleware.httpApp(cache, CacheType.Public)
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
        now <- HttpDate.current[IO]
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
        cached = CacheMiddleware.httpApp(cache, CacheType.Private)
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

    "public cache does not serve an authorized response to a later unauthenticated request" in {
      for {
        cache <- io.chrisdavenport.mules.MemoryCache.ofConcurrentHashMap[IO,(Method, Uri), CacheItem](None)
        ref <- Ref[IO].of(0)
        now <- HttpDate.current[IO]
        lifetime = 24.hours
        httpApp = HttpRoutes.of[IO]{
          case _ => ref.modify(i => (i+1, i)).map{i =>
            Response[IO](Status.Ok)
              .withEntity(i.toString())
              .withHeaders(
                `Cache-Control`(
                  NonEmptyList.of(
                    CacheDirective.`max-age`(lifetime)
                  )
                ),
                Date(now),
                Expires(HttpDate.unsafeFromEpochSecond(now.epochSecond + lifetime.toSeconds)),
              )
          }
        }.orNotFound
        cached = CacheMiddleware.httpApp(cache, CacheType.Public)
        newApp = cached(httpApp)
        authorized = Request[IO]().putHeaders(
          Authorization(Credentials.Token(AuthScheme.Bearer, "a-users-secret-token"))
        )
        anonymous = Request[IO]()

        firstResp <- newApp.run(authorized)
        first <- firstResp.as[String]

        secondResp <- newApp.run(anonymous)
        second <- secondResp.as[String]
      } yield {
        (first, second) must_===(("0","1"))
      }
    }

    "public cache does cache an authorized response explicitly marked public" in {
      for {
        cache <- io.chrisdavenport.mules.MemoryCache.ofConcurrentHashMap[IO,(Method, Uri), CacheItem](None)
        ref <- Ref[IO].of(0)
        now <- HttpDate.current[IO]
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
        cached = CacheMiddleware.httpApp(cache, CacheType.Public)
        newApp = cached(httpApp)
        authorized = Request[IO]().putHeaders(
          Authorization(Credentials.Token(AuthScheme.Bearer, "a-users-secret-token"))
        )

        firstResp <- newApp.run(authorized)
        first <- firstResp.as[String]

        secondResp <- newApp.run(authorized)
        second <- secondResp.as[String]
      } yield {
        (first, second) must_===(("0","0"))
      }
    }

    "private cache still caches an authorized response" in {
      for {
        cache <- io.chrisdavenport.mules.MemoryCache.ofConcurrentHashMap[IO,(Method, Uri), CacheItem](None)
        ref <- Ref[IO].of(0)
        now <- HttpDate.current[IO]
        lifetime = 24.hours
        httpApp = HttpRoutes.of[IO]{
          case _ => ref.modify(i => (i+1, i)).map{i =>
            Response[IO](Status.Ok)
              .withEntity(i.toString())
              .withHeaders(
                `Cache-Control`(
                  NonEmptyList.of(
                    CacheDirective.`max-age`(lifetime)
                  )
                ),
                Date(now),
                Expires(HttpDate.unsafeFromEpochSecond(now.epochSecond + lifetime.toSeconds)),
              )
          }
        }.orNotFound
        cached = CacheMiddleware.httpApp(cache, CacheType.Private)
        newApp = cached(httpApp)
        authorized = Request[IO]().putHeaders(
          Authorization(Credentials.Token(AuthScheme.Bearer, "a-users-secret-token"))
        )

        firstResp <- newApp.run(authorized)
        first <- firstResp.as[String]

        secondResp <- newApp.run(authorized)
        second <- secondResp.as[String]
      } yield {
        (first, second) must_===(("0","0"))
      }
    }

    "min-fresh is satisfied by a response that stays fresh for long enough" in {
      for {
        cache <- io.chrisdavenport.mules.MemoryCache.ofConcurrentHashMap[IO,(Method, Uri), CacheItem](None)
        ref <- Ref[IO].of(0)
        now <- HttpDate.current[IO]
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
        cached = CacheMiddleware.httpApp(cache, CacheType.Public)
        newApp = cached(httpApp)
        request = Request[IO]()
        // 24h of remaining freshness comfortably exceeds the 60s demanded.
        demanding = Request[IO]().putHeaders(
          `Cache-Control`(NonEmptyList.of(CacheDirective.`min-fresh`(60.seconds)))
        )

        firstResp <- newApp.run(request)
        first <- firstResp.as[String]

        secondResp <- newApp.run(demanding)
        second <- secondResp.as[String]
      } yield {
        (first, second) must_===(("0","0"))
      }
    }

    "min-fresh is not satisfied by a response that goes stale too soon" in {
      for {
        cache <- io.chrisdavenport.mules.MemoryCache.ofConcurrentHashMap[IO,(Method, Uri), CacheItem](None)
        ref <- Ref[IO].of(0)
        now <- HttpDate.current[IO]
        lifetime = 30.seconds
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
        cached = CacheMiddleware.httpApp(cache, CacheType.Public)
        newApp = cached(httpApp)
        request = Request[IO]()
        // Only ~30s of freshness remains, so an hour's worth cannot be met.
        demanding = Request[IO]().putHeaders(
          `Cache-Control`(NonEmptyList.of(CacheDirective.`min-fresh`(1.hour)))
        )

        firstResp <- newApp.run(request)
        first <- firstResp.as[String]

        secondResp <- newApp.run(demanding)
        second <- secondResp.as[String]
      } yield {
        (first, second) must_===(("0","1"))
      }
    }

    "a shared cache does not serve one user's varied response to another" in {
      for {
        cache <- io.chrisdavenport.mules.MemoryCache.ofConcurrentHashMap[IO,(Method, Uri), CacheItem](None)
        ref <- Ref[IO].of(0)
        now <- HttpDate.current[IO]
        lifetime = 24.hours
        httpApp = HttpRoutes.of[IO]{
          case _ => ref.modify(i => (i+1, i)).map{i =>
            Response[IO](Status.Ok)
              .withEntity(i.toString())
              .withHeaders(
                `Cache-Control`(
                  NonEmptyList.of(CacheDirective.public, CacheDirective.`max-age`(lifetime))
                ),
                Date(now),
                Expires(HttpDate.unsafeFromEpochSecond(now.epochSecond + lifetime.toSeconds)),
                ("Vary", "Cookie"),
              )
          }
        }.orNotFound
        cached = CacheMiddleware.httpApp(cache, CacheType.Public)
        newApp = cached(httpApp)
        userA = Request[IO]().putHeaders(("Cookie", "session=aaa"))
        userB = Request[IO]().putHeaders(("Cookie", "session=bbb"))

        firstResp <- newApp.run(userA)
        first <- firstResp.as[String]

        secondResp <- newApp.run(userB)
        second <- secondResp.as[String]
      } yield {
        (first, second) must_===(("0","1"))
      }
    }

    "a varied response is still reused for a matching request" in {
      for {
        cache <- io.chrisdavenport.mules.MemoryCache.ofConcurrentHashMap[IO,(Method, Uri), CacheItem](None)
        ref <- Ref[IO].of(0)
        now <- HttpDate.current[IO]
        lifetime = 24.hours
        httpApp = HttpRoutes.of[IO]{
          case _ => ref.modify(i => (i+1, i)).map{i =>
            Response[IO](Status.Ok)
              .withEntity(i.toString())
              .withHeaders(
                `Cache-Control`(
                  NonEmptyList.of(CacheDirective.public, CacheDirective.`max-age`(lifetime))
                ),
                Date(now),
                Expires(HttpDate.unsafeFromEpochSecond(now.epochSecond + lifetime.toSeconds)),
                ("Vary", "Accept-Encoding"),
              )
          }
        }.orNotFound
        cached = CacheMiddleware.httpApp(cache, CacheType.Public)
        newApp = cached(httpApp)
        request = Request[IO]().putHeaders(("Accept-Encoding", "gzip"))

        firstResp <- newApp.run(request)
        first <- firstResp.as[String]

        secondResp <- newApp.run(request)
        second <- secondResp.as[String]
      } yield {
        (first, second) must_===(("0","0"))
      }
    }

    "a private cache also honours Vary" in {
      for {
        cache <- io.chrisdavenport.mules.MemoryCache.ofConcurrentHashMap[IO,(Method, Uri), CacheItem](None)
        ref <- Ref[IO].of(0)
        now <- HttpDate.current[IO]
        lifetime = 24.hours
        httpApp = HttpRoutes.of[IO]{
          case _ => ref.modify(i => (i+1, i)).map{i =>
            Response[IO](Status.Ok)
              .withEntity(i.toString())
              .withHeaders(
                `Cache-Control`(NonEmptyList.of(CacheDirective.`max-age`(lifetime))),
                Date(now),
                Expires(HttpDate.unsafeFromEpochSecond(now.epochSecond + lifetime.toSeconds)),
                ("Vary", "Accept-Encoding"),
              )
          }
        }.orNotFound
        cached = CacheMiddleware.httpApp(cache, CacheType.Private)
        newApp = cached(httpApp)
        gzip = Request[IO]().putHeaders(("Accept-Encoding", "gzip"))
        plain = Request[IO]()

        firstResp <- newApp.run(gzip)
        first <- firstResp.as[String]

        // Serving the gzip variant here would be a content-negotiation bug
        // even though a private cache has no cross-user exposure.
        secondResp <- newApp.run(plain)
        second <- secondResp.as[String]
      } yield {
        (first, second) must_===(("0","1"))
      }
    }

    "the internal vary key is never served to a client" in {
      for {
        cache <- io.chrisdavenport.mules.MemoryCache.ofConcurrentHashMap[IO,(Method, Uri), CacheItem](None)
        now <- HttpDate.current[IO]
        lifetime = 24.hours
        httpApp = HttpRoutes.of[IO]{
          case _ => IO.pure(
            Response[IO](Status.Ok)
              .withEntity("body")
              .withHeaders(
                `Cache-Control`(
                  NonEmptyList.of(CacheDirective.public, CacheDirective.`max-age`(lifetime))
                ),
                Date(now),
                Expires(HttpDate.unsafeFromEpochSecond(now.epochSecond + lifetime.toSeconds)),
                ("Vary", "Accept-Encoding"),
              )
          )
        }.orNotFound
        cached = CacheMiddleware.httpApp(cache, CacheType.Public)
        newApp = cached(httpApp)
        request = Request[IO]().putHeaders(("Accept-Encoding", "gzip"))

        // once on the store path, once on the serve-from-cache path
        firstResp <- newApp.run(request)
        secondResp <- newApp.run(request)
      } yield {
        val name = org.typelevel.ci.CIString("X-Mules-Http4s-Vary-Key")
        (firstResp.headers.get(name) must beNone) and
          (secondResp.headers.get(name) must beNone)
      }
    }

    "cached value expires after time" in {
      for {
        cache <- io.chrisdavenport.mules.MemoryCache.ofConcurrentHashMap[IO,(Method, Uri), CacheItem](None)
        ref <- Ref[IO].of(0)
        now <- HttpDate.current[IO]
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
        cached = CacheMiddleware.httpApp(cache, CacheType.Private)
        newApp = cached(httpApp)
        request = Request[IO]()

        firstResp <- newApp.run(request)
        first <- firstResp.as[String]

        _ <- Temporal[IO].sleep(2.seconds)

        secondResp <- newApp.run(request)
        second <- secondResp.as[String]
      } yield {
        (first, second) must_===(("0","1"))
      }
    }

  }
}