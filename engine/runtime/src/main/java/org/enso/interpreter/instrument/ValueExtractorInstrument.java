package org.enso.interpreter.instrument;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.*;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;

import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * An instrument used to extract node values from currently executed functions.
 *
 * <p>Allows to listen for a node at a given position, and trigger a callback when the node is
 * executed for the first time, passing the node's return value to the callback.
 */
@TruffleInstrument.Registration(
    id = ValueExtractorInstrument.INSTRUMENT_ID,
    services = ValueExtractorInstrument.class)
public class ValueExtractorInstrument extends ExactPositionInstrument<Object> {
  public static final String INSTRUMENT_ID = "value-extractor";

  @Override
  public ExactPositionListener createListener(
      String funName, int sourceStart, int length, Consumer<Object> callback) {
    return new ExactPositionListener(funName, sourceStart, length) {
      @Override
      public void handleReturnValue(Object result) {
        callback.accept(result);
      }
    };
  }

  @Override
  public SourceSectionFilter createSourceSectionFilter(
      String funName, int sourceStart, int length) {
    return SourceSectionFilter.newBuilder()
        .tagIs(StandardTags.ExpressionTag.class)
        .indexIn(sourceStart, length)
        .build();
  }
}
