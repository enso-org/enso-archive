package org.enso.interpreter.nodes;

import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.nodes.Node;

public abstract class ExpressionNode extends Node implements InstrumentableNode {
    // TODO [AA] Base of the node hierarchy
    // TODO [AA] A simple sum type for source positions (pos, span)
}
