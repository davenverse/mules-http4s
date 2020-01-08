package io.chrisdavenport.mules.http4s

/**
 * CacheTypes are in 2 flavors, private caches which are specifically
 * for a single user, or public caches which can be used for multiple
 * users. Private caches can cache information set to Cache-Control: private, 
 * whereas public caches are not allowed to cache that information
 **/
sealed trait CacheType {
  /**
   * Whether or not a Cache is Shared,
   * public caches are shared, private caches
   * are not
   **/
  def isShared: Boolean = this match {
    case CacheType.Private => false
    case CacheType.Public => true
  }
}
object CacheType {
  case object Public extends CacheType
  case object Private extends CacheType
}