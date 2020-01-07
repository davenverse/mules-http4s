package io.chrisdavenport.mules.http4s.internal

import io.chrisdavenport.vault.Vault
import org.http4s._
import fs2._
import scodec.bits.ByteVector
import cats.Functor
import cats.implicits._

final class CachedResponse private (
  val status: Status,
  val httpVersion: HttpVersion,
  val headers: Headers,
  val body: ByteVector,
  val attributes: Vault
){
  def toResponse[F[_]]: Response[F] = CachedResponse.toResponse(this)
}

object CachedResponse {

  def fromResponse[F[_]: Functor](response: Response[F])(implicit compiler: Stream.Compiler[F,F]): F[CachedResponse] = {
    response.body.compile.to(ByteVector).map{bv => 
      new CachedResponse(
        response.status,
        response.httpVersion,
        response.headers,
        bv,
        response.attributes
      )
    }
  }

  def toResponse[F[_]](cachedResponse: CachedResponse): Response[F] = 
    Response(
      cachedResponse.status,
      cachedResponse.httpVersion,
      cachedResponse.headers,
      Stream.chunk(Chunk.byteVector(cachedResponse.body)),
      cachedResponse.attributes
    )
}