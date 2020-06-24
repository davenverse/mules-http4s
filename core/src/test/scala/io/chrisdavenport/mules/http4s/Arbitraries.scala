package io.chrisdavenport.mules.http4s

import org.scalacheck._
import scodec.bits.ByteVector
import org.http4s._
import org.http4s.util.CaseInsensitiveString
import java.time._

trait Arbitraries {
    implicit val arbitraryByteVector: Arbitrary[ByteVector] =
    Arbitrary(Gen.containerOf[Array, Byte](Arbitrary.arbitrary[Byte]).map(ByteVector(_)))
  
  implicit val arbStatus: Arbitrary[Status] = 
    Arbitrary{
      Gen.choose(100, 599).map(Status.fromInt(_).fold(throw _, identity)) // Safe because we are in valid range
    }

  implicit val arbHttpVersion : Arbitrary[HttpVersion] = Arbitrary {
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

  implicit val http4sTestingArbitraryForRawHeader: Arbitrary[Header.Raw] =
    Arbitrary {
      for {
        token <- genToken
        value <- genFieldValue
      } yield Header.Raw(CaseInsensitiveString(token), value)
    }

  implicit val headers: Arbitrary[Headers]  = Arbitrary(Gen.listOf(Arbitrary.arbitrary[Header.Raw]).map(Headers(_)))

  implicit val arbCachedResponse: Arbitrary[CachedResponse] = Arbitrary(
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

  implicit val arbHttpDate: Arbitrary[HttpDate] = Arbitrary(genHttpDate)
  
  implicit val arbCacheItem = Arbitrary(
    for {
      created <- Arbitrary.arbitrary[HttpDate]
      expires <- Arbitrary.arbitrary[Option[HttpDate]]
      cachedResponse <- Arbitrary.arbitrary[CachedResponse]
    } yield CacheItem(created, expires, cachedResponse)
  )
}

object Arbitraries extends Arbitraries