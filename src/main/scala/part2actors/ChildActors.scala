package part2actors

import akka.actor.{Actor, ActorRef, ActorSystem, Props}

object ChildActors extends App {

  // Actors can create other actors
  object Parent {
    case class CreateChild(name: String)
    case class TellChild(message: String)
  }
  class Parent extends Actor {
    import Parent._

    override def receive: Receive = {
      case CreateChild(name) =>
        println(s"${self.path} creating child")
        // create a new actor right HERE
        val childRef = context.actorOf(Props[Child], name)
        context.become(withChild(childRef))
    }

    def withChild(childRef: ActorRef): Receive = {
      case TellChild(message) =>
        childRef forward message
    }
  }

  class Child extends Actor{
    override def receive: Receive = {
      case message => println(s"${self.path} I got: $message")
    }
  }

  import Parent._

  val system = ActorSystem("ParentChildDemo")
  val parent = system.actorOf(Props[Parent], "parent")
  parent ! CreateChild("child")
  parent ! TellChild("hey Kid")

  // actor hierarchies
  // parent -> child -> grandChild
  //        -> child2 ->

  /*
    Guardian actors (top-level)
    - /system = system guardian -> manage various things, e.g., logging system
    - /user = user-level guardian -> top level guardian actor, manages every single actor created
    - / = the root guardian, manages the system and user guardians, if someone dies here, everything dies
   */

  /**
   * Actor selection - feature offered to find an actor by a path
   */
  val childSelection = system.actorSelection("/user/parent/child") // type ActorSelection is a wrapper over a ActorRef
  childSelection ! "I found you!" // we can send a message to an actor by knowing a path
  // if the path is not valid, the message is send to deadLetters
  // use this when we want to find an actor that is deep in the hierarchy

  /**
   * Danger!!!
   *
   * NEVER PASS MUTABLE ACTOR STATES, OR THE "THIS" REFERENCE, TO CHILD ACTORS
   *
   * NEVER IN YOUR LIFE - BREAKS ENCAPSULATION!!!
   */
  //EXAMPLE

  object NaiveBankAccount {
    case class Deposit(amount: Int)
    case class Withdraw(amount: Int)
    case object InitializeAccount
  }
  class NaiveBankAccount extends Actor {
    import NaiveBankAccount._
    import CreditCard._

    var amount = 0

    override def receive: Receive = {
      case InitializeAccount =>
        val creditCardRef = context.actorOf(Props[CreditCard], "card")
        creditCardRef ! AttachToAccount(this) //!!!
      case Deposit(funds) => deposit(funds)
      case Withdraw(funds) => withdraw(funds)
    }

    def deposit(funds: Int) = {
      println(s"${self.path} depositing $funds on top of $amount")
      amount += funds
    }
    def withdraw(funds: Int) = {
      println(s"${self.path} withdrawing $funds from $amount")
      amount -= funds
    }
  }

  object CreditCard {
    case class AttachToAccount(bankAccount: NaiveBankAccount) //!!
    case object CheckStatus
  }
  class CreditCard extends Actor {
    import CreditCard._

    override def receive: Receive = {
      case AttachToAccount(account) => context.become(attachedTo(account))
    }

    def attachedTo(account: NaiveBankAccount): Receive = {
      case CheckStatus =>
        println(s"$self.path your message has been processed")
        // benign
        account.withdraw(1) //because I can
    }
  }

  import NaiveBankAccount._
  import CreditCard._

  val bankAccountRef = system.actorOf(Props[NaiveBankAccount], "account")
  bankAccountRef ! InitializeAccount
  bankAccountRef ! Deposit(100)

  Thread.sleep(500)
  val ccSelection = system.actorSelection("/user/account/card")
  ccSelection ! CheckStatus

  // this implementation is WRONG!!!! because there's an action being done on bankAccount without a message being send
  // to bank account - because we are passing the logic of an actor outside his capsule - security issues
  // to correct this we cannot pass bankAccount: BankAccount in an argument but we should pass bankAccountRef: ActorRef
      // with this we are breaking the Akka principles
  // this makes possible to call methods from an Actor in another actor -> WRONG

  // this is called CLOSING OVER
  // Never close over mutable sate or "this"

}
