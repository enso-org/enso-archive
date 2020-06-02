package org.enso.interpreter.test.semantic

import org.enso.interpreter.test.InterpreterTest
import org.graalvm.polyglot.Value

import scala.util.{Failure, Try}

class ThreadInterruptionTest extends InterpreterTest {
  "Execution of Enso code" should "be interruptible through Thread#interrupt()" in {
    var res: Try[Value] = Failure(new RuntimeException)
    val code =
      """
        |foo x = here.foo x
        |
        |main =
        |    IO.println "pre"
        |    x = Thread.with_interrupt_handler (here.foo 10) (IO.println "Tell my sister I loved her.")
        |""".stripMargin

    println(Thread.currentThread())
    val t =
      new Thread({ () =>
        println(Thread.currentThread())
        res = Try(eval(code))
      })
//    ctx.
    t.start()
    println("Thread launched")
    Thread.sleep(2000)
    t.interrupt()
    println("Thread interrupted")
    t.join()
    println(res)
    println(consumeOut)
  }
}
