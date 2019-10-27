/*
 * Copyright (C) 2009-2019 Lightbend Inc. <https://www.lightbend.com>
 */package sample.cluster.factorial

import akka.actor.typed.ActorSystem
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.receptionist.ServiceKey
import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.config.ConfigFactory

object FactorialBackend {

  val FactorialServiceKey = ServiceKey[Calculator.CalculateFactorial]("factorials")

  def main(args: Array[String]): Unit = {
    // Override the configuration of the port when specified as program argument
    val port = if (args.isEmpty) "0" else args(0)
    // Override the configuration of the port and the node role
    val config = ConfigFactory.parseString(s"""
        akka.remote.artery.canonical.port=$port
        akka.cluster.roles = [backend]
      """)
      .withFallback(ConfigFactory.load("factorial"))

    val rootBehavior = Behaviors.setup[Nothing] { ctx =>
      val numberOfWorkers = ctx.system.settings.config.getInt("factorial.workers-per-node")
      (0 to numberOfWorkers).foreach { n =>
        val calculator = ctx.spawn(Calculator(), s"FactorialCalculator$n")
        ctx.system.receptionist ! Receptionist.Register(FactorialServiceKey, calculator)
      }
      Behaviors.empty
    }

    val system = ActorSystem[Nothing](rootBehavior, "ClusterSystem", config)

  }
}