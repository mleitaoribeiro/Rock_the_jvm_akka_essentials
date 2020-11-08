package part3Testing

import scala.util.Random

import akka.actor.{Actor, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

import scala.concurrent.duration._

class BasicSpec extends TestKit(ActorSystem("BasicSpec")) // when we run the tests, is automatically instantiated this actor system
  with ImplicitSender // sen reply scenarios in actors
  with WordSpecLike // a trait from scala test, allows the description of test in a natural way
  with BeforeAndAfterAll // hooks to be called before and after the tests
{
  // setup
  override def afterAll(): Unit = { // terminate the actor system
    TestKit.shutdownActorSystem(system) // system is a member of TestKit
  }

  /* pattern from Scala Test
  "The being tested" should { // -> this is the test suite
    "do this" in { // these are tests
      // testing scenario
    }

    "do another thing" in {
      // testing scenario
    }
  }*/

  import BasicSpec._

  "A simple actor" should {
    "send back the same message" in {
      val echoActor = system.actorOf(Props[SimpleActor])
      val message = "hello, test"
      echoActor ! message // who is the sender()? testActor, as the implicit tender -> extend ImplicitSender trait

      expectMsg(message) // who is expecting? -> testActor, belongs to TestKit
    }
  }

  "A blackHole actor" should {
    "send back some message" in {
      val blackHole = system.actorOf(Props[BlackHole])
      val message = "hello, test"
      blackHole ! message

      // expectMsg(message) // fails because no message was received in the default time out, to change this akka.test.single-expect-default
      expectNoMsg(1 second)

    }
  }

  // message assertions
  "A LabTestActor" should {
    val labTestActor = system.actorOf(Props[LabTestActor]) // to not instantiate always the same actor, but if we want to clear we have to put it inside the test
    "turn a string into uppercase" in {
      labTestActor ! "I love Akka"
      // expectMsg("I LOVE AKKA")
      val reply = expectMsgType[String]

      assert(reply == "I LOVE AKKA")
    }

    "reply to a greeting" in {
      labTestActor ! "greeting"
      expectMsgAnyOf("hi", "hello")
    }

    "reply with favorite tech" in {
      labTestActor ! "favoriteTech"
      expectMsgAllOf("Scala", "Akka")
    }

    "reply with cool tech in a different way" in {
      labTestActor ! "favoriteTech"
      val messages = receiveN(2) // Seq[Any]

      // free to do more complicated assertions
    }

    "reply with cool tech in a fancy way" in {
      labTestActor ! "favoriteTech"

      expectMsgPF() {
        case "Scala" => // only care that the PF is defined
        case "Akka" =>
      }
    }
  }

}

object BasicSpec { // all the information, objects and information needed for the test

  class SimpleActor extends Actor {
    override def receive: Receive = {
      case message => sender() ! message
    }
  }

  class BlackHole extends Actor {
    override def receive: Receive = Actor.emptyBehavior
  }

  class LabTestActor extends Actor {
    val random = new Random(

    )

    override def receive: Receive = {
      case "greeting" =>
        if (random.nextBoolean()) sender() ! "hi"
        else sender() ! "hello"
      case "favoriteTech" =>
        sender() ! "Scala"
        sender() ! "Akka"
      case message: String => sender() ! message.toUpperCase()
    }
  }
}