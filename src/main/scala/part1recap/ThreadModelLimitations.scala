package part1recap

import scala.concurrent.Future

object ThreadModelLimitations extends App {

  /*
    Daniel's Rants
   */

  /**
   * DR #1: OOP encapsulation is only valid in the SINGLE THREADED MODEL.
   */
  class BankAccount(private var amount: Int) {
    override def toString: String = "" + amount

    def withdraw(money: Int) = this.synchronized {
      this.amount -= money
    }

    def deposit(money: Int) = this.synchronized {
      this.amount += money
    }
    def getAmount = amount
  }

  val account = new BankAccount(2000)
  for(_ <- 1 to 1000) {
    new Thread(() => account.withdraw(1)).start()
  }

  for(_ <- 1 to 1000) {
    new Thread(() => account.deposit(1)).start()
  }

  println(account.getAmount)
  // result 1999 is because they are not synchronized but this is happening because
  // OOP encapsulation is broken in a multithread env

  // this involves synchronization! Locks to the recue - solve the problem BUT
  // introduces deadlocks, livelocks - that in big application cause a really big delay

  // So, we would need a data structure:
  // - fully encapsulated
  // - with no locks


  /**
   * DR #2: delegating something to a thread is a PAIN.
   */
  // not executor services
  // but a thread that is already running and we want to pass something

  // we have a running thread and we want to pass a runnable to the thread
  var task: Runnable = null

  // we have to create a scenario where we have a producer and a consumer so we can communicate with the thread
  // consumer
  val runningThread: Thread = new Thread(() => {
    while (true) {
      while (task == null) {
        runningThread.synchronized { // we need this because the wait method requires it
          println("[background] waiting for a task....")
          runningThread.wait()
        }
      }

      task.synchronized {
        println("[background] I have a task!")
        task.run()
        task = null
      }
    }
  })

  //producer
  def delegateToBackgroundThread(r: Runnable) = {
    if (task == null) task = r

    runningThread.synchronized {
      runningThread.notify()
    }
  }

  runningThread.start()
  Thread.sleep(1000)
  delegateToBackgroundThread(() => println(42))
  Thread.sleep(1000)
  delegateToBackgroundThread(() => println("this should run in the background"))

  /* in big apps, this is something really difficult
     And if we want to send other signals??
     - run x every y seconds
     - pass some String or a Number
     And if we have multiple background tasks and threads?
     - how we identify which threads receives which task?
     From the running thread, how we identify who gave the signal?
     What if the background thread crashes? e.g. Exception

  // So we need a data structure which
     - can safely receive any type of signal
     - can identify the sender
     - sender is easily identifiable
     - can guard against errors
  */

  /**
   * DR #3: tracing and dealing with errors in a multithreading env is a pain in the neck!!
   */
  // 1M numbers in between 10 threads
  import scala.concurrent.ExecutionContext.Implicits.global

  val futures = (0 to 9)
    .map(i => 10000 * i until 10000 * (i+1)) // 0 - 99999, 10000 - 19999, 20000 - 29999 etc
    .map(range => Future {
      if(range.contains(546735)) throw new RuntimeException("invalid number")
      range.sum
    })

  val sumFuture = Future.reduceLeft(futures)(_ + _) // Future with the sum of all the numbers
  sumFuture.onComplete(println)


}
