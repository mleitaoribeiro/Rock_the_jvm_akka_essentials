package part2actors

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import part2actors.ChangingActorBehavior.Citizen.VoteStatusRequest

object ChangingActorBehavior extends App {

  object FussyKid {
    case object KidAccept
    case object KidReject
    val HAPPY = "happy"
    val SAD = "sad"
  }
  class FussyKid extends Actor {
    import FussyKid._
    import Mom._

    var state = HAPPY // we should not use var, because they're not immutable => we have to found another solution
    override def receive: Receive = {
      case Food(VEGETABLE) => state = SAD
      case Food(CHOCOLATE) => state = HAPPY
      case Ask(_) => // here because it's a simple interaction we only have 2 lines, but in major apps it could be 100S!!!! => BAD
        if(state == HAPPY) sender() ! KidAccept
        else sender() ! KidReject
    }
  }

  // stateless FussyKid
  class StatelessFussyKid extends Actor {
    import FussyKid._
    import Mom._

    override def receive: Receive = {
      happyReceive
    }

    def happyReceive: Receive = {
      case Food(VEGETABLE) => context.unbecome()
      case Food(CHOCOLATE) => context.become(happyReceive, false) // becomes less sad or happy depending if it was happy or sad before
      case Ask(_) => sender() ! KidAccept                                   // change my receive handler to happyReceive, with true or false to discard old message handler
    }                                                                       // if false, stack.push(happyReceive)

    def sadReceive: Receive = {
      case Food(VEGETABLE) => context.become(sadReceive, false)
      case Food(CHOCOLATE) => context.unbecome()
      case Ask(_) => sender() ! KidReject
    }
  }

  object Mom {
    case class MomStart(kidRef: ActorRef)
    case class Food(food: String)
    case class Ask(message: String) // do you want to play
    val VEGETABLE = "veggies"
    val CHOCOLATE = "chocolate"
  }
  class Mom extends Actor {
    import FussyKid._
    import Mom._

    override def receive: Receive = {
      case MomStart(kidRef) =>
        // test our interaction
        kidRef ! Food(VEGETABLE)
        kidRef ! Food(VEGETABLE)
        kidRef ! Food(CHOCOLATE)
        kidRef ! Food(CHOCOLATE)
        kidRef ! Ask("do you wanna play?")
      case KidAccept => println("Yay, my kid is happy!")
      case KidReject => println("My kid is sad, but at least he's healthy!")
    }
  }

  import Mom._

  val system = ActorSystem("changingActorBehaviorDemo")
  val fussyKid = system.actorOf(Props[FussyKid])
  val statelessFussyKid = system.actorOf(Props[StatelessFussyKid])
  val mom = system.actorOf(Props[Mom])

  mom ! MomStart(statelessFussyKid)

  /*
    ALGORITHM:

    mom receives MomStart
      kid receives Food(veg) -> kid will change the handler to sadReceive
      kid receives Ask(play?) -> kid replies with the sadReceive handler =>
    mom receives KidReject
   */

  /*
    context.become
      if true, like first implementation
        Food(veg) -> become sadReceive
        Food(chocolate) -> become happyReceive

      if false
        Food(veg) -> stack.push(sadReceive)
        Food(chocolate) -> stack.push(happyReceive)

        Stack: // every time the receive is used, the first method in the stack is called, if empty it chooses the general receive
        1. happyReceive
        2. sadReceive
        3. happyReceive
  */

  /*
    new behavior with context.unbecome
      Stack:
      1. happyReceive

      Food(veg)
      Stack:
      1. sadReceive
      2. happyReceive

      Food(veg)
      Stack:
      1. sadReceive
      2. sadReceive
      3. happyReceive

      Food(choco)
      Stack:
      1. sadReceive
      2. happyReceive

      Food(choco)
      Stack:
      1. happyReceive
   */

  /**
   * Exercises:
   * 1 - recreate the Counter Actor with context.become and NO MUTABLE state
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

    override def receive: Receive = {
      countReceive(0)
    }

    def countReceive(currentCount: Int): Receive = {
      case Increment =>
        println(s"[countReceive($currentCount)] incrementing")
        context.become(countReceive(currentCount + 1))
      case Decrement =>
        println(s"[countReceive($currentCount)] decrementing")
        context.become(countReceive(currentCount - 1))
      case Print => println(s"[countReceive($currentCount)] The counter is: $currentCount")
    }

  }

  import Counter._
  val counter = counterSystem.actorOf(Props[Counter], "myCounter")

  (1 to 5).foreach(_ => counter ! Increment)
  (1 to 3).foreach(_ => counter ! Decrement)
  counter ! Print

  /**
  * 2 - simplified voting system
  */

  object Citizen {
    case class Vote(candidate: String)
    case object VoteStatusRequest
    case class VoteStatusReply(candidate: Option[String])
  }
  class Citizen extends Actor { // after a vote, the citizen is marked as voted and the candidate - sending the vote to the class citizen (1x) and turns voted instead
    import Citizen._                                                                                // to a state of vote

    override def receive: Receive = {
      case Vote(c) => context.become(voted(c))
      case VoteStatusRequest => sender() ! VoteStatusReply(None)
    }

    def voted(candidate: String): Receive = {
      case VoteStatusRequest => sender() ! VoteStatusReply(Some(candidate))
    }
  }

  object VoteAggregator {
    case class AggregatesVotes(citizens: Set[ActorRef]) // the citizens we want to ask
  }
  class VoteAggregator extends Actor {
    // VoteAggregator is going to ask Citizen in which candidate the citizen voted
    // VoteAggregator asks to every Citizen in the set the VoteStatusRequest

    import Citizen._
    import VoteAggregator._

    override def receive: Receive = {
      awaitingCommand
    }

    def awaitingCommand: Receive = {
      case AggregatesVotes(citizens) =>
        citizens.foreach(citizenRef => citizenRef ! VoteStatusRequest)
        context.become(awaitingStatus(citizens, Map()))
    }

    def awaitingStatus(stillWaiting: Set[ActorRef], currentStats: Map[String, Int]): Receive = {
      case VoteStatusReply(None) =>
        // a citizen hasn't voted yet
        sender() ! VoteStatusRequest // probably an infinite loop
      case VoteStatusReply(Some(candidate)) =>
        val newStillWaiting = stillWaiting - sender()
        val currentVotesOfCandidate = currentStats.getOrElse(candidate, 0)
        val newStats = currentStats + (candidate -> (currentVotesOfCandidate+1))
        if (newStillWaiting.isEmpty) {
          println(s"[aggregator] poll stats: $newStats")
        } else {
          // still need to process some statuses
          context.become(awaitingStatus(newStillWaiting, newStats))
        }
    }
  }

  import Citizen._
  import VoteAggregator._

  val alice = system.actorOf(Props[Citizen])
  val bob = system.actorOf(Props[Citizen])
  val charlie = system.actorOf(Props[Citizen])
  val daniel = system.actorOf(Props[Citizen])

  alice ! Vote("Martin")
  bob ! Vote("Jonas")
  charlie ! Vote("Roland")
  daniel ! Vote("Roland")

  val voteAggregator = system.actorOf(Props[VoteAggregator])
  voteAggregator ! AggregatesVotes(Set(alice, bob, charlie, daniel))

  /*
    END RESULT
    Print the status of the votes

    Martin -> 1
    Jonas -> 1
    Roland -> 2
   */

}
