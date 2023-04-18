package io.chrisdavenport.mules.http4s

import cats._
import cats.effect._
import cats.implicits._
import org.http4s.HttpDate

/**
 * Cache Items are what we place in the cache, this is exposed
 * so that caches can be constructed by the user for this type
 **/
final case class CacheItem(
  created: HttpDate,
  expires: Option[HttpDate],
  response: CachedResponse,
)

object CacheItem {

  def create[F[_]: Clock: MonadThrow](response: CachedResponse, expires: Option[HttpDate]): F[CacheItem] =
    HttpDate.current[F].map(date =>
      new CacheItem(date, expires, response)
    )

  private[http4s] final case class Age(val deltaSeconds: Long) extends AnyVal
  private[http4s] object Age {
    def of(created: HttpDate, now: HttpDate): Age = new Age(now.epochSecond - created.epochSecond)
  }
  private[http4s] final case class CacheLifetime(val deltaSeconds: Long) extends AnyVal
  private[http4s] object CacheLifetime {
    def of(expires: Option[HttpDate], now: HttpDate): Option[CacheLifetime] = expires.map{expiredAt =>  
      new CacheLifetime(expiredAt.epochSecond - now.epochSecond)
    }
  }
}