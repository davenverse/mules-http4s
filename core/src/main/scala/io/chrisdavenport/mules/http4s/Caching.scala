package io.chrisdavenport.mules.http4s

import org.http4s._
import io.chrisdavenport.mules._
import cats.effect._
import cats.implicits._
import cats.data._

private class Caching[F[_]: Sync](cache: Cache[F, (Method, Uri), CacheItem], isPrivate: Boolean){

  def request(req: Request[F])(app: Kleisli[Resource[F, ?], Request[F], Response[F]]): Resource[F, Response[F]] = {
    if (CacheRules.requestCanUseCached(req)) {
      for {
        cachedValue <- Resource.liftF(cache.lookup((req.method, req.uri)))
        now <- Resource.liftF(CacheItem.dateNow)
        out <- cachedValue match {
          case None => 
            if (CacheRules.onlyIfCached(req)) Response[F](Status.GatewayTimeout).pure[Resource[F, ?]]
            else app.run(req)

          case Some(item) => 
            if (CacheRules.cacheAgeAcceptable(req, item, now)) {
              item.response.toResponse[F].pure[Resource[F, ?]]
            } else {
              app.run(req)
            }
        }
      } yield out
    } else {
      app.run(req)
    }
  }

  
}

