package org.enso.interpreter.node.expression;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;
import org.enso.interpreter.node.ExpressionNode;

@NodeInfo(
        shortName = "Parentheses",
        description = "A representation of parenthesised expressions."
)
public class ParenthesesNode extends ExpressionNode{
    @Child ExpressionNode expression;

    @Override
    public Object execute(VirtualFrame frame) {
        return expression.execute(frame);
    }
}
