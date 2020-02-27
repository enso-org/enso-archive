package org.enso.languageserver.buffer
import org.enso.languageserver.data.buffer.StringUtils
import org.scalacheck.Properties
import org.scalacheck.Prop.forAll

class StringUtilsSpecification extends Properties("StringUtils") {
  property("getLines preserves string contents") =
    forAll(Generators.newLinedString) { str =>
      val (lines, lastLine) = StringUtils.getLines(str)
      (lines ++ lastLine).mkString("") == str
    }

  property(
    "getLines result has newlines at the end of each line except the last"
  ) = forAll(Generators.newLinedString) { str =>
    val (lines, lastLine) = StringUtils.getLines(str)
    lines.forall(StringUtils.endsInNewline) && lastLine.forall(
      !StringUtils.endsInNewline(_)
    )
  }

  property("getLines result has no newlines except for line ends") =
    forAll(Generators.newLinedString) { str =>
      val (lines, lastLine)    = StringUtils.getLines(str)
      val linesWithoutNewlines = lines.map(StringUtils.stripNewline)
      (linesWithoutNewlines ++ lastLine).forall(_.indexOf('\n') == -1)
    }
}
