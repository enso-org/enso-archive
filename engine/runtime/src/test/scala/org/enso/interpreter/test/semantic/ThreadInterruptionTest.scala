package org.enso.interpreter.test.semantic

import org.enso.interpreter.test.InterpreterTest
import org.graalvm.polyglot.Value

import scala.util.{Failure, Try}

class ThreadInterruptionTest extends InterpreterTest {
  "Execution of Enso code" should "be interruptible through Thread#interrupt()" in {
    var res: Try[Value] = Failure(new RuntimeException)
    val code =
      """
        |foo x = 
        |  ifZero x 0 (here.foo x-1)
        |
        |main = here.foo 1000000000
        |""".stripMargin
    val t = new Thread {  res = Try(eval(code))  }
    t.interrupt()
    t.join()
    println(res)
  }
}
