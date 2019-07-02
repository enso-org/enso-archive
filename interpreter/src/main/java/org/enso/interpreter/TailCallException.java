package org.enso.interpreter;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.nodes.ControlFlowException;
import org.enso.interpreter.runtime.Block;

public class TailCallException extends ControlFlowException {
  private final Block block;
  private final Object[] arguments;

  public Block getBlock() {
    return block;
  }

  public Object[] getArguments() {
    return arguments;
  }

  public TailCallException(Block block, Object[] arguments) {
    this.block = block;
    this.arguments = arguments;
  }
}
