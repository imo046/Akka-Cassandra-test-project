package actors

import actors.PersistentAccount.Command
import actors.PersistentAccount.Response.AccountUpdatedResponse
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}

import java.util.UUID

//"Bank" main actor

object MainActor {

  //commands = messages
  //TODO: do refactoring, move commands and responses
  import PersistentAccount.Command._
  import PersistentAccount.Response._

  //events
  case class AccountCreated(id:String) extends Event

  //state of actor
  case class State(accounts: Map[String,ActorRef[Command]]){}


  //command handler
  def commandHandler(ctx:ActorContext[Command]): (State, Command) => Effect[Event,State] = (state, command) => command match {
    //TODO: Check if user has rights to create an account
    case createCommand @ CreateAccount(userId, currency, initBalance, replyTo) => {
      //spawn new child actor (user account)
      val id = UUID.randomUUID().toString
      val newUserAccount = ctx.spawn(PersistentAccount(id), name = id)
      Effect
        .persist(AccountCreated(id))
        .thenReply(newUserAccount)(_ => createCommand)
    }
    case updateCommand @ UpdateAccount(userId, currency, amount, replyTo) =>
      state.accounts.get(userId) match {
        case Some(acc) => Effect.reply(acc)(updateCommand)
        case None => Effect.reply(replyTo)(AccountUpdatedResponse(None))
      }

    case getCommand @ GetAccount(userId, replyTo) =>
      state.accounts.get(userId) match {
        case Some(account) => Effect.reply(account)(getCommand)
        case None => Effect.reply(replyTo)(AccountGetResponse(None))
      }

  }

  //event handler (update state here!)
  private def eventHandler(ctx:ActorContext[Command]): (State, Event) => State = (state, event) => event match {
    case AccountCreated(id) => {
      val account = ctx.child(id) //exists after the command handler, but
      .getOrElse(ctx.spawn(PersistentAccount(id), name = id)) //doesn't exists in the recovery mode, so needs to be created
        .asInstanceOf[ActorRef[Command]]// OK to use since the type is already an ActorRef of Command
      state.copy(accounts = state.accounts + account)
    }
  }

  //behavior
  def apply(): Behavior[Command] = Behaviors.setup{ context =>
    EventSourcedBehavior[Command, Event, State](
      persistenceId = PersistenceId.ofUniqueId("bankActor"),
      emptyState = State(accounts = Map()),
      commandHandler = commandHandler(context),
      eventHandler = eventHandler(context)
    )
  }

}
