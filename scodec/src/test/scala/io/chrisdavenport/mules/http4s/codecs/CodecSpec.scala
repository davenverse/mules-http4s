package io.chrisdavenport.mules.http4s.codecs

import io.chrisdavenport.mules.http4s._
import Arbitraries._

class CodecSpec extends org.specs2.mutable.Specification with org.specs2.ScalaCheck {

  "CachedResponse Codec" should {
    "round trip succesfully" in prop{ cached: CachedResponse =>

      val encoded = cachedResponseCodec.encode(cached)
      val decoded = encoded.flatMap(bv => cachedResponseCodec.decode(bv))

      decoded.toEither must beRight.like{
        case a  => a.value must_=== cached
      }
    }
  }

  "CacheItem Codec" should {
    "round trip succesfully" in prop { cached: CacheItem =>

      val encoded = cacheItemCodec.encode(cached)
      val decoded = encoded.flatMap(bv => cacheItemCodec.decode(bv))

      decoded.toEither must beRight.like{
        case a  => a.value must_=== cached
      }
    }
  }
}