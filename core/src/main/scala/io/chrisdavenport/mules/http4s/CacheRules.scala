package io.chrisdavenport.mules.http4s

import org.http4s._
import org.http4s.headers._
import org.http4s.CacheDirective._
import scala.concurrent.duration._

object CacheRules {

  // def withCached[F[_]](req: Request[F], cachedResponse: Option[(CachedResponse, Age)]) = ???


  def requestCanUseCached[F[_]](req: Request[F]): Boolean =  
    methodIsCacheable(req.method) &&
    !req.headers.get(`Cache-Control`).exists{
      _.values.exists{ 
        case `no-cache`(_) => true
        case _ => false
      }
    }
    

  private val cacheableMethods = Seq(
    Method.GET,
    Method.HEAD
  )

  def methodIsCacheable(m: Method): Boolean = cacheableMethods.contains(m)

  private val cacheableStatus = Seq(
    Status.Ok, // 200
    Status.NonAuthoritativeInformation, // 203
    Status.NoContent, // 204
    Status.PartialContent, // 206
    Status.MultipleChoices, // 300
    Status.MovedPermanently, // 301
    Status.NotFound, // 404
    Status.MethodNotAllowed, // 405
    Status.Gone, // 410
    Status.UriTooLong, // 414
    Status.NotImplemented, // 501
  )

  def statusIsCacheable(s: Status): Boolean = cacheableStatus.contains(s)

  def cacheAgeAcceptable[F[_]](req: Request[F], item: CacheItem, now: HttpDate): Boolean = {
    req.headers.get(`Cache-Control`) match {
      case None => true
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
        
        maxAgeMet && maxStaleMet && minFreshMet
    }
  }

  def onlyIfCached[F[_]](req: Request[F]): Boolean = req.headers.get(`Cache-Control`)
    .exists{_.values.exists{ 
      case `only-if-cached` => true
      case _ => false
    }}


}