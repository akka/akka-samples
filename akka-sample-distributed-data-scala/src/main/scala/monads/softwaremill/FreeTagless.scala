package monads.softwaremill

import java.util.UUID

import cats.data.Coproduct
import cats.{Monad, ~>}
import cats.implicits._
import cats.free.{Free, Inject}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global


/**
  * https://softwaremill.com/free-tagless-compared-how-not-to-commit-to-monad-too-early/
  */
case class User(id: UUID, email: String, loyaltyPoint: Int) {
  def serialize: String = id.toString + "," + loyaltyPoint + "," + email
}

object User {
  def parse(s: String): User = {
    val parts = s.split(",")
    User(UUID.fromString(parts(0)), parts(2), parts(1).toInt)
  }
}

trait KVStore {
  def get(k: String): Future[Option[String]]
  def put(k: String, v: String): Future[Unit]
}

trait UserRepository {
  def findUser(id: UUID): Future[Option[User]]
  def updateUser(u: User): Future[Unit]
}

trait EmailService {
  def sendEmail(email: String, subject: String, body: String): Future[Unit]
}

class UserRepositoryUsingKVStore(kvStore: KVStore) extends UserRepository {
  override def findUser(id: UUID) =
    kvStore.get(id.toString).map(serialized => serialized.map(User.parse))

  override def updateUser(u: User) = {
    val serialized = u.serialize
    for {
      _ <- kvStore.put(u.id.toString, serialized)
      _ <- kvStore.put(u.email, serialized)
    } yield ()
  }
}

class LoyaltyPoints(ur: UserRepository, es: EmailService) {
  def addPoints(userId: UUID, pointsToAdd: Int): Future[Either[String, Unit]] = {
    ur.findUser(userId).flatMap {
      case None => Future.successful(Left("User not found"))
      case Some(user) =>
        val updated = user.copy(loyaltyPoint = user.loyaltyPoint + pointsToAdd)
        //ur.updateUser(updated).map(_ => Right(()))
        for {
          _ <- ur.updateUser(updated)
          _ <- es.sendEmail(user.email, "Points added", s"You now have ${updated.loyaltyPoint}")
        } yield Right(())
    }
  }
}


object UseFree {

  sealed trait UserRepositoryAlg[T]
  case class FindUser(id: UUID) extends UserRepositoryAlg[Option[User]]
  case class UpdateUser(u: User) extends UserRepositoryAlg[Unit]

  sealed trait EmailAlg[T]
  case class SendEmail(email: String, subject: String, body: String) extends EmailAlg[Unit]

  trait KVAlg[T]
  case class Get(k: String) extends KVAlg[Option[String]]
  case class Put(k: String, v: String) extends KVAlg[Unit]

  type UserRepository[T] = Free[UserRepositoryAlg, T]

  type UserAndEmailAlg[T] = Coproduct[UserRepositoryAlg, EmailAlg, T]

  type KV[T] = Free[KVAlg, T]

  def get(k: String): KV[Option[String]] = Free.liftF(Get(k))
  def put(k: String, v: String): KV[Unit] = Free.liftF(Put(k, v))

  def findUser(id: UUID): UserRepository[Option[User]] = Free.liftF(FindUser(id))
  def updateUser(u: User): UserRepository[Unit] = Free.liftF(UpdateUser(u))
  
  def addPoints(userId: UUID, pointsToAdd: Int): UserRepository[Either[String, Unit]] = {
    findUser(userId).flatMap {
      case None => Free.pure(Left("User not found"))
      case Some(user) =>
        val updated = user.copy(loyaltyPoint = user.loyaltyPoint + pointsToAdd)
        updateUser(updated).map(_ => Right(()))
    }
  }

  def addPoints2(userId: UUID, pointsToAdd: Int)(
                implicit ur: Users[UserAndEmailAlg],
                es: Emails[UserAndEmailAlg]
  ): Free[UserAndEmailAlg, Either[String, Unit]] = {
    ur.findUser(userId).flatMap {
      case None => Free.pure(Left("User not found"))
      case Some(user) =>
        val updated = user.copy(loyaltyPoint = user.loyaltyPoint + pointsToAdd)

        for {
          _ <- ur.updateUser(updated)
          _ <- es.sendEmail(user.email, "Points added!", s"You now have ${updated.loyaltyPoint}")
        } yield Right(())
    }
  }

  val futureInterpreter = new (UserRepositoryAlg ~> Future) {
    override def apply[A](fa: UserRepositoryAlg[A]): Future[A] = fa match {
      case FindUser(id) =>
        /* go and talk to a db */
        Future.successful(None)

      case UpdateUser(u) =>
        /* as above */
        Future.successful(())
    }
  }

  val futureUserInterpreter = new (UserRepositoryAlg ~> Future) {
    override def apply[A](fa: UserRepositoryAlg[A]) = fa match {
      case FindUser(id) =>
        Future.successful(None)

      case UpdateUser(u) =>
        /* as above */
        Future.successful(())
    }
  }

  val futureEmailInterpreter = new (EmailAlg ~> Future) {
    override def apply[A](fa: EmailAlg[A]) = fa match {
      case SendEmail(email, subject, body) =>
        Future.successful(())
    }
  }

