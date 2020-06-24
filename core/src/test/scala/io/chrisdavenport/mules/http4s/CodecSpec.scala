package io.chrisdavenport.mules.http4s

import Arbitraries._

class CodecSpec extends org.specs2.mutable.Specification with org.specs2.ScalaCheck {

  "CachedResponse Codec" should {
    "round trip succesfully" in prop{ cached: CachedResponse =>

      val encoded = CachedResponse.codec.encode(cached)
      val decoded = encoded.flatMap(bv => CachedResponse.codec.decode(bv))

      decoded.toEither must beRight.like{
        case a  => a.value must_=== cached
      }
    }
  }

  "CacheItem Codec" should {
    "round trip succesfully" in prop { cached: CacheItem =>

      val encoded = CacheItem.codec.encode(cached)
      val decoded = encoded.flatMap(bv => CacheItem.codec.decode(bv))

      decoded.toEither must beRight.like{
        case a  => a.value must_=== cached
      }
    }
  }
}