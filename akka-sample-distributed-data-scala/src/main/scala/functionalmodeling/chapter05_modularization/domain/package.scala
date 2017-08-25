package functionalmodeling.chapter05_modularization

import scalaz._
import Scalaz._

package object domain {
  type Valid[A] = NonEmptyList[String] \/ A
}
