package io.chrisdavenport.mules.http4s

import io.chrisdavenport.mules.http4s.internal._
import org.http4s._
import io.chrisdavenport.mules._
import io.chrisdavenport.cats.effect.time._
import cats._
import cats.arrow.FunctionK
import cats.effect._
import cats.implicits._
import cats.data._
import fs2.Stream
import org.http4s.client.Client

private class Caching[F[_]: MonadError[*[_], Throwable]: JavaTime] private (cache: Cache[F, (Method, Uri), CacheItem], cacheType: CacheType)(implicit Compiler: Stream.Compiler[F,F]){

  def request[G[_]: FlatMap](app: Kleisli[G, Request[F], Response[F]], fk: F ~> G)(req: Request[F]): G[Response[F]] = {
    if (CacheRules.requestCanUseCached(req)) {
      for {
        cachedValue <- fk(cache.lookup((req.method, req.uri)))
        now <- fk(JavaTime[F].getInstant.map(HttpDate.fromInstant).rethrow)
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
                .putHeaders(CacheRules.getIfMatch(item.response).toSeq:_*)
                .putHeaders(CacheRules.getIfUnmodifiedSince(item.response).toSeq:_*)
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
                  .getOrElse(CachedResponse.fromResponse(resp))
                )
            case _ => CachedResponse.fromResponse(resp)
          }
          now <- JavaTime[F].getInstant.map(HttpDate.fromInstant).rethrow
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


object Caching {

  def client[F[_]: Bracket[*[_], Throwable]: JavaTime](cache: Cache[F, (Method, Uri), CacheItem], cacheType: CacheType)(implicit compiler: Stream.Compiler[F, F]): Client[F] => Client[F] = {
    val caching = new Caching[F](cache, cacheType){}
    {client: Client[F] => 
      Client(req => 
        caching.request(Kleisli(req => client.run(req)), Resource.liftK)(req)
      )
    }
  }

  def httpApp[F[_]: Bracket[*[_], Throwable]: JavaTime](cache: Cache[F, (Method, Uri), CacheItem], cacheType: CacheType)(implicit compiler: Stream.Compiler[F, F]): HttpApp[F] => HttpApp[F] = {
    val caching = new Caching[F](cache, cacheType){}
    {app: HttpApp[F] => 
      Kleisli{ req => 
        caching.request(app, FunctionK.id)(req)
      }
    }
  }

  def httpRoutes[F[_]: Bracket[*[_], Throwable]: JavaTime](cache: Cache[F, (Method, Uri), CacheItem], cacheType: CacheType)(implicit compiler: Stream.Compiler[F, F]): HttpRoutes[F] => HttpRoutes[F] = {
    val caching = new Caching[F](cache, cacheType){}
    {app: HttpRoutes[F] => 
      Kleisli{ req => 
        caching.request(app, OptionT.liftK)(req)
      }
    }
  }
}

