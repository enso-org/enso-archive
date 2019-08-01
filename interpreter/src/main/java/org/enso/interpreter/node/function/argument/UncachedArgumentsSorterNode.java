package org.enso.interpreter.node.function.argument;

import com.oracle.truffle.api.nodes.Node;
import org.enso.interpreter.node.function.DispatchNode;
import org.enso.interpreter.runtime.function.Function;

public class UncachedArgumentsSorterNode extends Node {

  // This is call-site info about which args are named and which are positional.
  // Should be passed from the constructor.
  public static class ArgType {}
  private ArgType[] schema;

  // Dispatch Node to handle stuff after we've unscrambled the args
  @Child private DispatchNode dispatchNode;

  public Object execute(Function function, Object[] arguments) {
    Object[] properlyOrderedArgs = unscramble(function, arguments);
    return dispatchNode.executeDispatch(function, properlyOrderedArgs);
  }


  // this is the function you asked me about on Discord, here's where it belongs.
  private Object[] unscramble(Function f, Object[] arguments) {
    return null;
  }

}
