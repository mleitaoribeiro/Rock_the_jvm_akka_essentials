package part5infra

import akka.actor.{Actor, ActorLogging, ActorSystem, Cancellable, Props, Timers}

import scala.concurrent.duration._

object TimersSchedulers extends App {

  class SimpleActor extends Actor with ActorLogging {
    override def receive: Receive = {
      case message => log.info(message.toString)
    }
  }

  val system = ActorSystem("SchedulersTimersDemo")
  val simpleActor = system.actorOf(Props[SimpleActor])

  system.log.info("Scheduling reminder for simpleActor") // to log messages

  import system.dispatcher

  // the schedule of messages has to happen in some type of thread like Futures so we need an execution context
  system.scheduler.scheduleOnce(1 second) {
    simpleActor ! "reminder"
  }  // implements the execution context interface
  // we can pass this:
  // 1. explicitly - (system.dispatcher) at the final }
  // 2. with an implicit
  // implicit val executionContext = system.dispatcher before the code
  // 3. with an import
  // import system.dispatcher

  val routine: Cancellable = system.scheduler.schedule(1 second, 2 seconds) { // first delay and interval between messages
    simpleActor ! "heartbeat"
  }

  // a Cancellable can be cancel
  system.scheduler.scheduleOnce(5 seconds) {
    routine.cancel()
  }

  /**
   * Exercise: implement a self-closing actor
   *
   * - if the actor receives a message (anything), you have 1 second to send it another message
   * - if the time window expires, the actor will stop itself
   * - if you send another message, the time window is reset
   */
  class SelfClosingActor extends Actor with ActorLogging {

    var schedule = createTimeoutWindow()

    def createTimeoutWindow(): Cancellable = {
      system.scheduler.scheduleOnce(1 second) {
        self ! "timeout"
      }
    }

    override def receive: Receive = {
      case "timeout" =>
        log.info(s"Stopping myself")
        context.stop(self)
      case message =>
        log.info(s"Received $message, staying alive")
        schedule.cancel()
        schedule = createTimeoutWindow()
    }
  }

  val selfClosingActor = system.actorOf(Props[SelfClosingActor], "selfClosingActor")
  system.scheduler.scheduleOnce(250 millis) {
    selfClosingActor ! "ping"
  }

  system.scheduler.scheduleOnce(2 seconds) {
    system.log.info("sending pong to the self-closing Actor")
    selfClosingActor ! "pong"
  }

  /**
   * Timer - messages to the actor itself within an actor, it's a safer way than the implementation above
   */

  case object TimerKey
  case object Start
  case object Reminder
  case object Stop
  class TimerBasedHeartbeatActor extends Actor with ActorLogging with Timers {
    timers.startSingleTimer(TimerKey, Start, 500 millis)

    override def receive: Receive = {
      case Start =>
        log.info("Bootstrapping")
        timers.startPeriodicTimer(TimerKey, Reminder, 1 second)
      case Reminder =>
        log.info("I am alive")
      case Stop =>
        log.warning("Stopping!")
        timers.cancel(TimerKey)
        context.stop(self)
    }
  }

  val timerBasedHeartbeatActor = system.actorOf(Props[TimerBasedHeartbeatActor], "timeActor")
  system.scheduler.scheduleOnce(5 seconds) {
    timerBasedHeartbeatActor ! Stop
  }
}
