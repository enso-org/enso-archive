package org.enso.interpreter.node.callable;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.*;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;
import org.enso.interpreter.runtime.callable.function.Function;

@GenerateWrapper
public class FunctionCallInstrumentationNode extends Node implements InstrumentableNode {
  @Override
  public boolean isInstrumentable() {
    return true;
  }

  @ExportLibrary(InteropLibrary.class)
  public static class FunctionCall implements TruffleObject {
    private final Function function;
    private final Object state;
    private final @CompilerDirectives.CompilationFinal(dimensions = 1) Object[] arguments;

    public FunctionCall(Function function, Object state, Object[] arguments) {
      this.function = function;
      this.state = state;
      this.arguments = arguments;
    }

    @ExportMessage
    boolean isExecutable() {
      return true;
    }

    @ExportMessage
    static class Execute {
      @Specialization
      static Object callCached(
          FunctionCall functionCall, Object[] arguments, @Cached InteropApplicationNode interopApplicationNode) {
        return interopApplicationNode.execute(functionCall.function, functionCall.state, functionCall.arguments);
      }
    }

    public Function getFunction() {
      return function;
    }

    public Object getState() {
      return state;
    }

    public Object[] getArguments() {
      return arguments;
    }
  }

  public Object execute(
      VirtualFrame frame,
      Function function,
      Object state,
      Object[] arguments) {
    return new FunctionCall(function, state, arguments);
  }

  @Override
  public WrapperNode createWrapper(ProbeNode probeNode) {
    return new FunctionCallInstrumentationNodeWrapper(this, probeNode);
  }

  @Override
  public boolean hasTag(Class<? extends Tag> tag) {
    return tag == StandardTags.CallTag.class;
  }

  @Override
  public SourceSection getSourceSection() {
    Node parent = getParent();
    return parent == null ? null : parent.getSourceSection();
  }
}
