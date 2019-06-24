package org.enso.interpreter.nodes.expression.operator;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.NodeInfo;
import org.enso.interpreter.nodes.expression.BinaryOperatorNode;

@NodeInfo(shortName = "+")
public abstract class AddOperatorNode extends BinaryOperatorNode {

    @Specialization
    protected long add(long left, long right) {
        return Math.addExact(left, right);
    }
}
