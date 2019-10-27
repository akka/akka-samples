/*
 * Copyright (C) 2009-2019 Lightbend Inc. <https://www.lightbend.com>
 */package sample.cluster.factorial

import akka.actor.typed.ActorSystem
import akka.actor.typed.Terminated
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.ClusterEvent.MemberUp
import akka.cluster.typed.Cluster
import akka.cluster.typed.Subscribe
import com.typesafe.config.ConfigFactory

object FactorialFrontend {
  def main(args: Array[String]): Unit = {
    val upToN = 200

    val config = ConfigFactory.parseString("akka.cluster.roles = [frontend]").
      withFallback(ConfigFactory.load("factorial"))


    val rootBehavior = Behaviors.setup[MemberUp] { ctx =>
      ctx.log.info("Factorials will start when 2 backend members in the cluster.")

      // delay until member up
      val cluster = Cluster(ctx.system)
      cluster.subscriptions ! Subscribe(ctx.self, classOf[MemberUp])

      Behaviors.receiveMessage {
        case MemberUp(member) =>
          if (member == cluster.selfMember) {
            ctx.log.info("Starting factorial calculation.")
            val session = ctx.spawn(FactorialSession(upToN, true), "FactorialFrontend")

            // switch to a new behavior that stops this root behavior and
            // therefore the ActorSystem when requestor stops
            ctx.watch(session)
            Behaviors.receiveSignal {
              case (_, Terminated(session)) =>
                ctx.log.info("Factorial calculation completed, stopping.")
                Behaviors.stopped
            }
          } else {
            Behaviors.same
          }
      }
    }

    val system = ActorSystem(rootBehavior, "ClusterSystem", config)

  }
}
