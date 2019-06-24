package org.enso.interpreter.nodes.expression.operator;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.NodeInfo;
import org.enso.interpreter.nodes.expression.BinaryOperatorNode;

@NodeInfo(shortName = "*")
public abstract class MultiplyOperatorNode extends BinaryOperatorNode {

    @Specialization
    protected long multiply(long left, long right) {
        return left * right;
    }
}
