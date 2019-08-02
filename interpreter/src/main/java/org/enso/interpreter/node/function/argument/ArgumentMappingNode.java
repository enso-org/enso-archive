package org.enso.interpreter.node.function.argument;

import com.oracle.truffle.api.nodes.NodeInfo;
import org.enso.interpreter.node.BaseNode;
import org.enso.interpreter.node.function.dispatch.DispatchNode;
import org.enso.interpreter.runtime.Callable;
import org.enso.interpreter.runtime.error.ArityException;
import org.enso.interpreter.runtime.error.NotInvokableException;
import org.enso.interpreter.runtime.function.argument.ArgumentDefinition;
import org.enso.interpreter.runtime.function.argument.CallArgument;

@NodeInfo(shortName = "ArgumentMap")
public class ArgumentMappingNode extends BaseNode {
  private final CallArgumentInfo[] schema;
  private Integer[] mapping;
  @Child private DispatchNode dispatchNode;

  public ArgumentMappingNode(CallArgumentInfo[] schema) {
    this.schema = schema;
    this.dispatchNode = null;
  }

  public Object execute(Object callable, Object[] arguments) {
    // FIXME [AA] See if we can remove this typecheck
    if (callable instanceof Callable) {
      Callable actualCallable = (Callable) callable;
      Object[] argsInDefOrder = generateArgMapping(actualCallable, arguments);
      return dispatchNode.executeDispatch(actualCallable, argsInDefOrder);
    } else {
      throw new NotInvokableException(callable, this);
    }
  }

  // this is the function you asked me about on Discord, here's where it belongs.
  private Object[] generateArgMapping(Callable callable, Object[] arguments) {
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

    //    for (ArgumentDefinition definedArg : definedArgs) {
    //      int definedArgPosition = definedArg.getPosition();
    //      String definedArgName = definedArg.getName();
    //
    //      if (hasArgByKey(callArgsByName, definedArgName)) {
    //        CallArgument callArg = callArguments[getArgByKey(callArgsByName, definedArgName)];
    //        computedArguments[definedArgPosition] = callArg.executeGeneric(frame);
    //
    //      } else if (hasArgByKey(callArgsByPosition, definedArgPosition)) {
    //        CallArgument callArg = getArgByKey(callArgsByPosition, definedArgPosition);
    //        computedArguments[definedArgPosition] = callArg.executeGeneric(frame);
    //
    //      } else if (definedArg.hasDefaultValue()) {
    //        computedArguments[definedArgPosition] = new DefaultedArgument(definedArg);
    //      } else {
    //        computedArguments[definedArgPosition] = new UnappliedArgument(definedArg);
    //        this.isSaturatedApplication = false;
    //      }
    //    }

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
