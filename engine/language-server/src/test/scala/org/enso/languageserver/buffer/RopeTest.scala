package org.enso.languageserver.buffer

import org.enso.languageserver.data.buffer.Rope
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class RopeTest extends AnyFlatSpec with Matchers {
  "Rope" should "allow taking and dropping lines" in {
    val r1 = Rope("\nHello World.\n")
    val r2 = Rope("The line starts here")
    val r3 = Rope(" but ends here.\nAnd then another starts.\nWow")
    val r4 = Rope(", that is indeed tricky.")

    val rope = (r1 ++ r2) ++ (r3 ++ r4)

    rope.lines.take(3).toString shouldEqual
    """
      |Hello World.
      |The line starts here but ends here.
      |""".stripMargin

    rope.lines.drop(2).toString shouldEqual
    """|The line starts here but ends here.
       |And then another starts.
       |Wow, that is indeed tricky.""".stripMargin
  }
}
