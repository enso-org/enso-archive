package org.enso.interpreter.instrument;

import com.oracle.truffle.api.instrumentation.*;
import org.enso.interpreter.node.callable.FunctionCallInstrumentationNode;

import java.util.function.Consumer;

/**
 * An instrument used to extract node values from currently executed functions.
 *
 * <p>Allows to listen for a node at a given position, and trigger a callback when the node is
 * executed for the first time, passing the node's return value to the callback.
 */
@TruffleInstrument.Registration(
    id = FunctionCallExtractorInstrument.INSTRUMENT_ID,
    services = FunctionCallExtractorInstrument.class)
public class FunctionCallExtractorInstrument extends ExactPositionInstrument<FunctionCallInstrumentationNode.FunctionCall> {
  public static final String INSTRUMENT_ID = "function-call-extractor";

  @Override
  public ExactPositionListener createListener(
      String funName, int sourceStart, int length, Consumer<FunctionCallInstrumentationNode.FunctionCall> callback) {
    return new ExactPositionListener(funName, sourceStart, length) {
      @Override
      public void handleReturnValue(Object result) {
        if (result instanceof FunctionCallInstrumentationNode.FunctionCall) {
          callback.accept((FunctionCallInstrumentationNode.FunctionCall) result);
        }
      }
    };
  }

  @Override
  public SourceSectionFilter createSourceSectionFilter(
      String funName, int sourceStart, int length) {
    return SourceSectionFilter.newBuilder()
        .tagIs(StandardTags.CallTag.class)
        .indexIn(sourceStart, length)
        .build();
  }
}
