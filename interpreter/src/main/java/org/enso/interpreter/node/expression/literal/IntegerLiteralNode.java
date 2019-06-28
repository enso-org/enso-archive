package org.enso.interpreter.node.expression.literal;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;
import org.enso.interpreter.node.expression.LiteralNode;

@NodeInfo(shortName = "IntegerLiteral")
public final class IntegerLiteralNode extends LiteralNode {
    // We don't need to zero init this as it's always constructed with a value.
    private long value;

    public IntegerLiteralNode(long value) {
        this.value = value;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        return this.value;
    }
}
