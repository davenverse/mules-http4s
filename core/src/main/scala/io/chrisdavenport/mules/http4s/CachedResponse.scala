package io.chrisdavenport.mules.http4s

import io.chrisdavenport.vault.Vault
import org.http4s._
import fs2._

import cats._
import cats.implicits._
import scodec._
import scodec.bits.ByteVector
import scodec.bits._
import scodec.interop.cats._
import codecs._

// As attributes can be unbound. We cannot cache them as they may not be safe to do so.
final case class CachedResponse(
  status: Status,
  httpVersion: HttpVersion,
  headers: Headers,
  body: ByteVector
){
  def withHeaders(headers: Headers): CachedResponse = new CachedResponse(
    this.status,
    this.httpVersion,
    headers,
    this.body
  )
  def toResponse[F[_]]: Response[F] = CachedResponse.toResponse(this)
}

object CachedResponse {

  private[http4s] val statusCodec : Codec[Status] = int16.exmap(
    i => Attempt.fromEither(Status.fromInt(i).leftMap(p => Err.apply(p.details))),
    s => Attempt.successful(s.code)
  )

  private[http4s] val httpVersionCodec: Codec[HttpVersion] = {
    def decode(major: Int, minor: Int): Attempt[HttpVersion] = 
      Attempt.fromEither(HttpVersion.fromVersion(major, minor).leftMap(p => Err.apply(p.message)))
    (int8 ~ int8).exmap(
      decode,
      httpVersion => Attempt.successful(httpVersion.major -> httpVersion.minor )
    )
  }

  private[http4s] val headersCodec : Codec[Headers] = {
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

  val codec : Codec[CachedResponse] = 
    (statusCodec :: httpVersionCodec :: headersCodec :: bytes).as[CachedResponse]

  def fromResponse[F[_], G[_]: Functor](response: Response[F])(implicit compiler: Stream.Compiler[F,G]): G[CachedResponse] = {
    response.body.compile.to(ByteVector).map{bv =>
      new CachedResponse(
        response.status,
        response.httpVersion,
        response.headers,
        bv
      )
    }
  }

  def toResponse[F[_]](cachedResponse: CachedResponse): Response[F] = 
    Response(
      cachedResponse.status,
      cachedResponse.httpVersion,
      cachedResponse.headers,
      Stream.chunk(Chunk.byteVector(cachedResponse.body)),
      Vault.empty
    )
}