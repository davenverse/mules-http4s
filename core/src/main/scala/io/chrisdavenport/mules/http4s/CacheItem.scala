package io.chrisdavenport.mules.http4s

import io.chrisdavenport.mules.http4s.internal.CachedResponse
import cats._
// import cats.effect._
import cats.implicits._
import org.http4s.HttpDate
import io.chrisdavenport.cats.effect.time.JavaTime

final class CacheItem private (
  val response: CachedResponse,
  val created: HttpDate,
  val expires: Option[HttpDate]
)

object CacheItem {

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