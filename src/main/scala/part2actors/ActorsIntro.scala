package part2actors

import akka.actor.{Actor, ActorSystem, Props}

object ActorsIntro extends App {

  // part1 - always has to start with a akka system
  val actorSystem = ActorSystem("firstActorSystem") // data structure that controls threads and allocate them to actors
  println(actorSystem.name)

  // part2 - create actors
  // they are uniquely identified
  // messages are async
  // each actor may respond differently
  // we cannot force an actor to give information

  //word count actor
  class WordCountActor extends Actor {
    // internal data
    var totalWords = 0

    // behavior or receive handler that akka will invoke
    def receive: Receive = { // the type Receive is the same as PartialFunction[Any, Unit]
      case message: String =>
        println(s"[word counter] I have received: $message")
        totalWords += message.split(" ").length
      case msg => println(s"[word counter] I cannot understand ${msg.toString}")
    }
  }

  // part3 - instantiate our actor
  val wordCounter = actorSystem.actorOf(Props[WordCountActor], "wordCounter") // actor reference
  val anotherWordCounter = actorSystem.actorOf(Props[WordCountActor], "anotherWordCounter")
  // actor name has to be unique
  // they're fully encapsulated

  // part4 - communicate
  wordCounter ! "I am learning akka and it's pretty damn cool" // infix notation. instead of .! (! is a method)
  wordCounter ! 42 // not a string, so it's going to print the second case msg
  anotherWordCounter ! "A different message"
  // asynchronous - messages do not have an order to be send
  // communication with actors is always with the ! or "tell"

  // how to construct arguments when we instantiate an actor
  object Person {
    def props(name: String) = Props(new Person(name))
  }

  class Person(name: String) extends Actor {
    override def receive: Receive = {
      case "hi" => println(s"Hi, my name is $name")
      case _ =>
    }
  }

  // val person = actorSystem.actorOf(Props(new Person("Bob"))) --> discouraged, we should declare a companion object
  val person = actorSystem.actorOf(Person.props("Bob"))
  person ! "hi"


}
