package org.enso.syntax

import org.enso.flexer.Macro
import org.enso.syntax.text.docsParser.DocParserDef
import org.enso.syntax.text.DocAST
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

  val newParser = Macro.compile(DocParserDef)

  performance of "DocParser" in {
    measure method "Formatters" in (using(formatters) in (newParser().run(_)))
    measure method "longSegment" in (using(longSegment) in (newParser().run(_)))
    measure method "100 letter segments" in (using(oneHundredSegment) in (newParser()
      .run(_)))
    measure method "Ginormous segment" in (using(ginormousSegment) in (newParser()
      .run(_)))
    measure method "tags" in (using(tags) in (newParser().run(_)))
    measure method "url test" in (using(urlTest) in (newParser().run(_)))
    measure method "image test" in (using(imageTest) in (newParser().run(_)))
    measure method "list test" in (using(listTest) in (newParser().run(_)))
    measure method "list nested test" in (using(listNestedTest) in (newParser()
      .run(_)))
    measure method "sections test" in (using(sections) in (newParser().run(_)))
  }
}
