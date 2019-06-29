package org.enso.interpreter.node.expression.operator;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.NodeInfo;
import org.enso.interpreter.node.expression.BinaryOperatorNode;

@NodeInfo(shortName = "+")
public abstract class AddOperatorNode extends BinaryOperatorNode {

  @Specialization
  protected long add(int left, int right) { return (long) left + (long) right; }

  @Specialization
  protected long add(long left, int right) { return left + (long) right; }

  @Specialization
  protected long add(int left, long right) { return (long) left + right; }

  @Specialization
  protected long add(long left, long right) {
    return left + right;
  }
}
