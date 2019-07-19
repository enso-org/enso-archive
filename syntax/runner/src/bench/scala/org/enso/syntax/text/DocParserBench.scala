package org.enso.syntax.text

import org.enso.flexer.Macro._
import org.enso.flexer.Success
import org.enso.parser.docsParser._
import org.scalameter.api._

import scala.math.pow

object DocParserBench extends Bench.OfflineReport {
  val exp14 = Gen.exponential("size")(pow(2, 14).toInt, pow(2, 16).toInt, 2)
  val exp15 = Gen.exponential("size")(pow(2, 15).toInt, pow(2, 17).toInt, 2)
  val exp16 = Gen.exponential("size")(pow(2, 16).toInt, pow(2, 18).toInt, 2)

  val longSegment       = for { i <- exp16 } yield "test12" * i
  val formatters        = for { i <- exp16 } yield "*A* ~B~ _C_ *~_D_~*" * i
  val invalidFormatters = for { i <- exp16 } yield "*~_D* ~_Foo~" * i
  val tags              = for { i <- exp16 } yield "DEPRECATED\nMODIFIED\nADDED in 1.9" * i
  val urlTest           = for { i <- exp16 } yield "[link](http://foo.com)" * i
  val imageTest         = for { i <- exp16 } yield "![Image](http://foo.jpg)" * i
  val listTest          = for { i <- exp16 } yield """List
                                            |  - A
                                            |  - B
                                            |  - C""".stripMargin * i
  val listNestedTest    = for { i <- exp16 } yield """List
                                                  |  - A
                                                  |  - B
                                                  |    * CA
                                                  |    * CB
                                                  |  - D""".stripMargin * i
  val sections          = for { i <- exp16 } yield "Foo \n\nA \n\n ! B\n\n ? C \n\n > D " * i

  val part =
    "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Morbi sodales mi vitae orci imperdiet amet" // 100 letters
  val oneHundredSegment = for { i <- exp16 } yield (part + "\n") * i
  val ginormousSegment  = for { i <- exp16 } yield part * i

