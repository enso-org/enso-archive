package org.enso.interpreter.nodes.expression;

import com.oracle.truffle.api.dsl.NodeChild;
import org.enso.interpreter.nodes.ExpressionNode;

@NodeChild("leftOperand")
@NodeChild("rightOperand")
public abstract class BinaryOperatorNode extends ExpressionNode {
}
