package io.chrisdavenport.mules.http4s.codecs

import io.chrisdavenport.mules.http4s._
import _root_.scodec.bits.ByteVector
import org.http4s._
import org.http4s.implicits._
import org.http4s.util.CaseInsensitiveString
import java.time._
import java.util.Locale
import cats._
import cats.implicits._
import org.scalacheck._
import org.scalacheck.cats.implicits._

trait Arbitraries {
    implicit lazy val arbitraryByteVector: Arbitrary[ByteVector] =
    Arbitrary(Gen.containerOf[Array, Byte](Arbitrary.arbitrary[Byte]).map(ByteVector(_)))
  
  implicit lazy val arbStatus: Arbitrary[Status] = 
    Arbitrary{
      Gen.choose(100, 599).map(Status.fromInt(_).fold(throw _, identity)) // Safe because we are in valid range
    }

  implicit lazy val arbHttpVersion : Arbitrary[HttpVersion] = Arbitrary {
      for {
        major <- Gen.choose(0, 9)
        minor <- Gen.choose(0, 9)
      } yield HttpVersion.fromVersion(major, minor).fold(throw _ , identity)
    }

  val genVchar: Gen[Char] =
    Gen.oneOf('\u0021' to '\u007e')

  val genFieldVchar: Gen[Char] =
    genVchar

  val genTchar: Gen[Char] = Gen.oneOf {
    Seq('!', '#', '$', '%', '&', '\'', '*', '+', '-', '.', '^', '_', '`', '|', '~') ++
      ('0' to '9') ++ ('A' to 'Z') ++ ('a' to 'z')
  }

  val genToken: Gen[String] =
    Gen.nonEmptyListOf(genTchar).map(_.mkString)

  val genFieldContent: Gen[String] =
    for {
      head <- genFieldVchar
      tail <- Gen.containerOf[Vector, Vector[Char]](
        Gen.frequency(
          9 -> genFieldVchar.map(Vector(_)),
          1 -> (for {
            spaces <- Gen.nonEmptyContainerOf[Vector, Char](Gen.oneOf(' ', '\t'))
            fieldVchar <- genFieldVchar
          } yield spaces :+ fieldVchar)
        )
      ).map(_.flatten)
    } yield (head +: tail).mkString

  val genFieldValue: Gen[String] =
    genFieldContent

  implicit lazy val http4sTestingArbitraryForRawHeader: Arbitrary[Header.Raw] =
    Arbitrary {
      for {
        token <- genToken
        value <- genFieldValue
      } yield Header.Raw(CaseInsensitiveString(token), value)
    }

  implicit lazy val headers: Arbitrary[Headers]  = Arbitrary(Gen.listOf(Arbitrary.arbitrary[Header.Raw]).map(Headers(_)))

  implicit lazy val arbCachedResponse: Arbitrary[CachedResponse] = Arbitrary(
    for {
      status <- Arbitrary.arbitrary[Status]
      version <- Arbitrary.arbitrary[HttpVersion]
      headers <- Arbitrary.arbitrary[Headers]
      bv <- Arbitrary.arbitrary[ByteVector]
    } yield CachedResponse(status, version, headers, bv)
  )

  val genHttpDate: Gen[HttpDate] = {
    val min = ZonedDateTime
      .of(1900, 1, 1, 0, 0, 0, 0, ZoneId.of("UTC"))
      .toInstant
      .toEpochMilli / 1000
    val max = ZonedDateTime
      .of(9999, 12, 31, 23, 59, 59, 0, ZoneId.of("UTC"))
      .toInstant
      .toEpochMilli / 1000
    Gen.choose[Long](min, max).map(HttpDate.unsafeFromEpochSecond)
  }

  implicit lazy val arbHttpDate: Arbitrary[HttpDate] = Arbitrary(genHttpDate)
  
  implicit lazy val arbCacheItem = Arbitrary(
    for {
      created <- Arbitrary.arbitrary[HttpDate]
      expires <- Arbitrary.arbitrary[Option[HttpDate]]
      cachedResponse <- Arbitrary.arbitrary[CachedResponse]
    } yield CacheItem(created, expires, cachedResponse)
  )

  implicit lazy val arbMethod = Arbitrary(
    Gen.oneOf(Method.all)
  )

