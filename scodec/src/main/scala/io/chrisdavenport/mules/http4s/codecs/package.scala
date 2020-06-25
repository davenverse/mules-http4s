package io.chrisdavenport.mules.http4s

import cats.implicits._
import _root_.scodec.interop.cats._
import _root_.scodec._
import _root_.scodec.codecs._
import org.http4s._
import java.nio.charset.StandardCharsets

package object codecs {
  private[codecs] val statusCodec : Codec[Status] = int16.exmap(
    i => Attempt.fromEither(Status.fromInt(i).leftMap(p => Err.apply(p.details))),
    s => Attempt.successful(s.code)
  )

  private[codecs] val httpVersionCodec: Codec[HttpVersion] = {
    def decode(major: Int, minor: Int): Attempt[HttpVersion] = 
      Attempt.fromEither(HttpVersion.fromVersion(major, minor).leftMap(p => Err.apply(p.message)))
    (int8 ~ int8).exmap(
      decode,
      httpVersion => Attempt.successful(httpVersion.major -> httpVersion.minor )
    )
  }

  private[codecs] val headersCodec : Codec[Headers] = {
    cstring.exmapc{
      s => 
        if (s.isEmpty()) 
          Attempt.successful(Headers.empty)
        else
          s.split("\r\n").toList.traverse{line => 
            val idx = line.indexOf(':')
            if (idx >= 0) {
              Attempt.successful(Header(line.substring(0, idx), line.substring(idx + 1).trim))
            } else Attempt.failure[Header](Err(s"No : found in Header - $line"))
          }.map(Headers(_))
        
    }{h => 
      Attempt.successful(
        h.toList.map(h => s"${h.name.toString()}:${h.value}")
          .intercalate("\r\n")
      )
    }
  }

  private[codecs] val httpDateCodec: Codec[HttpDate] = 
    int64.exmapc(i => Attempt.fromEither(HttpDate.fromEpochSecond(i).leftMap(e => Err(e.details))))(
      date => Attempt.successful(date.epochSecond)
    )

  private[codecs] val method: Codec[Method] = cstring.exmapc(s => 
      Attempt.fromEither(Method.fromString(s).leftMap(p => Err.apply(p.details)))
    )(m => Attempt.successful(m.name))

  private[codecs] val uri : Codec[Uri] = variableSizeBytesLong(int64, string(StandardCharsets.UTF_8))
      .withToString(s"string64(${StandardCharsets.UTF_8.displayName()})")
      .exmapc(
      s => Attempt.fromEither(Uri.fromString(s).leftMap(p => Err.apply(p.details)))
  )(uri =>  Attempt.successful(uri.renderString))

  val keyTupleCodec : Codec[(Method, Uri)] = method ~ uri

  val cachedResponseCodec : Codec[CachedResponse] = 
    (statusCodec :: httpVersionCodec :: headersCodec :: bytes).as[CachedResponse]

  val cacheItemCodec: Codec[CacheItem] = (httpDateCodec :: optional(bool, httpDateCodec) :: cachedResponseCodec).as[CacheItem]
}