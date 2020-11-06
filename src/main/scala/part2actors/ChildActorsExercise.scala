package part2actors

import akka.actor.{Actor, ActorRef, ActorSystem, Props}

object ChildActorsExercise extends App {

  // Distributed Word Counting

  /*
    Flow:
    create WordCounterMaster
    send Initialize(10) to wordCounterMaster
    send "Akka is awesome" to WCMaster
      wcMaster will send a workCountTask("...") to one of its children
        child replies with a WordCountReply(3) to the master
      wcMaster replies with 3 to the sender

    requester -> wcMaster -> wcWorker
            r <- wcMaster <-
   */
  // tasks to children are sent:
  // round robin logic
  // 1,2,3,4,5 children and 7 tasks
  // 1,2,3,4,5,1,2

  object WordCounterMaster {
    case class Initialize(nChildren: Int) // WCMaster creates nChildren of type WCWorker to delegate tasks
    case class WordCountTask(id: Int, text: String) // WCWorker that a String and will reply WCReply to the original sender with the word count result
    case class WordCounterReply(id: Int, count: Int)
  }
  class WordCounterMaster extends Actor {
    import WordCounterMaster._

    override def receive: Receive = {
      case Initialize(nChildren: Int) =>
        println("[master] Initializing...")
        val children = for {
          i <- 1 to nChildren
        } yield context.actorOf(Props[WordCounterWorker], s"WCWorker_$i")
        context.become(withChildren(children, 0, 0, Map()))

    }

    def withChildren(children: Seq[ActorRef], currentChildIndex: Int, currentTaskId: Int, requestMap: Map[Int, ActorRef]): Receive = {
      case text: String =>
        println(s"[master] I have receive $text - I will send it to child $currentChildIndex")
        val originalSender = sender()
        val task = WordCountTask(currentTaskId, text)
        val childRef = children(currentChildIndex)
        childRef ! task
        val nextChildIndex = (currentChildIndex + 1) % children.length // when children length is max, it goes back to 0
        val newTaskId = currentTaskId + 1
        val newRequestMap = requestMap + (currentTaskId -> originalSender)
        context.become(withChildren(children, nextChildIndex, newTaskId, newRequestMap))
      case WordCounterReply(id, count) =>
        println(s"[master] I have received a reply for task id $id with $count")
        // who is the original send of the text? in this case is the WCWorker, but it's not the original, so we need to keep track of it
        val originalSender = requestMap(id)
        originalSender ! count
        context.become(withChildren(children, currentChildIndex, currentTaskId, requestMap - id))
    }
  }

  class WordCounterWorker extends Actor {
    import WordCounterMaster._

    override def receive: Receive = {
      case WordCountTask(id, text) =>
        println(s"[${self.path}] I have received a task $id with $text")
        sender() ! WordCounterReply(id, text.split(" ").length)
    }
  }

  class TestActor extends Actor {
    import WordCounterMaster._

    override def receive: Receive = {
      case "go" =>
        val master = context.actorOf(Props[WordCounterMaster], "master")
        master ! Initialize(3)
        val texts = List("I love Akka", "Scala is super dope", "yes", "me too")
        texts.foreach( text => master ! text)
      case count: Int =>
        println(s"[test actor] I received a reply: $count")
    }
  }

  val system = ActorSystem("roundRobinWordCounter")

  val testActor = system.actorOf(Props[TestActor])
  testActor ! "go"



}
