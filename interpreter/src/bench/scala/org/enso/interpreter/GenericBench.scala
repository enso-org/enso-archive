package org.enso.interpreter

import org.scalameter.api._

class GenericBench extends Bench.LocalTime with LanguageRunner {
  val gen = Gen.unit("")

  val million: Long        = 1000000
  val hundredMillion: Long = 100000000

  // TODO [AA] Benchmarks for named and defaulted argument usage

  // Currently unused as we know this is very slow.
//  val mutRecursiveCode =
//    """
//    |summator = { |acc, current|
//    |    ifZero: [current, acc, @summator [acc + current, current - 1]]
//    |}
//    |
//    |{ |sumTo|
//    |  res = @summator [0, sumTo];
//    |  res
//    |}
//    |"""

  val sumTCOCode =
    """
      |{ |sumTo|
      |  summator = { |acc, current|
      |      ifZero: [current, acc, @summator [acc + current, current - 1]]
      |  };
      |  res = @summator [0, sumTo];
      |  res
      |}
    """.stripMargin

  val sumTCO = eval(sumTCOCode)

  performance of "Enso TCO" in {
    measure method "Summing numbers up to a million" in {
      using(gen) in { _ =>
        sumTCO.call(million)
      }
    }
  }

  val sumTCOWithNamedArgumentsCode =
    """
      |{ |sumTo|
      |  summator = { |acc, current|
      |      ifZero: [current, acc, @summator [current = current - 1, acc = acc + current]]
      |  };
      |  res = @summator [current = sumTo, acc = 0];
      |  res
      |}
    """.stripMargin

  val sumTCOWithNamedArguments = eval(sumTCOWithNamedArgumentsCode)

  performance of "Enso TCO with named arguments" in {
    measure method "Summing numbers up to 100 millions" in {
      using(gen) in { _ =>
        sumTCOWithNamedArguments.call(million)
      }
    }
  }

  // TODO Test defaulted arguments

  //  val sumRecursiveCode =
//    """
//      |{ |sumTo|
//      |  summator = { |i| ifZero: [i, 0, i + (@summator [i - 1])] };
//      |  res = @summator [sumTo];
//      |  res
//      |}
//    """.stripMargin
//
//  val sumRecursive = ctx.eval(Constants.LANGUAGE_ID, sumRecursiveCode)
//
//  performance of "Enso Recursive" in {
//    measure method "Summing numbers up to 100" in {
//      using(gen) in { _ =>
//        sumRecursive.call(100)
//      }
//    }
//  }
//
//  val generateListCode =
//    """
//      |{ |length|
//      |  generator = { |acc, i| ifZero: [i, acc, @generator [@Cons [i, acc], i - 1]] };
//      |  res = @generator [@Nil, length];
//      |  res
//      |}
//    """.stripMargin
//
//  val generateList = eval(generateListCode)
//
//  performance of "List generation" in {
//    measure method "Generating a million-element list of integers" in {
//      using(gen) in { _ =>
//        generateList.call(million)
//      }
//    }
//  }
//
//  val millionElementList = generateList.call(million)
//
//  val reverseListCode =
//    """
//      |{ |list|
//      |  reverser = { |acc, list| match list <
//      |    Cons ~ { |h, t| @reverser [@Cons [h, acc], t] };
//      |    Nil  ~ { acc };
//      |  >};
//      |  res = @reverser [@Nil, list];
//      |  res
//      |}
//    """.stripMargin
//
//  val reverseList = eval(reverseListCode)
//
//  performance of "List reversing" in {
//    measure method "Reversing a million element list" in {
//      using(gen) in { _ =>
//        reverseList.execute(millionElementList)
//      }
//    }
//  }
//
//  val sumListCode =
//    """
//      |{ |list|
//      |  summator = { |acc, list| match list <
//      |    Cons ~ { |h, t| @summator [acc + h, t] };
//      |    Nil ~ { acc };
//      |  >};
//      |  res = @summator [0, list];
//      |  res
//      |}
//    """.stripMargin
//
//  val sumList = eval(sumListCode)
//
//  performance of "List sum" in {
//    measure method "Summing a million element list of integers" in {
//      using(gen) in { _ =>
//        sumList.execute(millionElementList)
//      }
//    }
//  }
//
//  val sumListFallbackCode =
//    """
//      |{ |list|
//      |  sumator = { |acc, list| match list <
//      |    Cons ~ { |h, t| @sumator [acc + h, t] };
//      |    { acc };
//      |  >};
//      |  res = @sumator [0, list];
//      |  res
//      |}
//    """.stripMargin
//
//  val sumListFallback = eval(sumListFallbackCode)
//
//  performance of "List sum" in {
//    measure method "Summing a million element list of integers using fallback pattern match" in {
//      using(gen) in { _ =>
//        sumListFallback.execute(millionElementList)
//      }
//    }
//  }
}
