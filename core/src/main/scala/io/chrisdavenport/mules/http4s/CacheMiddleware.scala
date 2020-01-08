package io.chrisdavenport.mules.http4s

import cats.effect._
import io.chrisdavenport.cats.effect.time.JavaTime
import io.chrisdavenport.mules.Cache
import org.http4s._
import org.http4s.client.Client
import io.chrisdavenport.mules.http4s.internal._
import cats.data.{Kleisli, OptionT}
import fs2.Stream
import cats.arrow.FunctionK

object CacheMiddleware {

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