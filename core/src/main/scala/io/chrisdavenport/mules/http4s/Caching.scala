package io.chrisdavenport.mules.http4s

import io.chrisdavenport.mules.http4s.internal._
import org.http4s._
import io.chrisdavenport.mules._
import io.chrisdavenport.cats.effect.time._
import cats._
import cats.effect._
import cats.implicits._
import cats.data._
import fs2.Stream
import org.http4s.client.Client

private class Caching[F[_]: MonadError[*[_], Throwable]: JavaTime] private (cache: Cache[F, (Method, Uri), CacheItem], cacheType: CacheType)(implicit Compiler: Stream.Compiler[F,F]){

  def request(app: Kleisli[Resource[F, ?], Request[F], Response[F]])(req: Request[F]): Resource[F, Response[F]] = {
    if (CacheRules.requestCanUseCached(req)) {
      for {
        cachedValue <- Resource.liftF(cache.lookup((req.method, req.uri)))
        now <- Resource.liftF(JavaTime[F].getInstant.map(HttpDate.fromInstant).rethrow)
        out <- cachedValue match {
          case None => 
            if (CacheRules.onlyIfCached(req)) Response[F](Status.GatewayTimeout).pure[Resource[F, ?]]
            else app.run(req)
              .evalMap(withResponse(req, _))
          case Some(item) => 
            if (CacheRules.cacheAgeAcceptable(req, item, now)) {
              item.response.toResponse[F].pure[Resource[F, ?]]
            } else {
              app.run(req)
                .evalMap(withResponse(req, _))
            }
        }
      } yield out
    } else {
      app.run(req)
        .evalMap(withResponse(req, _))
    }
  }

  private def withResponse(req: Request[F], resp: Response[F]): F[Response[F]] = {
    if (CacheRules.isCacheable(req, resp, cacheType)){
      for {
        cachedResp <- CachedResponse.fromResponse(resp)
        now <- JavaTime[F].getInstant.map(HttpDate.fromInstant).rethrow
        expires = CacheRules.FreshnessAndExpiration.getExpires(now, resp)
        item <- CacheItem.create(cachedResp, expires.some)
        _ <- cache.insert((req.method, req.uri), item)
      } yield cachedResp.toResponse[F]
    } else resp.pure[F]
  }

}


object Caching {

  def client[F[_]: Bracket[*[_], Throwable]: JavaTime](cache: Cache[F, (Method, Uri), CacheItem], cacheType: CacheType)(implicit compiler: Stream.Compiler[F, F]): Client[F] => Client[F] = {
    val caching = new Caching[F](cache, cacheType){}

    {client: Client[F] => 
      Client(req => 
        caching.request(Kleisli(req => client.run(req)))(req)
      )
    }
  }

  def httpApp[F[_]: Bracket[*[_], Throwable]: JavaTime](cache: Cache[F, (Method, Uri), CacheItem], cacheType: CacheType)(implicit compiler: Stream.Compiler[F, F]): HttpApp[F] => HttpApp[F] = {
    val caching = new Caching[F](cache, cacheType){}
    {app: HttpApp[F] => 
      Kleisli{ req => 
        caching.request(Kleisli(req => Resource.liftF(app.run(req))))(req)
        .use(_.pure[F])
      }
    }
  }
}

