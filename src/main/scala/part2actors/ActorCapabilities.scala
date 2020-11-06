package part2actors

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import part2actors.ActorCapabilities.Person.LiveTheLife

object ActorCapabilities extends App {

  class SimpleActor extends Actor {
    override def receive: Receive = {
      case "Hi" => sender() ! "Hello, there!" // replying to a message, context.sender() or sender can identified the ref of who send the message
      case message: String => println(s"[${self}] I have received $message")
      case number: Int => println(s"[simple actor] I have received a number: $number")
      case SpecialMessage(contents) => println(s"[simple actor] I have received something special: $contents")
      case SendMessageToYourself(content) =>
        self ! content
      case SayHiTo(ref) => ref ! "Hi" // alice is being passed as the sender, not keeping the original sender
      case WirelessPhoneMessage(content, ref) => ref forward (content + "s") // i keep the original sender of the WPM
    }
  }

  val system = ActorSystem("actorCapabilitiesDemo")
  val simpleActor = system.actorOf(Props[SimpleActor], "simpleActor")

  simpleActor ! "hello, actor"

  // 1 - messages can be of any type
  // a) messages must me IMMUTABLE
  // b) messages must be SERIALIZABLE
  // there are a lot of ways to make messages serializable
  // in practice we should use case classes and case objects

  simpleActor ! 42

  case class SpecialMessage(contents: String)
  simpleActor ! SpecialMessage("some special content")

  // 2 - actors have information about their context and about themselves
  // context.system
  // context.self // equivalent to this in OOP, it's the name and its identified or only self

  case class SendMessageToYourself(content: String)
  simpleActor ! SendMessageToYourself("I'm an actor and I am proud of it")

  // 3 - actors can REPLY to messages, by using their context
  val alice = system.actorOf(Props[SimpleActor], "alice")
  val bob = system.actorOf(Props[SimpleActor], "bob")

  case class SayHiTo(ref: ActorRef)
  alice ! SayHiTo(bob)

  // 4 - deadLetters
  alice ! "Hi" // because no sender is set, sender = null and when there's no sender, deadLetters is a fake actor inside akka when messages are not sent to anyone

  // 5 - forwarding messages
  // D -> A -> B
  // forwarding a message with the ORIGINAL sender, we keep the original sender

  case class WirelessPhoneMessage(content: String, ref: ActorRef)
  alice ! WirelessPhoneMessage("Hi", bob) // no sender

  /**
   * Exercices
   *
   * 1. a Counter actor(int counter - internal variable)
   *    - Increment
   *    - Decrement
   *    - Print - internal counter
   *
   *  2. a Bank Account as an actor
   *     receives
   *     - Deposit an amount
   *     - Withdraw an amount
   *     - Statement
   *
   *     replies - answer to operations Deposit and WithDraw with Success/Failure
   *     - Success
   *     - Failure
   *
   *     internal variable to hold the funds
   *     test - interact with some other kind of actor to interpret the success and the failure
   */

  // 1.
  val counterSystem = ActorSystem("counterSystem")

  // DOMAIN of the counter
  object Counter {
    case object Increment
    case object Decrement
    case object Print
  }

  class Counter extends Actor {
    import Counter._

    var count = 0

    override def receive: Receive = {
      case Increment => count += 1
      case Decrement => count -= 1
      case Print => println(s"[counter] The counter is: $count")
    }
  }

  import Counter._
  val counter = counterSystem.actorOf(Props[Counter], "myCounter")

  (1 to 5).foreach(_ => counter ! Increment)
  (1 to 3).foreach(_ => counter ! Decrement)
  counter ! Print

  // 2.
  val bankSystem = ActorSystem("bankSystem")

  object BankAccount {
    case class Deposit(amount: Int)
    case class Withdraw(amount: Int)
    case object Statement

    case class TransactionSuccess(message: String)
    case class TransactionFailure(reason: String)
  }

  class BankAccount extends Actor {
    import BankAccount._

    var funds = 0

    override def receive: Receive = {
      case Deposit(amount) =>
        if (amount < 0) sender() ! TransactionFailure("Invalid deposit amount")
        else {
          funds += amount
          sender() ! TransactionSuccess(s"Successfully deposit $amount")
        }
      case Withdraw(amount) =>
        if (amount < 0) sender() ! TransactionFailure("Invalid withdraw amount")
        else if (amount > funds) sender() ! TransactionFailure("Insufficient funds")
        else {
          funds -= amount
          sender() ! TransactionSuccess(s"Successfully withdrew $amount")
        }
      case Statement => sender() ! s"Your balance is $funds"
    }
  }

  object Person {
    case class LiveTheLife(account: ActorRef)
  }
  class Person extends Actor {
    import Person._
    import BankAccount._

    override def receive: Receive = {
      case LiveTheLife(account) =>
        account ! Deposit(10000)
        account ! Withdraw(90000)
        account ! Withdraw(500)
        account ! Statement
      case message => println(message.toString)
    }
  }

  val bankAccount = bankSystem.actorOf(Props[BankAccount], "myBankAccount")
  val john = bankSystem.actorOf(Props[Person], "john")

  john ! LiveTheLife(bankAccount)

}
