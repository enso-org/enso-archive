package org.enso.interpreter.fixtures

import org.enso.interpreter.{Constants, LanguageRunner}

class NamedDefaultedArgumentFixtures extends LanguageRunner {
  val hundredMillion: Long = 100000000

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

  val sumTCOWithNamedArguments =
    ctx.eval(Constants.LANGUAGE_ID, sumTCOWithNamedArgumentsCode)

  val sumTCOWithDefaultedArgumentsCode =
    """
      |{ |sumTo|
      |  summator = { |acc = 0, current|
      |      ifZero: [current, acc, @summator [current = current - 1, acc = acc + current]]
      |  };
      |  res = @summator [current = sumTo];
      |  res
      |}
    """.stripMargin

  val sumTCOWithDefaultedArguments =
    ctx.eval(Constants.LANGUAGE_ID, sumTCOWithDefaultedArgumentsCode)

}
