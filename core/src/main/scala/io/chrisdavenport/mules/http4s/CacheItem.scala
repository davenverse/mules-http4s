package io.chrisdavenport.mules.http4s

import cats._
// import cats.effect._
import cats.implicits._
import org.http4s.HttpDate
import io.chrisdavenport.cats.effect.time.JavaTime
import scodec._
import scodec.codecs._

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

  def create[F[_]: JavaTime: MonadError[*[_], Throwable]](response: CachedResponse, expires: Option[HttpDate]): F[CacheItem] = 
    JavaTime[F].getInstant.map(HttpDate.fromInstant).rethrow.map(date => 
      new CacheItem(date, expires, response)
    )

  private[http4s] val httpDateCodec: Codec[HttpDate] = 
    int64.exmapc(i => Attempt.fromEither(HttpDate.fromEpochSecond(i).leftMap(e => Err(e.details))))(
      date => Attempt.successful(date.epochSecond)
    )

  val codec: Codec[CacheItem] = (httpDateCodec :: optional(bool, httpDateCodec) :: CachedResponse.codec).as[CacheItem]

  final case class Age(val deltaSeconds: Long) extends AnyVal
  object Age {
    def of(created: HttpDate, now: HttpDate): Age = new Age(now.epochSecond - created.epochSecond)
  }
  final case class CacheLifetime(val deltaSeconds: Long) extends AnyVal
  object CacheLifetime {
    def of(expires: Option[HttpDate], now: HttpDate): Option[CacheLifetime] = expires.map{expiredAt =>  
      new CacheLifetime(expiredAt.epochSecond - now.epochSecond)
    }
  }
}