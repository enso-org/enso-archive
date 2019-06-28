package org.enso.interpreter;

import static java.lang.System.err;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

import scala.util.parsing.combinator.JavaTokenParsers;

public class Main {
  public static void main(String[] args) {
    // This is all for testing purposes only.
    Context context = null;
    Map<String, String> options = new HashMap<>();
    InputStream in = System.in;
    OutputStream out = System.out;
    try {
      context = Context.newBuilder(Constants.LANGUAGE_ID).in(in).out(out).options(options).build();

      System.out.println(context.getEngine());

    } catch (IllegalArgumentException e) {
      err.println(e.getMessage());

      System.exit(1);
    }

    // TODO [AA] Try polyglot stuff
    // TODO ForeignCallNode Ruby, Python, JS

    String code = "(3 + js >>[1,2,3].length<<) * js >>({a: 20})[\"a\"]<<";
    Value value = context.eval(Constants.LANGUAGE_ID, code);

    System.out.println(code);
    System.out.println(value);
    System.out.println("Executed!");
  }
}
