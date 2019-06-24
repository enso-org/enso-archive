package org.enso.interpreter.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;

public class ParenthesesNode extends GroupedNode{
    @Child ExpressionNode expressionNode;

    public ParenthesesNode(ExpressionNode parensedExpression) {
        this.expressionNode = parensedExpression;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        return expressionNode.execute(frame);
    }
}
