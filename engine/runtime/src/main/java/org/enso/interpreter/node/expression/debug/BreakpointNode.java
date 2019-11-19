package org.enso.interpreter.node.expression.debug;

import com.oracle.truffle.api.debug.DebuggerTags;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;
import org.enso.interpreter.Language;
import org.enso.interpreter.runtime.Context;
import org.enso.interpreter.runtime.callable.CallerInfo;
import org.enso.interpreter.runtime.state.Stateful;

@GenerateWrapper
public abstract class BreakpointNode extends Node implements InstrumentableNode {

  @Override
  public boolean isInstrumentable() {
    return true;
  }

  public abstract Stateful execute(VirtualFrame frame, Object state);

  @Specialization
  public Stateful execute(
      VirtualFrame frame, Object state, @CachedContext(Language.class) Context context) {
    return new Stateful(state, context.getUnit().newInstance());
  }

  public static BreakpointNode build() {
    return BreakpointNodeGen.create();
  }

  @Override
  public boolean hasTag(Class<? extends Tag> tag) {
    return tag == DebuggerTags.AlwaysHalt.class;
  }

  @Override
  public WrapperNode createWrapper(ProbeNode probeNode) {
    return new BreakpointNodeWrapper(this, probeNode);
  }
}
