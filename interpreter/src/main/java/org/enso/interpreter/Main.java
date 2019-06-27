package org.enso.interpreter;

import static java.lang.System.err;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import org.enso.interpreter.node.ExpressionNode;
import org.enso.interpreter.node.expression.literal.IntegerLiteralNode;
import org.enso.interpreter.node.expression.operator.AddOperatorNodeGen;
import org.graalvm.polyglot.Context;

public class Main {
  public static void main(String[] args) {
    // This is all for testing purposes only.
    Context context;
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

    ExpressionNode test = AddOperatorNodeGen.create(new IntegerLiteralNode(1), new IntegerLiteralNode(2));

    Object result = test.execute(null);

    System.out.println(result);

    System.out.println("Executed!");
  }
}
