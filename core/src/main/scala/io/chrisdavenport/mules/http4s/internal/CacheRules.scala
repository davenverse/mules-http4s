package io.chrisdavenport.mules.http4s.internal

import io.chrisdavenport.mules.http4s._

import org.http4s._
import org.http4s.headers._
import org.http4s.CacheDirective._
import scala.concurrent.duration._
import cats._
import cats.implicits._
import cats.data._

private[http4s] object CacheRules {

  def requestCanUseCached[F[_]](req: Request[F]): Boolean =  
    methodIsCacheable(req.method) &&
    !req.headers.get(`Cache-Control`).exists{
      _.values.exists{ 
        case `no-cache`(_) => true
        case _ => false
      }
    }

  private val cacheableMethods: Set[Method] = Set(
    Method.GET,
    Method.HEAD,
    // Method.POST // Eventually make this work.
  )

  def methodIsCacheable(m: Method): Boolean = cacheableMethods.contains(m)

  private val cacheableStatus: Set[Status] = Set(
    Status.Ok, // 200
    Status.NonAuthoritativeInformation, // 203
    Status.NoContent, // 204
    Status.PartialContent, // 206
    Status.MultipleChoices, // 300
    Status.MovedPermanently, // 301
    // Status.NotModified , // 304
    Status.NotFound, // 404
    Status.MethodNotAllowed, // 405
    Status.Gone, // 410
    Status.UriTooLong, // 414
    Status.NotImplemented, // 501
  )

  def statusIsCacheable(s: Status): Boolean = cacheableStatus.contains(s)

  def cacheAgeAcceptable[F[_]](req: Request[F], item: CacheItem, now: HttpDate): Boolean = {
    req.headers.get(`Cache-Control`) match {
      case None => 
        
        // TODO: Investigate how this check works with cache-control
        // If the data in the cache is expired and client does not explicitly
        // accept stale data, then age is not ok.
        item.expires.map(expiresAt => expiresAt >= now).getOrElse(true)
      case Some(`Cache-Control`(values)) => 
        val age = CacheItem.age(item.created, now)
        val lifetime = CacheItem.cacheLifetime(item.expires, now)

        val maxAgeMet: Boolean = values.toList
          .collectFirst{ case c@CacheDirective.`max-age`(_) => c }
          .map(maxAge => age.deltaSeconds.seconds <= maxAge.deltaSeconds )
          .getOrElse(true)

        val maxStaleMet: Boolean = {
          for {
            maxStale <- values.toList.collectFirst{ case c@CacheDirective.`max-stale`(_) => c.deltaSeconds}.flatten
            stale <- lifetime
          } yield if (stale.deltaSeconds >= 0) true else stale.deltaSeconds.seconds <= maxStale
        }.getOrElse(true)

        val minFreshMet: Boolean = {
          for {
            minFresh <- values.toList.collectFirst{case CacheDirective.`min-fresh`(seconds) => seconds}
            expiresAt <- item.expires
          } yield (expiresAt.epochSecond - now.epochSecond).seconds <= minFresh
        }.getOrElse(true)
        
        // println(s"Age- $age, Lifetime- $lifetime, maxAgeMet: $maxAgeMet, maxStaleMet: $maxStaleMet, minFreshMet: $minFreshMet")
        
        maxAgeMet && maxStaleMet && minFreshMet
    }
  }

  def onlyIfCached[F[_]](req: Request[F]): Boolean = req.headers.get(`Cache-Control`)
    .exists{_.values.exists{ 
      case `only-if-cached` => true
      case _ => false
    }}

  def cacheControlNoStoreExists[F[_]](response: Response[F]): Boolean = response.headers
    .get(`Cache-Control`)
    .toList
    .flatMap(_.values.toList)
    .exists{
      case CacheDirective.`no-store` => true
      case _ => false
    }

  def cacheControlPrivateExists[F[_]](response: Response[F]): Boolean = response.headers
    .get(`Cache-Control`)
    .toList
    .flatMap(_.values.toList)
    .exists{
      case CacheDirective.`private`(_) => true
      case _ => false
    }

  def authorizationHeaderExists[F[_]](response: Response[F]): Boolean = response.headers
    .get(Authorization)
    .isDefined

  def cacheControlPublicExists[F[_]](response: Response[F]): Boolean = response.headers
    .get(`Cache-Control`)
    .toList
    .flatMap(_.values.toList)
    .exists{
      case CacheDirective.public => true
      case _ => false
    }

  def mustRevalidate[F[_]](response: Message[F]): Boolean = {
    response.headers.get(`Cache-Control`).exists{_.values.exists{ 
      case CacheDirective.`no-cache`(_) => true
      case CacheDirective.`max-age`(age) if age <= 0.seconds => true
      case _ => false
    }} || response.headers.get(Pragma).exists(_.value === "no-cache")
  }

  def isCacheable[F[_]](req: Request[F], response: Response[F], cacheType: CacheType): Boolean = {
    if (!cacheableMethods.contains(req.method)) {
      // println(s"Request Method ${req.method} - not Cacheable")
      false
    } else if (!statusIsCacheable(response.status)) {
      // println(s"Response Status ${response.status} - not Cacheable")
      false
    } else if (cacheControlNoStoreExists(response)) {
      // println("Cache-Control No-Store is present - not Cacheable")
      false
    } else if (cacheType.isShared && cacheControlPrivateExists(response)) {
      // println("Cache is shared and Cache-Control private exists - not Cacheable")
      false
    } else if (cacheType.isShared && response.headers.get(Vary).exists(h => h.value === "*")) {
      // println("Cache is shared and Vary header exists as * - not Cacheable")
      false
    } else if (cacheType.isShared && authorizationHeaderExists(response) && !cacheControlPublicExists(response)) {
      // println("Cache is Shared and Authorization Header is present and Cache-Control public is not present - not Cacheable")
      false
    } else if (mustRevalidate(response) && !(response.headers.get(ETag).isDefined || response.headers.get(`Last-Modified`).isDefined)) {
      false
    } else if (req.method === Method.GET || req.method === Method.HEAD) {
      true
    } else if (cacheControlPublicExists(response) || cacheControlPrivateExists(response)) {
      true
    } else {
      response.headers.get(Expires).isDefined
    }
  }

  def shouldInvalidate[F[_]](request: Request[F], response: Response[F]): Boolean = {
    if (Set(Status.NotFound, Status.Gone).contains(response.status)) {
      true
    } else if (Set(Method.GET, Method.HEAD: Method).contains(request.method)){
      false
    } else response.status.isSuccess
  }

  def getIfMatch(cachedResponse: CachedResponse): Option[`If-None-Match`] = 
    cachedResponse.headers.get(ETag).map(_.tag).flatMap{etag => 
    if (!etag.weak) `If-None-Match`(NonEmptyList.of(etag).some).some
    else None
  }

  def getIfUnmodifiedSince(cachedResponse: CachedResponse): Option[`If-Unmodified-Since`] = {
    for {
      lastModified <- cachedResponse.headers.get(`Last-Modified`)
      date <- cachedResponse.headers.get(Date)
      _ <- Alternative[Option].guard(date.date.epochSecond - lastModified.date.epochSecond >= 60L)
    } yield `If-Unmodified-Since`(lastModified.date)
  }

  object FreshnessAndExpiration {
    // Age in Seconds
    private def getAge[F[_]](now: HttpDate, response: Message[F]): FiniteDuration = {

      // Age Or Zero
      val initAgeSeconds: Long = now.epochSecond - 
        response.headers.get(Date).map(date => date.date.epochSecond)
          .getOrElse(0L)  

      response.headers.get(Age)
        .map(age => Math.max(age.age, initAgeSeconds))
        .getOrElse(initAgeSeconds)
        .seconds
    }

    // Since We do not emit warnings on cache times over 24 hours, limit cache time
    // to max of 24 hours.
    private def freshnessLifetime[F[_]](now: HttpDate, response: Message[F]) = {
      response.headers.get(`Cache-Control`)
        .flatMap{ 
            case `Cache-Control`(directives) => 
              directives.collectFirst{ 
                case `max-age`(deltaSeconds) => 
                  deltaSeconds match {
                    case finite: FiniteDuration => finite
                    case _ => 24.hours
                  }
              }
        }.orElse{
          for {
            exp <- response.headers.get(Expires)
            date <- response.headers.get(Date)
          } yield (exp.expirationDate.epochSecond - date.date.epochSecond).seconds
        }.orElse{
          response.headers.get(`Last-Modified`)
            .map{lm => 
              val estimatedLifetime = (now.epochSecond - lm.date.epochSecond) / 10
              Math.min(24.hours.toSeconds, estimatedLifetime).seconds
            }
        }.getOrElse(24.hours)

    }

    def getExpires[F[_]](now: HttpDate, response: Message[F]): HttpDate = {
      val age = getAge(now, response)
      val lifetime = freshnessLifetime(now, response)
      val ttl = lifetime - age

      HttpDate.unsafeFromEpochSecond(now.epochSecond + ttl.toSeconds)
    }
  }

}