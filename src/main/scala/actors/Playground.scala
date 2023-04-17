package actors

import akka.NotUsed
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors

object Playground  {

  def main(args: Array[String]): Unit = {

    val rootBehavior: Behavior[NotUsed] = Behaviors.setup{ context =>
      val bank = context.spawn(MainActor(),"bank")

      //ask pattern
      


    }

  }


}
