package org.enso.interpreter.nodes;

import com.oracle.truffle.api.nodes.NodeInfo;

@NodeInfo(
        shortName = "Grouped",
        description = "A node that represents grouping constructs (e.g. [], ())"
)
public abstract class GroupedNode extends ExpressionNode {}
