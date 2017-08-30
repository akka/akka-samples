package cats_tut

import scala.language.higherKinds

/**
  * https://typelevel.org/cats/datatypes/freemonad.html
  */

/**
  * Create an ADT representing your grammar
  * @tparam A
  */
sealed trait KVStoreA[A]
case class Put[T](key: String, value: T) extends KVStoreA[Unit]
case class Get[T](key: String) extends KVStoreA[Option[T]]
case class Delete[T](key: String) extends KVStoreA[Unit]

/**
  * Free your ADT
  * 1. Create a type based on Free[_] and KVStoreA[_]
  * 2. Create smart constructors for KVStore[_] using liftF
  * 3. Build a program out of key-value DSL operations
  * 4. Build a compiler for programs of DSL operations
  * 5. Execute our compiler program
  */

object FreeMonads {
  /**
    * 1. Create a Free type based on your ADT
    */
  import cats.free.Free

  type KVStore[A] = Free[KVStoreA, A]

  /**
    * 2. Create smart constructors using liftF
    */
  import cats.free.Free.liftF

  // Put returns nothing (i.e. Unit)
  def put[T](key: String, value: T): KVStore[Unit] = liftF[KVStoreA, Unit](Put[T](key, value))

  // Get returns a T value
  def get[T](key: String): KVStore[Option[T]] = liftF[KVStoreA, Option[T]](Get[T](key))

  // Delete returns nothing
  def delete(key: String): KVStore[Unit] = liftF(Delete(key))

  // Update composes get and set, and returns nothing
  def update[T](key: String, f: T => T): KVStore[Unit] =for {
    vMaybe <- get[T](key)
    _ <- vMaybe.map(v => put[T](key, f(v))).getOrElse(Free.pure(()))
  } yield ()

  /**
    * 3. Build a program
    */
  def program: KVStore[Option[Int]] = for {
    _ <- put("wild-cats", 2)
    _ <- update[Int]("wild-cats", (_ + 12))
    _ <- put("tame-cats", 5)
    n <- get[Int]("wild-cats")
    _ <- delete("tame-cats")
  } yield n

  /**
    * 4. Write a compiler for your program
    */
  import cats.arrow.FunctionK
  import cats.{Id, ~>}
  import scala.collection.mutable.{Map => MMap}

  // the program will crash if a key is not found,
  // or if a type is incorrectly specified.
  def impureCompiler: KVStoreA ~> Id = new (KVStoreA ~> Id) {
    val kvs = MMap.empty[String, Any]

    override def apply[A](fa: KVStoreA[A]) = fa match {
      case Put(key, value) =>
        println(s"put($key, $value)")
        kvs(key) = value
        ()

      case Get(key) =>
        println(s"get($key)")
        kvs.get(key).map(_.asInstanceOf[A])

      case Delete(key) =>
        println(s"delete($key)")
        kvs.remove(key)
        ()
    }
  }

  /**
    * 5. Run your program
    */
  def main(args: Array[String]): Unit = {
    val result: Option[Int] = program.foldMap(impureCompiler)
    println(result)
    
    val rp = program.foldMap(pureCompiler)
    println(rp.run(Map.empty).value)
  }

  /**
    * 6. Use a pure compiler(optional)
    */
  import cats.data.State

  type KVStoreState[A] = State[Map[String, Any], A]
  val pureCompiler: KVStoreA ~> KVStoreState = new (KVStoreA ~> KVStoreState) {
    override def apply[A](fa: KVStoreA[A]): KVStoreState[A] = fa match {
      case Put(key, value) =>
        println(s"Put($key, $value) in pure")
        State.modify(_.updated(key, value))
      case Get(key) =>
        println(s"Get($key) in pure")
        State.inspect(_.get(key).map(_.asInstanceOf[A]))
      case Delete(key) =>
        println(s"Delete($key) in pure")
        State.modify(_ - key)
    }
  }

  /**
    * 7. Composing Free monads ADTs
    */
  import cats.data.Coproduct
  import cats.free.Inject
  import scala.collection.mutable.ListBuffer

  /** Handles user interaction */
  sealed trait Interact[A]
  case class Ask(prompt: String) extends Interact[String]
  case class Tell(msg: String) extends Interact[Unit]

  /** Represents persistence operations */
  sealed trait DataOp[A]
  case class AddCat(a: String) extends DataOp[Unit]
  case class GetAllCats() extends DataOp[List[String]]

  type CatsApp[A] = Coproduct[DataOp, Interact, A]

  class Interacts[F[_]](implicit I: Inject[Interact, F]) {
    def tell(msg: String): Free[F, Unit] = Free.inject[Interact, F](Tell(msg))
    def ask(prompt: String): Free[F, String] = Free.inject[Interact, F](Ask(prompt))
  }

  object Interacts {
    implicit def interacts[F[_]](implicit I: Inject[Interact, F]): Interacts[F] = new Interacts[F]
  }

  class DataSource[F[_]](implicit I: Inject[DataOp, F]) {
    def addCat(a: String): Free[F, Unit] = Free.inject[DataOp, F](AddCat(a))
    def getAllCats: Free[F, List[String]] = Free.inject[DataOp, F](GetAllCats())
  }

  object DataSource {
    implicit def dataSource[F[_]](implicit I: Inject[DataOp, F]): DataSource[F] = new DataSource[F]
  }

  def programc(implicit I: Interacts[CatsApp], D: DataSource[CatsApp]): Free[CatsApp, Unit] = {
    import I._, D._

    for {
      cat <- ask("What's the kitty's name?")
      _ <- addCat(cat)
      cats <- getAllCats
      _ <- tell(cats.toString)
    } yield ()
  }

  object ConsoleCatsInterpreter extends (Interact ~> Id) {
    override def apply[A](fa: Interact[A]) = fa match {
      case Ask(prompt) =>
        println(prompt)
        readLine()

      case Tell(msg) =>
        println(msg)
    }
  }

  object InMemoryDatasourceInterpreter extends (DataOp ~> Id) {
    private[this] val memDataSet = new ListBuffer[String]

    override def apply[A](fa: DataOp[A]) = fa match {
      case AddCat(a) =>
        memDataSet.append(a)
        ()
      case GetAllCats() =>
        memDataSet.toList
    }
  }

  val interpreter: CatsApp ~> Id = InMemoryDatasourceInterpreter or ConsoleCatsInterpreter
  
  import DataSource._
  import Interacts._

  val evaled = programc.foldMap(interpreter)
}
