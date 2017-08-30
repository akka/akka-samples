package functionalmodeling.chapter05_modularization

import scalaz._

package object free {
  type AccountRepo[A] = Free[AccountRepoF, A]
}
