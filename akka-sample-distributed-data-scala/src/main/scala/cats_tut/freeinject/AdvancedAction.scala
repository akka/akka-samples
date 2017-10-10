package cats_tut.freeinject

/**
  * https://underscore.io/blog/posts/2017/03/29/free-inject.html
  */

sealed trait AdvancedAction[A]
final case class EncryptData(key: String) extends AdvancedAction[String]
final case class DecryptData(key: String) extends AdvancedAction[String]

