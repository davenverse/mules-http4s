package io.chrisdavenport.mules.http4s

import cats.syntax.all._
import cats.effect._
import io.chrisdavenport.mules.Cache
import org.http4s._
import org.http4s.client.Client
import io.chrisdavenport.mules.http4s.internal._
import cats.data.{Kleisli, OptionT}
import cats.arrow.FunctionK

/**
 * Middlewares that allow one to cache full servers or clients.
 * 
 * All Constraints - Bracket, JavaTime, and Compiler are satisfied by Sync and Clock
 **/
object CacheMiddleware {

  def client[F[_]: Concurrent: Clock](cache: Cache[F, (Method, Uri), CacheItem], cacheType: CacheType): Client[F] => Client[F] = {
    val caching = new Caching[F](cache, cacheType){}
    {(client: Client[F]) =>
      Client((req: Request[F]) =>
        caching.request(Kleisli((req: Request[F]) => client.run(req)), Resource.liftK)(req)
      )
    }
  }

  def httpApp[F[_]: Concurrent: Clock](cache: Cache[F, (Method, Uri), CacheItem], cacheType: CacheType): HttpApp[F] => HttpApp[F] = {
    val caching = new Caching[F](cache, cacheType){}
    {(app: HttpApp[F]) =>
      Kleisli{ (req: Request[F]) =>
        caching.request(app, FunctionK.id)(req)
      }
    }
  }

  def httpRoutes[F[_]: Concurrent: Clock](cache: Cache[F, (Method, Uri), CacheItem], cacheType: CacheType): HttpRoutes[F] => HttpRoutes[F] = {
    val caching = new Caching[F](cache, cacheType){}
    {(app: HttpRoutes[F]) =>
      Kleisli{ (req: Request[F]) =>
        caching.request(app, OptionT.liftK)(req)
      }
    }
  }

  /**
    * Used on a Server rather than Client Caching Mechanism. Headers Applied Are Saved in the cache but caching headers
    * are not relayed to external callers.
    */
  def internalHttpApp[F[_]: Concurrent: Clock](cache: Cache[F, (Method, Uri), CacheItem], cacheType: CacheType): HttpApp[F] => HttpApp[F] = {
    val caching = new Caching[F](cache, cacheType){}
    {(app: HttpApp[F]) =>
      Kleisli{ (req: Request[F]) =>
        caching.request(app, FunctionK.id)(req).map(resp => 
          resp.filterHeaders(removeCacheHeaders).putHeaders(noStoreStaticHeaders:_*)
        )
      }
    }
  }


  /**
    * Used on a Server rather than Client Caching Mechanism. Headers Applied Are Saved in the cache but caching headers
    * are not relayed to external callers.
    */
  def internalHttpRoutes[F[_]: Concurrent: Clock](cache: Cache[F, (Method, Uri), CacheItem], cacheType: CacheType): HttpRoutes[F] => HttpRoutes[F] = {
        val caching = new Caching[F](cache, cacheType){}
    {(app: HttpRoutes[F]) =>
      Kleisli{ (req: Request[F]) =>
        caching.request(app, OptionT.liftK)(req).map(resp => 
          resp.filterHeaders(removeCacheHeaders).putHeaders(noStoreStaticHeaders:_*)
        )
      }
    }
  }

  import org.typelevel.ci._
  import cats.data.NonEmptyList
  import scala.concurrent.duration._
  import org.http4s.headers.{`Cache-Control`, Expires}

  private def removeCacheHeaders(h: Header.Raw): Boolean =
      h.name != `Cache-Control`.headerInstance.name &&
      h.name != CIString("Pragma") &&
      h.name != Expires.headerInstance.name

  private val noStoreStaticHeaders: List[Header.ToRaw] = List(
    `Cache-Control`(
      NonEmptyList.of[CacheDirective](
        CacheDirective.`no-store`,
        CacheDirective.`no-cache`(),
        CacheDirective.`max-age`(0.seconds)
      )
    ),
    "Pragma" -> "no-cache",
    Expires(HttpDate.Epoch) // Expire at the epoch for no time confusion
  )
}