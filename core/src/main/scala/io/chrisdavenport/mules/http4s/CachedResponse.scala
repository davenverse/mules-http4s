package io.chrisdavenport.mules.http4s

import org.typelevel.vault.Vault
import org.http4s._
import fs2._

import cats._
import cats.implicits._
import scodec.bits.ByteVector

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

  def fromResponse[F[_], G[_]: Functor](response: Response[F])(implicit compiler: Compiler[F,G]): G[CachedResponse] = {
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