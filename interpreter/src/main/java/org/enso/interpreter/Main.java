package org.enso.interpreter;

import static java.lang.System.err;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleStackTrace;
import com.oracle.truffle.api.impl.TVMCI;
import org.apache.commons.lang3.StringUtils;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

import scala.util.parsing.combinator.JavaTokenParsers;

public class Main {
  public static void main(String[] args) {
    // This is all for testing purposes only.
    Context context = null;
    Context jsContext = null;
    Map<String, String> options = new HashMap<>();
    InputStream in = System.in;
    OutputStream out = System.out;
    try {
      context = Context.newBuilder(Constants.LANGUAGE_ID).in(in).out(out).options(options).build();
      jsContext = Context.newBuilder("js").in(in).out(out).options(options).build();


      System.out.println(context.getEngine());

    } catch (IllegalArgumentException e) {
      err.println(e.getMessage());

      System.exit(1);
    }

    // TODO [AA] Try polyglot stuff
    // TODO ForeignCallNode Ruby, Python, JS

//    String code = "(3 + js >>[1,2,3].length<<) * js >>({a: 20})[\"a\"]<<";
    List<String> codeLs = new ArrayList<>();
    codeLs.add("{ x = 10;");
//    codeLs.add("   print: x;");
    codeLs.add("   newBlock = { |arg1|");
    codeLs.add("       y = x;");
//    codeLs.add("       print: x;");
//    codeLs.add("       print: arg1;");
//    codeLs.add("       print: y;");
    codeLs.add("       y + arg1");
    codeLs.add("   };");
   // codeLs.add("   jsCall: **(function (callback, callback2) { console.log(callback(), callback2(3)); })** [{ @newBlock[12]; 25 }, newBlock];");
    codeLs.add("   (@newBlock[1]) + (@newBlock[2])");
//    codeLs.add("   print: newBlock;");
//    codeLs.add("   print: newBlock;");
//    codeLs.add("   print: (@newBlock) + 5;");
//    codeLs.add("   print: (@newBlock) + 10;");
//    codeLs.add("   0");
    codeLs.add("}");
    String code = StringUtils.join(codeLs, "\n");
//    System.out.println(code);

    System.out.println("Starting execution...");

//    System.out.println(TruffleStackTrace.fillIn(new Exception()));

//    System.out.println(Truffle.getRuntime().getCapability(TVMCI.class));
    Value value = context.eval(Constants.LANGUAGE_ID, "@{ x = 10; x = 5; x }"); //new EnsoParser().internalSummatorCode());//"{|x| x+2}");
//    for (int i = 0; i < 100000; i++) {
//      value.execute(2);
//    }


//    String jsCode = "(function (arg) { var recursiver = function (i) { return (i == 0 ? 0 : i + recursiver(i-1)); } ; return recursiver(arg); })";

//    Value value = jsContext.eval("js", jsCode);

    System.out.println("Executed, result is: " + value.execute(600));
  }
}
