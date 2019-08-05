package org.enso.interpreter.runtime.function.argument;

import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import org.enso.interpreter.runtime.Callable;
import org.enso.interpreter.runtime.TypesGen;
import org.enso.interpreter.runtime.error.NotInvokableException;

public class CallArgumentInfo {
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

  @ExplodeLoop
  public static Object[] reorderArguments(int[] order, Object[] args) {
    Object[] result = new Object[args.length];
    for (int i = 0; i < args.length; i++) {
      result[order[i]] = args[i];
    }
    return result;
  }

  public static int[] generateArgMapping(Object callable, CallArgumentInfo[] callArgs, Node parent) {
    if (TypesGen.isCallable(callable)) {
      Callable realCallable = (Callable) callable;
      // TODO [AA] Factor out
      int numberOfDefinedArgs = realCallable.getArgs().length;

      ArgumentDefinition[] definedArgs = realCallable.getArgs();

      boolean[] definedArgumentsUsage = new boolean[numberOfDefinedArgs];

      int[] result = new int[callArgs.length];
      // TODO: Split into functions.
      for (int i = 0; i < callArgs.length; ++i) {
        boolean flag = false;
        CallArgumentInfo currentArgument = callArgs[i];
        if (currentArgument.isPositional()) {
          for (int j = 0; j < numberOfDefinedArgs; j++) {
            if (!definedArgumentsUsage[j]) {
              result[i] = j;
              definedArgumentsUsage[j] = true;
              flag = true;
              break;
            }
          }
          if (!flag) {
            throw new RuntimeException("Arguments are wrong");
          }
        } else {
          for (int j = 0; j < numberOfDefinedArgs; j++) {
            if ((currentArgument.getName().equals(definedArgs[j].getName()))
                && !definedArgumentsUsage[j]) {
              result[i] = j;
              definedArgumentsUsage[j] = true;
              flag = true;
              break;
            }
          }
          if (!flag) {
            throw new RuntimeException("Named arguments are wrong");
          }
        }
      }
      return result;
    } else {
      throw new NotInvokableException(callable, parent);
    }
  }
}
