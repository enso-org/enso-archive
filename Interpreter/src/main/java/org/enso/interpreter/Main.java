package org.enso.interpreter;

import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import java.io.File;
import java.io.IOException;

public class Main {
  public static void main(String[] args) throws IOException {
    Context context = Context.newBuilder(Constants.LANGUAGE_ID).build();
    Source source = Source.newBuilder(Constants.LANGUAGE_ID, new File("test.enso")).build();
    Value result = context.eval(source);
    long input = Long.parseLong(args[0]);
    for (int i = 0; i < 1000; i++) {
      long startTime = System.nanoTime();
      Value res = result.execute(input);
      long endTime = System.nanoTime();
      System.out.println("Time: " + (endTime - startTime)/1000000 + "ms. Result: " + res + ".");
    }
  }
}
