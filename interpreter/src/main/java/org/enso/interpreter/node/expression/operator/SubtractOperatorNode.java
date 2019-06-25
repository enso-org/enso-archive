package org.enso.interpreter.node.expression.operator;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.NodeInfo;
import org.enso.interpreter.node.expression.BinaryOperatorNode;

@NodeInfo(shortName = "-")
public abstract class SubtractOperatorNode extends BinaryOperatorNode {

    @Specialization
    protected long subtract(long left, long right) {
        return left - right;
    }
}
