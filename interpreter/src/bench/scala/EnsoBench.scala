import org.enso.interpreter.Constants
import org.graalvm.polyglot.Context
import org.scalameter.api._

class EnsoBench extends Bench.LocalTime {
  val ctx = Context.newBuilder(Constants.LANGUAGE_ID).build()

  def jsCallbackTester(size: Int) =
    s"""
       |(function (callback) {
       |    var res = 0;
       |    var i = 0;
       |    var adder = function(a, b) { return a + b; }
       |    for (i = 0; i < $size; i++) {
       |        res = adder(res, i);
       |    }
       |    console.log(res);
       |})
    """.stripMargin

  val ensoCode =
    s"""
       |{ myBlock = { |a1,a2| a1 + a2 };
       |  jsCall: **${jsCallbackTester(1000000)}** [myBlock];
       |  0
       |}
    """.stripMargin
//  val myCode =
//    """{ x = 10;
//      |    newBlock = { |arg1|
//      |      y = x;
//      |        print: y + arg1;
//      |        y + arg1
//      |    };
//      |    jsCall: **(function (callback, callback2) { console.log(callback(), callback2(3)); })** [{ @newBlock[12]; 25 }, newBlock];
//      |    (@newBlock[1]) + (@newBlock[2])
//      | }""".stripMargin
//  println(ensoCode)
  val gen = Gen.unit("foo")

  val internalSummatorCode =
    """
      |{ |sumTo|
      |    summator = { |current|
      |        ifZero: [current, 0, current + (@summator [current - 1])]
      |    };
      |    @summator [sumTo]
      |}
    """.stripMargin

  val value = ctx.eval(Constants.LANGUAGE_ID, internalSummatorCode)

  println(value.execute((100: Long).asInstanceOf[Object]))
//  performance of "FFI" in {
//    measure method "foo" in {
//      using(gen) in { _ =>
//        value.execute(10.asInstanceOf[Long].asInstanceOf[Object])
//      }
//    }
//  }

//  val sizes = Gen.range("size")(300000, 1500000, 300000)
//
//  val ranges = for {
//    size <- sizes
//  } yield 0 until size
//
//  performance of "Range" in {
//    measure method "map" in {
//      using(ranges) in { r =>
//        r.map(_ + 1)
//      }
//    }
//  }
}