  val futureUserOrEmailInterpreter = futureUserInterpreter or futureEmailInterpreter

  val userToKvInterpreter = new (UserRepositoryAlg ~> KV) {
    override def apply[A](fa: UserRepositoryAlg[A]) = fa match {
      case FindUser(id) =>
        get(id.toString).map(_.map(User.parse))
      case UpdateUser(u) =>
        val serialized = u.serialize
        for {
          _ <- put(u.id.toString, serialized)
          _ <- put(u.email, serialized)
        } yield ()
    }
  }

  val kvToFutureInterpreter = new (KVAlg ~> Future) {
    override def apply[A](fa: KVAlg[A]) = fa match {
      case Get(k) => Future.successful(None)
      case Put(k, v) => Future.successful(())
    }
  }

  val result: Future[Either[String, Unit]] = addPoints(UUID.randomUUID(), 10).foldMap(futureInterpreter)

  val result2: Future[Either[String, Unit]] = addPoints2(UUID.randomUUID(), 10).foldMap(futureUserOrEmailInterpreter)

  val result3: Future[Either[String, Unit]] = addPoints(UUID.randomUUID(), 10).foldMap(userToKvInterpreter).foldMap(kvToFutureInterpreter)

  class Users[F[_]](implicit i: Inject[UserRepositoryAlg, F]){
    def findUser(id: UUID): Free[F, Option[User]] = Free.inject(FindUser(id))
    def updateUser(u: User): Free[F, Unit] = Free.inject(UpdateUser(u))
  }

  object Users {
    implicit def users[F[_]](implicit i: Inject[UserRepositoryAlg, F]): Users[F] = new Users
  }

  class Emails[F[_]](implicit i: Inject[EmailAlg, F]) {
    def sendEmail(email: String, subject: String, body: String): Free[F, Unit] =
      Free.inject(SendEmail(email, subject, body))
  }
  
  object Emails {
    implicit def emails[F[_]](implicit i: Inject[EmailAlg, F]): Emails[F] = new Emails
  }
}


object UseTagless {

  trait UserRepositoryAlg[F[_]] {
    def findUser(id: UUID): F[Option[User]]
    def updateUser(u: User): F[Unit]
  }

  trait EmailAlg[F[_]] {
    def sendEmail(email: String, subject: String, body: String): F[Unit]
  }

  trait KVAlg[F[_]] {
    def get(k: String): F[Option[String]]
    def put(k: String, v: String): F[Unit]
  }

  trait KvToFutureInterpreter extends KVAlg[Future] {
    override def get(k: String) = Future.successful(None)

    override def put(k: String, v: String) = Future.successful(())
  }

  class LoyaltyPoints[F[_]: Monad](ur: UserRepositoryAlg[F]) {
    def addPoint(userId: UUID, pointsToAdd: Int): F[Either[String, Unit]] = {
      ur.findUser(userId).flatMap {
        case None => implicitly[Monad[F]].pure(Left("User not found"))
        case Some(user) =>
          val updated = user.copy(loyaltyPoint = user.loyaltyPoint + pointsToAdd)
          ur.updateUser(updated).map(_ => Right(()))

      }
    }
  }

  class LoyaltyPoints2[F[_]: Monad](ur: UserRepositoryAlg[F], es: EmailAlg[F]) {
    def addPoint(userId: UUID, pointsToAdd: Int): F[Either[String, Unit]] = {
      ur.findUser(userId).flatMap {
        case None => implicitly[Monad[F]].pure(Left("User not found"))
        case Some(user) =>
          val updated = user.copy(loyaltyPoint = user.loyaltyPoint + pointsToAdd)
          //ur.updateUser(updated).map(_ => Right(()))
          for {
            _ <- ur.updateUser(updated)
            _ <- es.sendEmail(user.email, "Points added!", s"You now have ${updated.loyaltyPoint}")
          } yield Right(())
      }
    }
  }

  trait FutureUserInterpreter extends UserRepositoryAlg[Future] {
    override def findUser(id: UUID): Future[Option[User]] =
      Future.successful(None) /* go to db */

    override def updateUser(u: User) =
      Future.successful(())
  }

  trait FutureEmailInterpreter extends EmailAlg[Future] {
    override def sendEmail(email: String, subject: String, body: String) = Future.successful(())
  }

  class UserThroughKvInterpreter[F[_]: Monad](kv: KVAlg[F]) extends UserRepositoryAlg[F] {
    override def findUser(id: UUID) = kv.get(id.toString).map(_.map(User.parse))

    override def updateUser(u: User) = {
      val serialized = u.serialize

      for {
        _ <- kv.put(u.id.toString, serialized)
        _ <- kv.put(u.email, serialized)
      } yield ()
    }
  }

  val result: Future[Either[String, Unit]] =
    new LoyaltyPoints2(new FutureUserInterpreter {}, new FutureEmailInterpreter {}).addPoint(UUID.randomUUID(), 10)

  val result3: Future[Either[String, Unit]] =
    new LoyaltyPoints(new UserThroughKvInterpreter(new KvToFutureInterpreter {}))
    .addPoint(UUID.randomUUID(), 10)
}