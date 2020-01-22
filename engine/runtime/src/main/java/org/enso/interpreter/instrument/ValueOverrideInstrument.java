package org.enso.interpreter.instrument;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.*;

@TruffleInstrument.Registration(
    id = ValueOverrideInstrument.INSTRUMENT_ID,
    services = ValueOverrideInstrument.class)
public class ValueOverrideInstrument extends TruffleInstrument {
  public static final String INSTRUMENT_ID = "value-override";

  private Env env;

  /**
   * Initializes the instrument. Substitute for a constructor, called by the Truffle framework.
   *
   * @param env the instrumentation environment
   */
  @Override
  protected void onCreate(Env env) {
    env.registerService(this);
    this.env = env;
  }

  private static class OverrideUnwind {
    private final Object value;

    private OverrideUnwind(Object value) {
      this.value = value;
    }

    private Object getValue() {
      return value;
    }
  }

  public EventBinding<ExactPositionListener> overrideAt(
      String funName, int sourceStart, int sourceLength, Object value) {
    SourceSectionFilter sourceSectionFilter =
        SourceSectionFilter.newBuilder()
            .indexIn(sourceStart, sourceLength)
            .tagIs(StandardTags.ExpressionTag.class)
            .build();
    ExactPositionListener positionListener =
        new ExactPositionListener(funName, sourceStart, sourceLength) {
          @Override
          public void onEnter(EventContext context, VirtualFrame frame) {
            if (shouldTrigger(context)) {
              throw context.createUnwind(new OverrideUnwind(value), getBinding());
            }
          }

          @Override
          public Object onUnwind(EventContext context, VirtualFrame frame, Object info) {
            if (info instanceof OverrideUnwind) {
              detach();
              return ((OverrideUnwind) info).getValue();
            }
            return info;
          }
        };
    EventBinding<ExactPositionListener> binding =
        env.getInstrumenter().attachExecutionEventListener(sourceSectionFilter, positionListener);
    positionListener.setBinding(binding);
    return binding;
  }
}
