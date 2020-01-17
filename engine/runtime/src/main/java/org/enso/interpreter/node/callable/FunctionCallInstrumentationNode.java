package org.enso.interpreter.node.callable;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.*;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;
import org.enso.interpreter.Constants;
import org.enso.interpreter.Language;
import org.enso.interpreter.node.callable.dispatch.InvokeFunctionNode;
import org.enso.interpreter.runtime.Context;
import org.enso.interpreter.runtime.callable.CallerInfo;
import org.enso.interpreter.runtime.callable.argument.CallArgumentInfo;
import org.enso.interpreter.runtime.callable.function.Function;

@GenerateWrapper
public class FunctionCallInstrumentationNode extends Node implements InstrumentableNode {
  @Override
  public boolean isInstrumentable() {
    return true;
  }

  @ExportLibrary(InteropLibrary.class)
  public static class Data implements TruffleObject {
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

    @ExportMessage
    boolean isExecutable() {
      return true;
    }

    @ExportMessage
    static class Execute {
      @ExplodeLoop
      protected static InvokeFunctionNode buildSorter(int length) {
        CallArgumentInfo[] args = new CallArgumentInfo[length];
        for (int i = 0; i < length; i++) {
          args[i] = new CallArgumentInfo();
        }
        return InvokeFunctionNode.build(
            args,
            InvokeCallableNode.DefaultsExecutionMode.EXECUTE,
            InvokeCallableNode.ArgumentsExecutionMode.PRE_EXECUTED);
      }

      @Specialization(
          guards = "arguments.length == cachedArgsLength",
          limit = Constants.CacheSizes.FUNCTION_INTEROP_LIBRARY)
      protected static Object callCached(
          Data data,
          Object[] arguments,
          @Cached(value = "data.getArguments().length") int cachedArgsLength,
          @Cached(value = "buildSorter(cachedArgsLength)") InvokeFunctionNode sorterNode) {
        return sorterNode
            .execute(data.getFunction(), null, data.state, data.arguments)
            .getValue();
      }

      @Specialization(replaces = "callCached")
      protected static Object callUncached(Data data, Object[] arguments) {
        return callCached(
            data, arguments, data.arguments.length, buildSorter(data.arguments.length));
      }
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
