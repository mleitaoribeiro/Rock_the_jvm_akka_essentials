package part3Testing

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

class TestProbeSpec extends TestKit(ActorSystem("TestProbeSpec"))
  with ImplicitSender
  with WordSpecLike
  with BeforeAndAfterAll {

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  import TestProbeSpec._

  "A master actor" should {
    "register a slave" in {
      val master = system.actorOf(Props[Master])
      val slave = TestProbe("slave") // simulates the slave

      master ! Register(slave.ref)
      expectMsg(RegistrationAck)
    }

    "send the work to the slave actor" in {
      val master = system.actorOf(Props[Master])
      val slave = TestProbe("slave") // fictitious, can be instructed to send or replied messages!!!! and then we can test master
      master ! Register(slave.ref)
      expectMsg(RegistrationAck)

      val workLoadString = "I love akka"
      master ! Work(workLoadString)

      // testing the interaction between the master and the slave actor
      slave.expectMsg(SlaveWork(workLoadString, testActor)) // some assertions as test actor
      slave.reply(WorkCompleted(3, testActor))

      expectMsg(Report(3)) // test actor is the replier so we don't use slave, it receives the Report(3)
    }

    "aggregate data correctly" in {
      val master = system.actorOf(Props[Master])
      val slave = TestProbe("slave")
      master ! Register(slave.ref)
      expectMsg(RegistrationAck)

      val workLoadString = "I love akka"
      master ! Work(workLoadString)
      master ! Work(workLoadString)

      // in the meantime I don't have a slave actor
      slave.receiveWhile() {
        case SlaveWork(workloadString, testActor) => slave.reply(WorkCompleted(3, testActor))
      }
      expectMsg(Report(3))
      expectMsg(Report(6))

    }
  }

}

object TestProbeSpec {
  // scenario
  /*
    word counting actor hierarchy master-slave

    send some work to the master
      - master sends the slave the piece of work
      - slave processes the work and replies to master
      - master aggregates the result
    master sends the total count to the original requester
   */

  case class Register(slaveRef: ActorRef)
  case class Work(text: String)
  case class SlaveWork(text: String, originalRequester: ActorRef)
  case class WorkCompleted(count: Int, originalRequester: ActorRef)
  case class Report(totalCount: Int)
  case object RegistrationAck

  // Actor suppose to test
  class Master extends Actor {
    override def receive: Receive = {
      case Register(slaveRef) =>
        sender() ! RegistrationAck
        context.become(online(slaveRef, 0))
      case _ => //ignore
    }

    def online(slaveRef: ActorRef, totalWordCount: Int): Receive = {
      case Work(text) => slaveRef ! SlaveWork(text, sender())
      case WorkCompleted(count, originalRequester) =>
        val newTotalWorkCount = totalWordCount + count
        originalRequester ! Report(newTotalWorkCount)
        context.become(online(slaveRef, newTotalWorkCount))
    }
  }

  // the team
  // class Slave extends Actor ....

}
