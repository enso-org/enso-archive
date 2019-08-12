package org.enso.syntax

import org.enso.syntax.text.DocParser
import org.enso.syntax.text.ast.Doc
import org.enso.syntax.text.docsParser.DocParserDef
import org.scalameter.api._

import scala.math.pow

object DocParserBenchmark extends Bench.OfflineRegressionReport {
  val exp12 = Gen.exponential("size")(pow(2, 9).toInt, pow(2, 12).toInt, 2)
  val exp14 = Gen.exponential("size")(pow(2, 11).toInt, pow(2, 14).toInt, 2)
  val exp18 = Gen.exponential("size")(pow(2, 15).toInt, pow(2, 18).toInt, 2)

  val longSegment       = for { i <- exp18 } yield "test12" * i
  val formatters        = for { i <- exp14 } yield "*A* ~B~ _C_ *~_D_~*" * i
  val invalidFormatters = for { i <- exp14 } yield "*~_D* ~_Foo~" * i
  val tags              = for { i <- exp14 } yield "DEPRECATED\nMODIFIED\nADDED in 1.9" * i
  val urlTest           = for { i <- exp14 } yield "[link](http://foo.com)" * i
  val imageTest         = for { i <- exp14 } yield "![Image](http://foo.jpg)" * i
  val listTest          = for { i <- exp14 } yield """List
                                            |  - A
                                            |  - B
                                            |  - C""".stripMargin * i
  val listNestedTest    = for { i <- exp12 } yield """List
                                                  |  - A
                                                  |  - B
                                                  |    * CA
                                                  |    * CB
                                                  |  - D""".stripMargin * i
  val sections          = for { i <- exp12 } yield "Foo \n\nA \n\n ! B\n\n ? C \n\n > D " * i

  val part =
    "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Morbi sodales mi vitae orci imperdiet amet" // 100 letters
  val oneHundredSegment = for { i <- exp14 } yield (part + "\n") * i
  val ginormousSegment  = for { i <- exp18 } yield part * i

  performance of "DocParser" in {
    measure method "formatters" in (using(formatters) in (DocParser.run(_)))
    measure method "longS" in (using(longSegment) in (DocParser.run(_)))
    measure method "100ls" in (using(oneHundredSegment) in (DocParser.run(_)))
    measure method "GinS" in (using(ginormousSegment) in (DocParser.run(_)))
    measure method "tags" in (using(tags) in (DocParser.run(_)))
    measure method "url" in (using(urlTest) in (DocParser.run(_)))
    measure method "image" in (using(imageTest) in (DocParser.run(_)))
    measure method "list" in (using(listTest) in (DocParser.run(_)))
    measure method "listNT" in (using(listNestedTest) in (DocParser.run(_)))
    measure method "sections" in (using(sections) in (DocParser.run(_)))
  }
}
