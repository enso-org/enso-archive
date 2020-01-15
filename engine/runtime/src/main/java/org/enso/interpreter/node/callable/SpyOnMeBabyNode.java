package org.enso.interpreter.node.callable;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.*;
import com.oracle.truffle.api.nodes.Node;
import org.enso.interpreter.runtime.callable.CallerInfo;
import org.enso.interpreter.runtime.callable.function.Function;

@GenerateWrapper
public class SpyOnMeBabyNode extends Node implements InstrumentableNode {
  @Override
  public boolean isInstrumentable() {
    return true;
  }

  public static class Data {
    private final Function function;
    private final CallerInfo callerInfo;
    private final Object state;
    private final @CompilerDirectives.CompilationFinal(dimensions = 1) Object[] arguments;

    public Data(Function function, CallerInfo callerInfo, Object state, Object[] arguments) {
      this.function = function;
      this.callerInfo = callerInfo;
      this.state = state;
      this.arguments = arguments;
    }

    public Function getFunction() {
      return function;
    }

    public CallerInfo getCallerInfo() {
      return callerInfo;
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
      CallerInfo callerInfo,
      Object state,
      Object[] arguments) {
    return new Data(function, callerInfo, state, arguments);
  }

  @Override
  public WrapperNode createWrapper(ProbeNode probeNode) {
    return new SpyOnMeBabyNodeWrapper(this, probeNode);
  }

  @Override
  public boolean hasTag(Class<? extends Tag> tag) {
    return tag == StandardTags.CallTag.class;
  }
}
