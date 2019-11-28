package org.enso.syntax.text

import org.enso.flexer.Reader

import scala.scalajs.js.annotation._

object Parse {
    @JSExportTopLevel("parse")
    def parse(input: String): String =
      new Parser().run(new Reader(input)).encodeShape().toString()
}
