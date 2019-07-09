import org.enso.interpreter.Constants
import org.enso.interpreter.LanguageRunner
import org.scalameter.api._

class EnsoBench extends Bench.LocalTime with LanguageRunner {
  val gen = Gen.unit("")

  val sumTCOCode =
    """
      |summator = { |acc, current|
      |    ifZero: [current, acc, @summator [acc + current, current - 1]]
      |}
      |
      |{ |sumTo|
      |  res = @summator [0, sumTo];
      |  res
      |}
    """.stripMargin

  val sumTCO = ctx.eval(Constants.LANGUAGE_ID, sumTCOCode)

  val sumRecursiveCode =
    """
      |{ |sumTo|
      |  summator = { |i| ifZero: [i, 0, i + (@summator [i - 1])] };
      |  res = @summator [sumTo];
      |  res
      |}
    """.stripMargin

  val sumRecursive = ctx.eval(Constants.LANGUAGE_ID, sumRecursiveCode)

  val jsSumLoopCode =
    """
      |(function (sumTo) {
      |  var res = 0;
      |  for (var i = 0; i <= sumTo; i++) {
      |    res += i;
      |  }
      |  return res;
      |})
    """.stripMargin

  val jsSumLoop = ctx.eval("js", jsSumLoopCode)

  val jsSumRecurCode =
    """
      |(function (sumTo) {
      |  var summator = function (i) {
      |    if (i == 0); return 0;
      |    return (i + summator(i-1));
      |  };
      |  return summator(sumTo);
      |})
    """.stripMargin

//  1.to(1000).foreach(_ => sumTCO.call(100))
//  1.to(1000).foreach(_ => sumRecursive.call(100))

  val jsSumRecur = ctx.eval("js", jsSumRecurCode)

  performance of "Enso TCO" in {
    measure method "sum numbers upto a million" in {
      using(gen) in { _ =>
        sumTCO.call(100000000)
      }
    }
  }

  performance of "JS Loop" in {
    measure method "sum numbers upto a million" in {
      using(gen) in { _ =>
        jsSumLoop.call(100000000)
      }
    }
  }

  performance of "Enso Recursive" in {
    measure method "sum numbers upto 100" in {
      using(gen) in { _ =>
        sumRecursive.call(100)
      }
    }
  }

  performance of "JS Recursive" in {
    measure method "sum numbers upto 100" in {
      using(gen) in { _ =>
        jsSumRecur.call(100)
      }
    }
  }
}
