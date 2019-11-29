package org.enso.interpreter.test;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.*;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;

@TruffleInstrument.Registration(
    id = CodeLocationsTestInstrument.INSTRUMENT_ID,
    services = CodeLocationsTestInstrument.class)
public class CodeLocationsTestInstrument extends TruffleInstrument {
  public static final String INSTRUMENT_ID = "locations-test";
  private Env env;

  @Override
  protected void onCreate(Env env) {
    env.registerService(this);
    this.env = env;
  }

  public static class LocationsEventListener implements ExecutionEventListener {
    private boolean successful = false;
    private final int start;
    private final int length;
    private final Class<?> type;

    public LocationsEventListener(int start, int length, Class<?> type) {
      this.start = start;
      this.length = length;
      this.type = type;
    }

    public int getStart() {
      return start;
    }

    public int getLength() {
      return length;
    }

    public Class<?> getType() {
      return type;
    }

    public boolean isSuccessful() {
      return successful;
    }

    @Override
    public void onEnter(EventContext context, VirtualFrame frame) {
      if (successful) {
        return;
      }
      Node node = context.getInstrumentedNode();
      if (!type.isInstance(node)) {
        return;
      }
      SourceSection section = node.getSourceSection();
      if (section == null || !section.hasCharIndex()) {
        return;
      }
      if (section.getCharIndex() == start && section.getCharLength() == length) {
        successful = true;
      }
    }

    @Override
    public void onReturnValue(EventContext context, VirtualFrame frame, Object result) {}

    @Override
    public void onReturnExceptional(
        EventContext context, VirtualFrame frame, Throwable exception) {}
  }

  public EventBinding<LocationsEventListener> bindTo(int sourceStart, int length, Class<?> type) {
    return env.getInstrumenter()
        .attachExecutionEventListener(
            SourceSectionFilter.newBuilder().build(),
            new LocationsEventListener(sourceStart, length, type));
  }
}
