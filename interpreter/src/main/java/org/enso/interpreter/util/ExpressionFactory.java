package org.enso.interpreter.util;

import org.enso.interpreter.AbstractExpressionFactory;
import org.enso.interpreter.node.ExpressionNode;
import org.enso.interpreter.node.expression.ForeignCallNode;
import org.enso.interpreter.node.expression.literal.IntegerLiteralNode;
import org.enso.interpreter.node.expression.operator.*;

import java.beans.Expression;

public class ExpressionFactory implements AbstractExpressionFactory<ExpressionNode> {
    public ExpressionNode makeArith(String operator, ExpressionNode left, ExpressionNode right) {
        if (operator.equals( "+")) return AddOperatorNodeGen.create(left, right);
        if (operator.equals( "-")) return SubtractOperatorNodeGen.create(left, right);
        if (operator.equals( "*")) return MultiplyOperatorNodeGen.create(left, right);
        if (operator.equals( "/")) return DivideOperatorNodeGen.create(left, right);

        return null;
    }

    public ExpressionNode makeLong(long x) {
        return new IntegerLiteralNode(x);
    }

    public ExpressionNode makeForeign(String lang, String code) {
        return new ForeignCallNode(lang, code);
    }
}
