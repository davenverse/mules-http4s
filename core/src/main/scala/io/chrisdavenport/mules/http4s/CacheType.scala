package io.chrisdavenport.mules.http4s

sealed trait CacheType {
  def isShared: Boolean = this match {
    case CacheType.Private => false
    case CacheType.Public => true
  }
}
object CacheType {
  case object Public extends CacheType
  case object Private extends CacheType
}