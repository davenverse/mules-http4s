package io.chrisdavenport.mules.http4s.internal

import io.chrisdavenport.mules.http4s._
import org.http4s._
import io.chrisdavenport.mules._
import cats._
import cats.syntax.all._
import cats.data._
import cats.effect._
import org.http4s.Header.ToRaw.modelledHeadersToRaw

private[http4s] class Caching[F[_]: Concurrent: Clock] private[http4s] (cache: Cache[F, (Method, Uri), CacheItem], cacheType: CacheType){

  def request[G[_]: FlatMap](app: Kleisli[G, Request[F], Response[F]], fk: F ~> G)(req: Request[F]): G[Response[F]] = {
    if (CacheRules.requestCanUseCached(req)) {
      for {
        cachedValue <- fk(cache.lookup((req.method, req.uri)))
        now <- fk(HttpDate.current[F])
        out <- cachedValue match {
          case None => 
            if (CacheRules.onlyIfCached(req)) fk(Response[F](Status.GatewayTimeout).pure[F])
            else {
              app.run(req)
              .flatMap(resp => fk(withResponse(req, resp)))
            }
          case Some(item) => 
            if (CacheRules.cacheAgeAcceptable(req, item, now)) {
              fk(item.response.toResponse[F].pure[F])
            } else {
              app.run(
                req
                .putHeaders(CacheRules.getIfMatch(item.response).map(modelledHeadersToRaw(_)).toSeq:_*)
                .putHeaders(CacheRules.getIfUnmodifiedSince(item.response).map(modelledHeadersToRaw(_)).toSeq:_*)
              ).flatMap(resp => fk(withResponse(req, resp)))
            }
        }
      } yield out
    } else {
      app.run(req)
        .flatMap(resp => fk(withResponse(req, resp)))
    }
  }

  private def withResponse(req: Request[F], resp: Response[F]): F[Response[F]] = {
    {
      if (CacheRules.shouldInvalidate(req, resp)){
        cache.delete((req.method, req.uri))
      } else Applicative[F].unit 
    } *> {
      if (CacheRules.isCacheable(req, resp, cacheType)){
        for {
          cachedResp <- resp.status match {
            case Status.NotModified => 
              cache.lookup((req.method, req.uri))
                .flatMap(
                  _.map{item => 
                    val cached = item.response
                    cached.withHeaders(resp.headers ++ cached.headers).pure[F]
                  }
                  .getOrElse(CachedResponse.fromResponse[F, F](resp))
                )
            case _ => CachedResponse.fromResponse[F, F](resp)
          }
          now <- HttpDate.current[F]
          expires = CacheRules.FreshnessAndExpiration.getExpires(now, resp)
          item <- CacheItem.create(cachedResp, expires.some)
          _ <- cache.insert((req.method, req.uri), item)
        } yield cachedResp.toResponse[F]
      
      } else {
        resp.pure[F]
      }
    }
  }

}