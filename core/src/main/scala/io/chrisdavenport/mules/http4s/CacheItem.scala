package io.chrisdavenport.mules.http4s

import io.chrisdavenport.mules.http4s.internal.CachedResponse
import cats._
// import cats.effect._
import cats.implicits._
import org.http4s.HttpDate
import io.chrisdavenport.cats.effect.time.JavaTime

/**
 * Cache Items are what we place in the cache, this is exposed
 * so that caches can be constructed by the user for this type
 **/
final class CacheItem private (
  private[http4s] val response: CachedResponse,
  private[http4s] val created: HttpDate,
  private[http4s] val expires: Option[HttpDate]
){
  private[http4s] def withResponse(cachedResponse: CachedResponse) = new CacheItem(
    cachedResponse,
    this.created,
    this.expires
  )
}

private[http4s] object CacheItem {

  final class Age private[CacheItem] (val deltaSeconds: Long) extends AnyVal
  final class CacheLifetime private[CacheItem] (val deltaSeconds: Long) extends AnyVal


  def create[F[_]: JavaTime: MonadError[*[_], Throwable]](response: CachedResponse, expires: Option[HttpDate]): F[CacheItem] = 
    JavaTime[F].getInstant.map(HttpDate.fromInstant).rethrow.map(date => 
      new CacheItem(response, date, expires)
    )

  def age(created: HttpDate, now: HttpDate): Age = new Age(now.epochSecond - created.epochSecond)

  def cacheLifetime(expires: Option[HttpDate], now: HttpDate): Option[CacheLifetime] = expires.map{expiredAt =>  
    new CacheLifetime(expiredAt.epochSecond - now.epochSecond)
  }

}