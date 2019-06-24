package org.enso.interpreter.nodes.expression.operator;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.NodeInfo;
import org.enso.interpreter.nodes.expression.BinaryOperatorNode;

@NodeInfo(shortName = "/")
public abstract class DivideOperatorNode extends BinaryOperatorNode {

    @Specialization
    protected long divide(long left, long right) {
        return left / right;
    }
}
