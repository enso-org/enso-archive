package org.enso.interpreter.nodes;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeInfo;

@NodeInfo(language="Enso", description="xD")
public abstract class BinaryOperatorTwo extends Node {

    public abstract Object execute(VirtualFrame frame);

}
