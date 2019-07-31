package org.enso.interpreter;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

public class Main {
  public static void main(String[] args) {
    //    System.out.println("Unimplemented");
    int numRuns = 1000;

    Context context = Context.newBuilder(Constants.LANGUAGE_ID).build();

    String code =
          "{ |sumTo|\n"
        + "  summator = { |acc, current|\n"
        + "    ifZero: [current, acc, @summator [acc + current, current - 1]]\n"
        + "  };\n"
        + "  res = @summator [0, sumTo];\n"
        + "  res\n"
        + "}";

    Value program = context.eval(Constants.LANGUAGE_ID, code);

    for (int i = 0; i < numRuns; ++i) {
      program.execute((long) 1000);
    }
  }
}
