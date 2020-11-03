package part1recap

import scala.concurrent.Future
import scala.util.{Failure, Success}

object MultithreadingRecap extends App {

  // creating threads on the JVM
  val aThread = new Thread(new Runnable {
    override def run(): Unit = println("I'm running in parallel")
  })

  // syntax sugar - the same as above
  val aThread2 = new Thread(() => println("I'm running in parallel"))
  // we cannot see the thread running
  aThread2.start()
  aThread2.join()

  // with the multi core computer we have nowadays, we can run multi thread environments and have tasks performing in parallel
  // problem is threads are completely unpredictable
  val threadHello = new Thread(() => (1 to 1000).foreach(_ => println("hello")))
  val threadGoodbye = new Thread(() => (1 to 1000).foreach(_ => println("goodbye")))

  threadHello.start()
  threadGoodbye.start()
  // we are going to see goodbyes and hellos all mixed up and every time we run the App, is going to be a different order
  // Unpredictable
  // Threads and threads scheduling - different runs produce different results!!!

  class BankAccount(@volatile private var amount: Int) {
    override def toString: String = "" + amount

    def withdraw(money: Int) = this.amount -= money // not thread safe

    // to solve this, we add synchronized blocks
    def safeWithdraw(money: Int) = this.synchronized { // in this case is simple, but in very large apps this is a pain
      this.amount -= money                             // in the neck because of the blocking
    }
    // safe because we a synchronized expression we cannot have to threads evaluating at the same time
    // if one is evaluating this expression, the other is blocked!!!!
    // another solution is to add @volatile to the parameter, it has the same effect!!! blocks the second thread, but it
    // only works with primitive types such as Int
  }

  /*
    Example:
    Bank Account (10 000)

    Thread1 -> withdraw 1000
    Thread2 -> withdraw 2000

    Order:
    T1 -> this.amount = this.amount - .... // PREEMPTED by the OS
    T2 -> this.amoint = this.amount - 2000 = 8000
    and then it goes back to
    T1 -> -1000 = 9000 because when the thread began the initial value was 10 000

    => result = 9000
    this happens because the instruction -= it's NOT ATOMIC!! - ATOMIC means thread safe, that it's not possible to
    execute two threads at the same time
   */

  // inter-thread communication om the JVM
  // wait - notify mechanism

  // SCALA FUTURES
  import scala.concurrent.ExecutionContext.Implicits.global
  val future = Future {
    // long computation - on a different thread
    42
  }

  // callbacks
  future.onComplete{ // with Futures, we can run something on complete
    case Success(42) => println("I found the meaning of life")
    case Failure(_) => println("something happened with the meaning of life")
  }

  val aProcessedFuture = future.map(_ + 1) // future with 43
  val aFlatFuture = future.flatMap{
    value => Future(value+2) }

  val filteredFuture = future.filter(_ % 2 == 0) // if this is true, the filtered future is going to be identical to the
                                                 // first feature
                                                 // otherwise, the result future will fail with NoSuchElementException

  // for comprehensions
  val aNonsenseFuture = for {
    meaningOfLife <- future
    filteredMeaning <- filteredFuture
  } yield meaningOfLife + filteredMeaning

  // other useful features are andThen, recover/recoverWith

  // Promises - see the advanced scala course
  // complete Futures manually with Promises

}