  performance of "Range" in {
    val parser = compile(DocParser)

//    Tests performed on MacBook Pro (15-inch, 2018)
//    2,9 GHz Intel Core i9
//    32 GB 2400 MHz DDR4
//    Radeon Pro 560X
//    macOS Mojave 10.14.5 (18F203)

    measure method "Fortmatters" in {
      using(formatters) in { input =>
        parser().run(input)
      }
    }
//    - at size -> 65536: passed
//    (mean = 110,91 ms, ci = <106,13 ms, 115,68 ms>, significance = 1.0E-10)
//    - at size -> 131072: passed
//    (mean = 110,48 ms, ci = <106,22 ms, 114,74 ms>, significance = 1.0E-10)
//    - at size -> 262144: passed
//    (mean = 110,59 ms, ci = <105,92 ms, 115,27 ms>, significance = 1.0E-10)

    measure method "longSegment" in {
      using(longSegment) in { input =>
        parser().run(input)
      }
    }
//    - at size -> 65536: passed
//    (mean = 8,41 ms, ci = <7,95 ms, 8,88 ms>, significance = 1.0E-10)
//    - at size -> 131072: passed
//    (mean = 8,13 ms, ci = <7,78 ms, 8,47 ms>, significance = 1.0E-10)
//    - at size -> 262144: passed
//    (mean = 7,64 ms, ci = <7,04 ms, 8,23 ms>, significance = 1.0E-10)

    measure method "100 letter segments" in {
      using(oneHundredSegment) in { input =>
        parser().run(input)
      }
    }
//    - at size -> 65536: passed
//    (mean = 10,62 ms, ci = <10,05 ms, 11,19 ms>, significance = 1.0E-10)
//    - at size -> 131072: passed
//    (mean = 10,50 ms, ci = <9,67 ms, 11,34 ms>, significance = 1.0E-10)
//    - at size -> 262144: passed
//    (mean = 10,60 ms, ci = <9,98 ms, 11,23 ms>, significance = 1.0E-10)

    measure method "Ginormous segment" in {
      using(ginormousSegment) in { input =>
        parser().run(input)
      }
    }
//    - at size -> 65536: passed
//    (mean = 8,80 ms, ci = <7,82 ms, 9,78 ms>, significance = 1.0E-10)
//    - at size -> 131072: passed
//    (mean = 8,80 ms, ci = <8,33 ms, 9,26 ms>, significance = 1.0E-10)
//    - at size -> 262144: passed
//    (mean = 8,68 ms, ci = <7,53 ms, 9,83 ms>, significance = 1.0E-10)

    measure method "tags" in {
      using(tags) in { input =>
        parser().run(input)
      }
    }
//    - at size -> 65536: passed
//    (mean = 35,30 ms, ci = <34,26 ms, 36,34 ms>, significance = 1.0E-10)
//    - at size -> 131072: passed
//    (mean = 35,46 ms, ci = <33,64 ms, 37,27 ms>, significance = 1.0E-10)
//    - at size -> 262144: passed
//    (mean = 37,61 ms, ci = <35,25 ms, 39,97 ms>, significance = 1.0E-10)

    measure method "url test" in {
      using(urlTest) in { input =>
        parser().run(input)
      }
    }
//    - at size -> 65536: passed
//    (mean = 8,82 ms, ci = <7,93 ms, 9,70 ms>, significance = 1.0E-10)
//    - at size -> 131072: passed
//    (mean = 8,31 ms, ci = <7,78 ms, 8,83 ms>, significance = 1.0E-10)
//    - at size -> 262144: passed
//    (mean = 8,34 ms, ci = <7,63 ms, 9,05 ms>, significance = 1.0E-10)

    measure method "image test" in {
      using(imageTest) in { input =>
        parser().run(input)
      }
    }
//    - at size -> 65536: passed
//    (mean = 8,13 ms, ci = <7,23 ms, 9,04 ms>, significance = 1.0E-10)
//    - at size -> 131072: passed
//    (mean = 7,98 ms, ci = <7,22 ms, 8,74 ms>, significance = 1.0E-10)
//    - at size -> 262144: passed
//    (mean = 7,59 ms, ci = <6,99 ms, 8,20 ms>, significance = 1.0E-10)

    measure method "list test" in {
      using(listTest) in { input =>
        parser().run(input)
      }
    }
//    - at size -> 65536: passed
//    (mean = 78,75 ms, ci = <73,53 ms, 83,97 ms>, significance = 1.0E-10)
//    - at size -> 131072: passed
//    (mean = 77,31 ms, ci = <73,20 ms, 81,42 ms>, significance = 1.0E-10)
//    - at size -> 262144: passed
//    (mean = 76,02 ms, ci = <72,91 ms, 79,14 ms>, significance = 1.0E-10)

    measure method "list nested test" in {
      using(listNestedTest) in { input =>
        parser().run(input)
      }
    }
//    - at size -> 65536: passed
//    (mean = 60,92 ms, ci = <56,89 ms, 64,95 ms>, significance = 1.0E-10)
//    - at size -> 131072: passed
//    (mean = 62,24 ms, ci = <51,49 ms, 72,99 ms>, significance = 1.0E-10)
//    - at size -> 262144: passed
//    (mean = 62,94 ms, ci = <47,19 ms, 78,69 ms>, significance = 1.0E-10)

    measure method "sections test" in {
      using(sections) in { input =>
        parser().run(input)
      }
    }
//    - at size -> 65536: passed
//    (mean = 427,91 ms, ci = <184,52 ms, 671,30 ms>, significance = 1.0E-10)
//    - at size -> 131072: passed
//    (mean = 433,80 ms, ci = <184,46 ms, 683,15 ms>, significance = 1.0E-10)
//    - at size -> 262144: passed
//    (mean = 423,76 ms, ci = <182,22 ms, 665,30 ms>, significance = 1.0E-10)

  }
}
