package io.chrisdavenport.mules.http4s.codecs

import io.chrisdavenport.mules.http4s._
import org.http4s._
import org.http4s.implicits._
import Arbitraries._
import cats.effect.unsafe.implicits.global

class CodecSpec extends org.specs2.mutable.Specification with org.specs2.ScalaCheck {

  "CachedResponse Codec" should {
    "encode a specific response" in {
      val baseString = "Hello"
      val cached = CachedResponse.fromResponse[fs2.Pure, cats.Id](
        Response[fs2.Pure](Status.Ok, HttpVersion.`HTTP/1.0`, Headers(("foo", "bar"))).withEntity(baseString)
      )
      val encoded = cachedResponseCodec.encode(cached)
      val decoded = encoded.flatMap(bv => cachedResponseCodec.decode(bv))

      decoded.toEither must beRight.like{
        case a  =>
          (a.value must_=== cached) &&
            (a.value.toResponse[fs2.Pure].body.through(fs2.text.utf8.decode).compile.string must_=== baseString)
      }
    }

    "round trip succesfully" in prop{ (cached: CachedResponse) =>

      val encoded = cachedResponseCodec.encode(cached)
      val decoded = encoded.flatMap(bv => cachedResponseCodec.decode(bv))

      decoded.toEither must beRight.like{
        case a  => a.value must_=== cached
      }
    }
  }

  "CacheItem Codec" should {
    "round trip succesfully" in prop { (cached: CacheItem) =>

      val encoded = cacheItemCodec.encode(cached)
      val decoded = encoded.flatMap(bv => cacheItemCodec.decode(bv))

      decoded.toEither must beRight.like{
        case a  => a.value must_=== cached
      }
    }
  }

  "Cache Key Codec" should {

    "round-trip a known uri" in {
      val test = (Method.GET, uri"https://chrisdavenport.io:4553/foo/bar/baz?implicit=yes")
      val encoded = keyTupleCodec.encode(test)
      val decoded = encoded.flatMap(bv => keyTupleCodec.decode(bv))
      decoded.toEither.map(_.value) must beRight.like{
        case a  => (a._1 must_=== test._1) and (a._2 must_=== test._2)
      }
    }

    "round trip succesfully" in prop { (cacheKey: (Method, Uri)) => Uri.fromString(cacheKey._2.renderString).isRight ==> {
      // Gave up after only 45 passed tests. 501 tests were discarded.
      // Uri.fromString(cacheKey._2.renderString).map(_ == cacheKey._2).getOrElse(false)  ==> {
      val encoded = keyTupleCodec.encode(cacheKey)
      val decoded = encoded.flatMap(bv => keyTupleCodec.decode(bv))

      val checkTraversal = Uri.fromString(cacheKey._2.renderString).map(_ == cacheKey._2).getOrElse(false)
      
      decoded.toEither.map(_.value) must beRight.like{
        case a  if (checkTraversal) => (a._1 must_=== cacheKey._1) and (a._2 must_=== cacheKey._2)
        case a => (a._1 must_=== cacheKey._1)
      }
    }}
  }
}