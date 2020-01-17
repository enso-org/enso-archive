package org.enso.interpreter.instrument;

import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public abstract class ExactPositionInstrument<T> extends TruffleInstrument {
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

  public abstract ExactPositionListener createListener(
      String funName, int sourceStart, int length, Consumer<T> callback);

  public abstract SourceSectionFilter createSourceSectionFilter(
      String funName, int sourceStart, int length);

  public static class CodeIndexLocation {
    private final int start;
    private final int length;

    public CodeIndexLocation(int start, int length) {
      this.start = start;
      this.length = length;
    }

    public int getStart() {
      return start;
    }

    public int getLength() {
      return length;
    }
  }

  /**
   * Attach a new listener to observe nodes with given parameters.
   *
   * @param sourceStart the source start location of the expected node
   * @param length the source length of the expected node
   * @param callback the consumer of the node value
   * @return a reference to attached event listener
   */
  public EventBinding<ExactPositionListener> bindTo(
      String funName, int sourceStart, int length, Consumer<T> callback) {
    ExactPositionListener listener = createListener(funName, sourceStart, length, callback);
    SourceSectionFilter filter = createSourceSectionFilter(funName, sourceStart, length);

    EventBinding<ExactPositionListener> binding =
        env.getInstrumenter().attachExecutionEventListener(filter, listener);
    listener.setBinding(binding);
    return binding;
  }

  public List<EventBinding<ExactPositionListener>> bindTo(
      String funName, Consumer<T> callback, CodeIndexLocation... locations) {
    return Arrays.stream(locations)
        .map(loc -> bindTo(funName, loc.getStart(), loc.getLength(), callback))
        .collect(Collectors.toList());
  }
}