  // https://tools.ietf.org/html/rfc2234#section-6
  val genHexDigit: Gen[Char] = Gen.oneOf(
    List('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'))

    // https://tools.ietf.org/html/rfc3986#appendix-A
  implicit lazy val http4sTestingArbitraryForIpv4Address: Arbitrary[Uri.Ipv4Address] = Arbitrary {
    for {
      a <- Arbitrary.arbitrary[Byte]
      b <- Arbitrary.arbitrary[Byte]
      c <- Arbitrary.arbitrary[Byte]
      d <- Arbitrary.arbitrary[Byte]
    } yield Uri.Ipv4Address(a, b, c, d)
  }

  implicit lazy val http4sTestingCogenForIpv4Address: Cogen[Uri.Ipv4Address] =
    Cogen[(Byte, Byte, Byte, Byte)].contramap(ipv4 => (ipv4.a, ipv4.b, ipv4.c, ipv4.d))

  // https://tools.ietf.org/html/rfc3986#appendix-A
  implicit lazy val http4sTestingArbitraryForIpv6Address: Arbitrary[Uri.Ipv6Address] = Arbitrary {
    for {
      a <- Arbitrary.arbitrary[Short]
      b <- Arbitrary.arbitrary[Short]
      c <- Arbitrary.arbitrary[Short]
      d <- Arbitrary.arbitrary[Short]
      e <- Arbitrary.arbitrary[Short]
      f <- Arbitrary.arbitrary[Short]
      g <- Arbitrary.arbitrary[Short]
      h <- Arbitrary.arbitrary[Short]
    } yield Uri.Ipv6Address(a, b, c, d, e, f, g, h)
  }

  implicit lazy val http4sTestingCogenForIpv6Address: Cogen[Uri.Ipv6Address] =
    Cogen[(Short, Short, Short, Short, Short, Short, Short, Short)]
      .contramap(ipv6 => (ipv6.a, ipv6.b, ipv6.c, ipv6.d, ipv6.e, ipv6.f, ipv6.g, ipv6.h))

  implicit lazy val http4sTestingArbitraryForUriHost: Arbitrary[Uri.Host] = Arbitrary {
    val genRegName =
      Gen.listOf(Gen.oneOf(genUnreserved, genPctEncoded, genSubDelims)).map(rn => Uri.RegName(rn.mkString))
    Gen.oneOf(Arbitrary.arbitrary[Uri.Ipv4Address], Arbitrary.arbitrary[Uri.Ipv6Address], genRegName)
  }

  implicit lazy val http4sTestingArbitraryForUserInfo: Arbitrary[Uri.UserInfo] =
    Arbitrary(
      for {
        username <- Arbitrary.arbitrary[String]
        password <- Arbitrary.arbitrary[Option[String]]
      } yield Uri.UserInfo(username, password)
    )

  implicit lazy val http4sTestingCogenForUserInfo: Cogen[Uri.UserInfo] =
    Cogen.tuple2[String, Option[String]].contramap(u => (u.username, u.password))

  implicit lazy val http4sTestingArbitraryForAuthority: Arbitrary[Uri.Authority] = Arbitrary {
    for {
      maybeUserInfo <- Arbitrary.arbitrary[Option[Uri.UserInfo]]
      host <- http4sTestingArbitraryForUriHost.arbitrary
      maybePort <- Gen.option(Gen.posNum[Int].suchThat(port => port >= 0 && port <= 65536))
    } yield Uri.Authority(maybeUserInfo, host, maybePort)
  }

  val genPctEncoded: Gen[String] =
    (Gen.const("%"), genHexDigit.map(_.toString), genHexDigit.map(_.toString)).mapN(_ |+| _ |+| _)
  val genUnreserved: Gen[Char] =
    Gen.oneOf(Gen.alphaChar, Gen.numChar, Gen.const('-'), Gen.const('.'), Gen.const('_'), Gen.const('~'))
  val genSubDelims: Gen[Char] = Gen.oneOf(List('!', '$', '&', '\'', '(', ')', '*', '+', ',', ';', '='))

