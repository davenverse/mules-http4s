package io.chrisdavenport.mules.http4s

import cats.effect._
import cats.implicits._
import org.http4s.HttpDate
import java.time.Instant

final class CacheItem private (
  val response: CachedResponse,
  val created: HttpDate,
  val expires: Option[HttpDate]
){
  // def validate(now: HttpDate): Option[(CacheItem, CacheItem.Age)] = CacheItem.validate(this, now)
}

object CacheItem {

  final class Age private[CacheItem] (val deltaSeconds: Long) extends AnyVal
  final class CacheLifetime private[CacheItem] (val deltaSeconds: Long) extends AnyVal

  def dateNow[F[_]: Sync]: F[HttpDate] = Sync[F].delay(HttpDate.fromInstant(Instant.now)).rethrow

  def create[F[_]: Sync](response: CachedResponse, expires: Option[HttpDate]): F[CacheItem] = 
    dateNow.map(date => 
      new CacheItem(response, date, expires)
    )

  // def validate(item: CacheItem, now: HttpDate): Option[(CacheItem, Age)] = item.expires match {
  //   case None => (item, age(item.created, now)).some
  //   case Some(value) if value > now => (item, age(item.created, now)).some
  //   case _  => None
  // }

  def age(created: HttpDate, now: HttpDate): Age = new Age(now.epochSecond - created.epochSecond)

  def cacheLifetime(expires: Option[HttpDate], now: HttpDate): Option[CacheLifetime] = expires.map{expiredAt =>  
    new CacheLifetime(expiredAt.epochSecond - now.epochSecond)
  }
    
}