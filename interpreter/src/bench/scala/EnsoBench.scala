import org.enso.interpreter.Constants
import org.graalvm.polyglot.Context
import org.scalameter.api._

class EnsoBench extends Bench.LocalTime {
  val ctx = Context.newBuilder(Constants.LANGUAGE_ID, "js").build()

  val gen = Gen.unit("foo")

  val internalSummatorCode =
    """
      |{ |sumTo|
      |    summator = { |acc, current|
      |        ifZero: [current, acc, @summator [acc + current, current - 1]]
      |    };
      |    @summator [0, sumTo]
      |}
    """.stripMargin

  val value = ctx.eval(Constants.LANGUAGE_ID, internalSummatorCode)

  val jsValue = ctx.eval(
    "js",
    "(function (i) { var res = 0; for (var j = 0; j <= i; j++) res += j; return res; })"
  )

//  println(value.execute((100: Long).asInstanceOf[Object]))
  performance of "Enso TCO" in {
    measure method "sum numbers upto a million" in {
      using(gen) in { _ =>
        value.execute(1000000.asInstanceOf[Long].asInstanceOf[Object])
      }
    }
  }

  performance of "JS Loop" in {
    measure method "sum numbers upto a million" in {
      using(gen) in { _ =>
        jsValue.execute(1000000.asInstanceOf[Long].asInstanceOf[Object])
      }
    }
  }

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