  implicit lazy val http4sTestingArbitraryForScheme: Arbitrary[Uri.Scheme] = Arbitrary {
    Gen.frequency(
      5 -> Uri.Scheme.http,
      5 -> Uri.Scheme.https,
      1 -> scheme"HTTP",
      1 -> scheme"HTTPS",
      3 -> (for {
        head <- Gen.alphaChar
        tail <- Gen.listOf(
          Gen.frequency(
            36 -> Gen.alphaNumChar,
            1 -> Gen.const('+'),
            1 -> Gen.const('-'),
            1 -> Gen.const('.')
          )
        )
      } yield HttpCodec[Uri.Scheme].parseOrThrow(tail.mkString(head.toString, "", "")))
    )
  }

  implicit lazy val http4sTestingCogenForScheme: Cogen[Uri.Scheme] =
    Cogen[String].contramap(_.value.toLowerCase(Locale.ROOT))

  implicit lazy val http4sTestingArbitraryForTransferCoding: Arbitrary[TransferCoding] = Arbitrary {
    Gen.oneOf(
      TransferCoding.chunked,
      TransferCoding.compress,
      TransferCoding.deflate,
      TransferCoding.gzip,
      TransferCoding.identity)
  }

  implicit lazy val http4sTestingCogenForTransferCoding: Cogen[TransferCoding] =
    Cogen[String].contramap(_.coding.toLowerCase(Locale.ROOT))

  implicit lazy val http4sTestingCogenForPath: Cogen[Uri.Path] =
    Cogen[String].contramap(identity)

  implicit lazy val http4sTestingAbitraryForPath: Arbitrary[Uri.Path] = Arbitrary {
    val genSegmentNzNc =
      Gen.nonEmptyListOf(Gen.oneOf(genUnreserved, genPctEncoded, genSubDelims, Gen.const("@"))).map(_.mkString)
    val genPChar = Gen.oneOf(genUnreserved, genPctEncoded, genSubDelims, Gen.const(":"), Gen.const("@"))
    val genSegmentNz = Gen.nonEmptyListOf(genPChar).map(_.mkString)
    val genSegment = Gen.listOf(genPChar).map(_.mkString)
    val genPathEmpty = Gen.const("")
    val genPathAbEmpty = Gen.listOf(Gen.const("/") |+| genSegment).map(_.mkString)
    val genPathRootless = genSegmentNz |+| genPathAbEmpty
    val genPathNoScheme = genSegmentNzNc |+| genPathAbEmpty
    val genPathAbsolute = Gen.const("/") |+| Gen.oneOf(genPathRootless, Gen.const(Monoid[String].empty))

    Gen.oneOf(genPathAbEmpty, genPathAbsolute, genPathNoScheme, genPathRootless, genPathEmpty).map(
      identity)//Uri.Path.fromString)
  }

  

  implicit lazy val http4sTestingArbitraryForQueryParam: Arbitrary[(String, Option[String])] =
    Arbitrary {
      Gen.frequency(
        5 -> {
          for {
            k <- Arbitrary.arbitrary[String]
            v <- Arbitrary.arbitrary[Option[String]]
          } yield (k, v)
        },
        2 -> Gen.const(("foo" -> Some("bar"))) // Want some repeats
      )
    }

  implicit lazy val http4sTestingArbitraryForQuery: Arbitrary[Query] =
    Arbitrary {
      for {
        n <- Gen.size
        vs <- Gen.containerOfN[Vector, (String, Option[String])](
          n % 8,
          http4sTestingArbitraryForQueryParam.arbitrary)
      } yield Query(vs: _*)
    }

  /** https://tools.ietf.org/html/rfc3986 */
  implicit lazy val http4sTestingArbitraryForUri: Arbitrary[Uri] = Arbitrary {
    val genPChar = Gen.oneOf(genUnreserved, genPctEncoded, genSubDelims, Gen.const(":"), Gen.const("@"))
    val genScheme = Gen.oneOf(Uri.Scheme.http, Uri.Scheme.https)

    val genFragment: Gen[Uri.Fragment] =
      Gen.listOf(Gen.oneOf(genPChar, Gen.const("/"), Gen.const("?"))).map(_.mkString)

    for {
      scheme <- Gen.option(genScheme)
      authority <- Gen.option(http4sTestingArbitraryForAuthority.arbitrary)
      path <- http4sTestingAbitraryForPath.arbitrary
      query <- http4sTestingArbitraryForQuery.arbitrary
      fragment <- Gen.option(genFragment)
    } yield Uri(scheme, authority, path, query, fragment)
  }

}

object Arbitraries extends Arbitraries