package org.enso.interpreter;

import static java.lang.System.err;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
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

//    String code = "(3 + js >>[1,2,3].length<<) * js >>({a: 20})[\"a\"]<<";
    List<String> codeLs = new ArrayList<>();
    codeLs.add("@{ x = 10;");
    codeLs.add("   newBlock = {");
    codeLs.add("       y = x;");
    codeLs.add("       print: y + 1;");
    codeLs.add("       y + 2");
    codeLs.add("   };");
    codeLs.add("   jsCall: **(function (callback) { console.log(callback()); })** [{ @newBlock; 25 }];");
//    codeLs.add("   print: newBlock;");
//    codeLs.add("   print: newBlock;");
//    codeLs.add("   print: (@newBlock) + 5;");
//    codeLs.add("   print: (@newBlock) + 10;");
    codeLs.add("   0");
    codeLs.add("}");
    String code = StringUtils.join(codeLs, "\n");
    System.out.println(code);

    System.out.println("Starting execution...");

    Value value = context.eval(Constants.LANGUAGE_ID, code);

    System.out.println("Executed, result is: " + value);
  }
}
