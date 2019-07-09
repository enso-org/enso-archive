package org.enso.interpreter;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import org.enso.interpreter.node.EnsoRootNode;
import org.enso.interpreter.node.ExpressionNode;
import org.enso.interpreter.runtime.Context;
import org.enso.interpreter.util.ExpressionFactory;

public class Main {

  public static void main(String[] args) {
    Context context = new Context();
    String code = "@{ adder = { |acc, i| ifZero: [i, acc, @adder [i+acc, i-1]] } ; print: @adder[0, 1000000]; 0 }";
    EnsoParser parser = new EnsoParser();
    ExpressionFactory fac = new ExpressionFactory(null);
    ExpressionNode result = fac.run(parser.parseEnso(code));
    FrameDescriptor d = new FrameDescriptor();
    EnsoRootNode root = new EnsoRootNode(null, d, result, null, "root");
    CallTarget tgt = Truffle.getRuntime().createCallTarget(root);
//    MaterializedFrame vFrame = Truffle.getRuntime().createMaterializedFrame(new Object[] {});
    IndirectCallNode node = Truffle.getRuntime().createIndirectCallNode();
    node.call(tgt, new Object[]{});

  }

// public static void main2(String[] args) {
//    // This is all for testing purposes only.
//    Context context = null;
//    Context jsContext = null;
//    Map<String, String> options = new HashMap<>();
//    InputStream in = System.in;
//    OutputStream out = System.out;
//    try {
//      context = Context.newBuilder(Constants.LANGUAGE_ID).in(in).out(out).options(options).build();
//      jsContext = Context.newBuilder("js").in(in).out(out).options(options).build();
//
//
//      System.out.println(context.getEngine());
//
//    } catch (IllegalArgumentException e) {
//      err.println(e.getMessage());
//
//      System.exit(1);
//    }
//
////    String code = "(3 + js >>[1,2,3].length<<) * js >>({a: 20})[\"a\"]<<";
//    List<String> codeLs = new ArrayList<>();
//    codeLs.add("{ x = 10;");
////    codeLs.add("   print: x;");
//    codeLs.add("   newBlock = { |arg1|");
//    codeLs.add("       y = x;");
////    codeLs.add("       print: x;");
////    codeLs.add("       print: arg1;");
////    codeLs.add("       print: y;");
//    codeLs.add("       y + arg1");
//    codeLs.add("   };");
//   // codeLs.add("   jsCall: **(function (callback, callback2) { console.log(callback(), callback2(3)); })** [{ @newBlock[12]; 25 }, newBlock];");
//    codeLs.add("   (@newBlock[1]) + (@newBlock[2])");
////    codeLs.add("   print: newBlock;");
////    codeLs.add("   print: newBlock;");
////    codeLs.add("   print: (@newBlock) + 5;");
////    codeLs.add("   print: (@newBlock) + 10;");
////    codeLs.add("   0");
//    codeLs.add("}");
//    String code = StringUtils.join(codeLs, "\n");
////    System.out.println(code);
//
//    System.out.println("Starting execution...");
//
////    System.out.println(TruffleStackTrace.fillIn(new Exception()));
//
////    System.out.println(Truffle.getRuntime().getCapability(TVMCI.class));
//    Value value = context.eval(Constants.LANGUAGE_ID, "@{ x = 10; x = 5; x }"); //new EnsoParser().internalSummatorCode());//"{|x| x+2}");
////    for (int i = 0; i < 100000; i++) {
////      value.execute(2);
////    }
//
//
////    String jsCode = "(function (arg) { var recursiver = function (i) { return (i == 0 ? 0 : i + recursiver(i-1)); } ; return recursiver(arg); })";
//
////    Value value = jsContext.eval("js", jsCode);
//
//    System.out.println("Executed, result is: " + value.execute(600));
//  }
}
