package org.enso.interpreter.nodes.expression;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.nodes.Node;

@NodeChild("leftOperand")
@NodeChild("rightOperand")
public abstract class BinaryOperatorTwo extends Node {
}
