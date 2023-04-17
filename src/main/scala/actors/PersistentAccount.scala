package actors
import akka.actor.typed.ActorRef
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}

/*
Account Actor,
can be:
 - created
 - updated
 - deleted

 has:
  - messages
  - events
  - states
 */

object PersistentAccount {

  import Command._
  import Response._

  //commands
  sealed trait Command
  object Command {
    case class CreateAccount(
                              userId: String,
                              currency: String,
                              initBalance: Double, //for convenience
                              replyTo: ActorRef[Response] //for storing the responses
                            ) extends Command

    case class UpdateAccount(
                              userId: String,
                              currency: String,
                              amount: Double,
                              replyTo: ActorRef[Response]
                            ) extends Command

    case class GetAccount(
                           userId: String,
                           replyTo: ActorRef[Response]
                         ) extends Command
  }


  //events
  case class AccountCreated(acc:Account) extends Event
  case class AccountUpdated(amount: Double) extends Event


  //state
  case class Account(id: String, name:String, currency: String, balance: Double)

  //responses
  sealed trait Response
  object Response {
    case class AccountCreatedResponse(id: String) extends Response

    case class AccountUpdatedResponse(accountOption: Option[Account]) extends Response

    case class AccountGetResponse(accountOption: Option[Account]) extends Response
  }



  //Actor
  //command handler = message handler => persist an event
  //event handler => update state
  //state

  val commandHandler: (Account, Command) => Effect[Event,Account] = (state, command) => command match {
    case CreateAccount(user, currency, initBalance, replyTo) => {
      val id = state.id
      /*
       - bank creates an account
       - sends CreateAccount
       - I persist AccountCreated
       - I update state
       - reply back with AccountCreatedResponse
       - app surfaces the response to HTTP server
       */
      Effect.persist(AccountCreated(Account(id,user,currency, balance = initBalance))) //persisted into Cassandra
        .thenReply(replyTo)(_ => AccountCreatedResponse(id))
    }
    case UpdateAccount(_,_,amount, replyTo) => {
      if (amount < 0 && math.abs(amount) > state.balance) {
        Effect.reply(replyTo)(AccountUpdatedResponse(None))
      }
      else {
        val updated = state.balance + amount
        Effect
          .persist(AccountUpdated(updated))
          .thenReply(replyTo)(newState => AccountUpdatedResponse(Some(newState)))
      }
    }
    case GetAccount(_, replyTo) =>
      Effect.reply(replyTo)(AccountGetResponse(Some(state)))
  }
  private val eventHandler: (Account, Event) => Account = (state, event) => event match {
    case AccountCreated(acc) => acc
    case AccountUpdated(updatedBalance) => state.copy(balance = updatedBalance)
  }


  def apply(id: String) = {
    EventSourcedBehavior[Command,Event,Account](
      persistenceId = PersistenceId.ofUniqueId(id),
      emptyState = Account(id,"anonymous","",0.0), //unused
      commandHandler = commandHandler,
      eventHandler = eventHandler
    )
  }



}
