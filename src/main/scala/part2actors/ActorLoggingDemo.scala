package part2actors

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.event.Logging

object ActorLoggingDemo extends App {

  class SimpleActorWithExplicitLogger extends Actor {
    // #1 - explicit logging
    // there are different ways to log
    /*
      LOGGING CAN BE DONE IN 4 LEVELS
      1 - DEBUG - the must verbose, messages to find what happens in one application
      2 - INFO - more common, benign messages just informing
      3 - WARNING/WARN - messages being lost, for example
      4 - ERROR - more critical - source of trouble, app crashes
     */
    val logger = Logging(context.system, this)

    override def receive: Receive = {
      case message => logger.info(message.toString) //Log it, level 2 logging
    }
  }

  val system = ActorSystem("LoggingDemo")
  val actor = system.actorOf(Props[SimpleActorWithExplicitLogger])

  actor ! "Logging a simple message"

  // #2 - ActorLogging
  class ActorWithLogging extends Actor with ActorLogging {
    override def receive: Receive = {
      case (a, b) => log.info("Two things: {} and {}", a, b) // Two things: 2 and 3 if a = 2 and 3 = b
      case message => log.info(message.toString)
    }
  }

  val simplerActor = system.actorOf(Props[ActorWithLogging])
  simplerActor ! "Logging a simple message by extending a trait"
  simplerActor ! (42, 65)


  // Logging is asynchronous, to minimize performance impact
  // Akka logging is made with actors
  // The logger can be changed

}
