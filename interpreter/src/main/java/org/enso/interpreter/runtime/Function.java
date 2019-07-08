package org.enso.interpreter.runtime;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;

@ExportLibrary(InteropLibrary.class)
public final class Function implements TruffleObject {
  private final RootCallTarget callTarget;
  private final MaterializedFrame scope;

  public Function(RootCallTarget callTarget, MaterializedFrame scope) {
    this.callTarget = callTarget;
    this.scope = scope;
  }

  public RootCallTarget getCallTarget() {
    return callTarget;
  }

  public MaterializedFrame getScope() {
    return scope;
  }

  @ExportMessage
  public boolean isExecutable() {
    return true;
  }

  @ExportMessage
  abstract static class Execute {
//
    @Specialization(guards = "function.getCallTarget() == cachedTarget")
    protected static Object callDirect(
        Function function,
        Object[] arguments,
        @Cached("function.getCallTarget()") RootCallTarget cachedTarget,
        @Cached("create(cachedTarget)") DirectCallNode callNode) {
      Object[] args = {function.getScope(), arguments};
//      args[0] = function.getScope();
//      for (int i = 0; i < arguments.length; i++) {
//        args[i + 1] = arguments[i];
//      }
      return callNode.call(args);
    }

    @Specialization(replaces = "callDirect")
    protected static Object callIndirect(
            Function function, Object[] arguments, @Cached IndirectCallNode callNode) {
//      Object[] args = new Object[arguments.length + 1];
//      args[0] = function.getScope();
//      for (int i = 0; i < arguments.length; i++) {
//        args[i + 1] = arguments[i];
//      }
      System.out.println("Function " + function + " goes slow");
      Object[] args = {function.getScope(), arguments};
      return callNode.call(function.getCallTarget(), args);
    }
  }
}
