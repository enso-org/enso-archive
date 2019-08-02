package org.enso.interpreter.node.function.argument;

import com.oracle.truffle.api.nodes.NodeInfo;
import org.enso.interpreter.node.BaseNode;
import org.enso.interpreter.node.function.dispatch.DispatchNode;
import org.enso.interpreter.node.function.dispatch.SimpleDispatchNode;
import org.enso.interpreter.runtime.Callable;
import org.enso.interpreter.runtime.error.ArityException;
import org.enso.interpreter.runtime.error.NotInvokableException;
import org.enso.interpreter.runtime.function.argument.ArgumentDefinition;
import org.enso.interpreter.runtime.function.argument.CallArgument;

@NodeInfo(shortName = "ArgumentMap")
public class ArgumentMappingNode extends BaseNode {
  private final CallArgumentInfo[] schema;
  @Child private DispatchNode dispatchNode;

  public ArgumentMappingNode(CallArgumentInfo[] schema) {
    this.schema = schema;
    this.dispatchNode = new SimpleDispatchNode();
  }

  public Object execute(Object callable, Object[] arguments) {
    // FIXME [AA] See if we can remove this typecheck
    if (callable instanceof Callable) {
      Callable actualCallable = (Callable) callable;
//      int[] order = generateArgMapping(actualCallable);
//      Object[] argsInDefOrder = reorderArguments(order, arguments);
      return dispatchNode.executeDispatch(actualCallable, arguments);// argsInDefOrder);
    } else {
      throw new NotInvokableException(callable, this);
    }
  }

  private Object[] reorderArguments(int[] order, Object[] args) {
    //TODO: This should be a number of defined args with holes and stuff.
    Object[] result = new Object[args.length];
    for (int i = 0; i < args.length; i++) {
      result[order[i]] = args[i];
    }
    return result;
  }

  /**
   * Maps call-site arguments positions to definition-site positions.
   * @param callable
   * @return
   */
  private int[] generateArgMapping(Callable callable) {
    int numberOfDefinedArgs = callable.getArgs().length;
    ArgumentDefinition[] definedArgs = callable.getArgs();
//    if (this.schema.length != numberOfDefinedArgs) {
//      throw new ArityException(numberOfDefinedArgs, this.schema.length);
//    }
    boolean[] definedArgumentsUsage = new boolean[numberOfDefinedArgs];
    int[] result = new int[this.schema.length];
    // TODO: Split into functions.
    for (int i = 0; i < this.schema.length; i++) {
      CallArgumentInfo currentArgument = this.schema[i];
      if (currentArgument.isPositional()) {
        for (int j = 0; j < numberOfDefinedArgs; j++) {
          if (!definedArgumentsUsage[j]) {
            result[i] = j;
            definedArgumentsUsage[j] = true;
            break;
          }
        }
        throw new RuntimeException("Arguments are wrong");
      } else {
        for (int j = 0; j < numberOfDefinedArgs; j++) {
          if ((currentArgument.getName().equals(definedArgs[j].getName()))
              && !definedArgumentsUsage[j]) {
            result[i] = j;
            definedArgumentsUsage[j] = true;
            break;
          }
        }
        throw new RuntimeException("Named arguments are wrong");
      }
    }
    return result;
  }
  // this is the function you asked me about on Discord, here's where it belongs.
  private int[] generateArgMapping2(Callable callable, Object[] arguments) {
    ArgumentDefinition[] argDefinitions = callable.getArgs();
    int numDefinedArgs = argDefinitions.length;

    // TODO [AA] Remove once it can handle differing numbers of args
    if (this.schema.length != numDefinedArgs) {
      throw new ArityException(numDefinedArgs, this.schema.length);
    }

    /* TODO [AA] Mapping between call site args and function args
     * Can do a child node that handles the argument matching to the Function purely on runtime
     * values.
     * - General case variant (matching below).
     * - An optimised variant that works on a precomputed mapping.
     *
     * TODO [AA] Handle the case where we have too many arguments
     * TODO [AA] Handle the case where we have too few arguments
     * TODO [AA] Return a function with some arguments applied.
     * TODO [AA] Loop nodes, returning new call targets for under-saturated.
     * TODO [AA] Too many arguments need to execute. Overflow args in an array.
     * TODO [AA] Looping until done.
     */
    return null;
  }

  public static class CallArgumentInfo {
    private final String name;
    private final boolean isNamed;
    private final boolean isPositional;
    private final boolean isIgnored;

    public CallArgumentInfo(CallArgument callArgNode) {
      this(
          callArgNode.getName(),
          callArgNode.isNamed(),
          callArgNode.isPositional(),
          callArgNode.isIgnored());
    }

    public CallArgumentInfo(String name, boolean isNamed, boolean isPositional, boolean isIgnored) {
      this.name = name;
      this.isNamed = isNamed;
      this.isPositional = isPositional;
      this.isIgnored = isIgnored;
    }

    public String getName() {
      return name;
    }

    public boolean isNamed() {
      return isNamed;
    }

    public boolean isPositional() {
      return isPositional;
    }

    public boolean isIgnored() {
      return isIgnored;
    }
  }
}
