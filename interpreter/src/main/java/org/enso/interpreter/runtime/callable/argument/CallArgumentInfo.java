package org.enso.interpreter.runtime.callable.argument;

import com.oracle.truffle.api.nodes.ExplodeLoop;
import org.enso.interpreter.runtime.callable.Callable;
import org.enso.interpreter.runtime.error.NotInvokableException;
import org.enso.interpreter.runtime.type.TypesGen;

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

  // TODO [AA] Why do we use the defined args here (reference `ReadArgumentNode`)
  @ExplodeLoop
  public static Object[] reorderArguments(int[] order, Object[] args, int numDefinedArgs) {
    Object[] result = new Object[numDefinedArgs];

    for (int i = 0; i < args.length; i++) {
      result[order[i]] = args[i];
    }
    return result;
  }

  public static int[] generateArgMapping(Object callable, CallArgumentInfo[] callArgs) {
    if (TypesGen.isCallable(callable)) {
      Callable realCallable = (Callable) callable;

      ArgumentDefinition[] definedArgs = realCallable.getArgs();
      int numberOfDefinedArgs = definedArgs.length;

      boolean[] definedArgumentIsUsed = new boolean[numberOfDefinedArgs];
      int[] argumentSortOrder = new int[callArgs.length];

      for (int i = 0; i < callArgs.length; ++i) {
        boolean argumentProcessed = false;
        CallArgumentInfo currentArgument = callArgs[i];

        boolean argumentIsPositional = currentArgument.isPositional();

        if (argumentIsPositional) {
          for (int j = 0; j < numberOfDefinedArgs; j++) {
            boolean argumentIsUnused = !definedArgumentIsUsed[j];

            if (argumentIsUnused) {
              argumentSortOrder[i] = j;
              definedArgumentIsUsed[j] = true;
              argumentProcessed = true;
              break;
            }
          }

          if (!argumentProcessed) {
            throw new RuntimeException("Arguments are wrong");
          }

        } else {
          for (int j = 0; j < numberOfDefinedArgs; j++) {
            boolean argumentIsValidAndNamed =
                currentArgument.getName().equals(definedArgs[j].getName())
                    && !definedArgumentIsUsed[j];

            if (argumentIsValidAndNamed) {
              argumentSortOrder[i] = j;
              definedArgumentIsUsed[j] = true;
              argumentProcessed = true;
              break;
            }
          }

          if (!argumentProcessed) {
            throw new RuntimeException("Named arguments are wrong");
          }
        }
      }

      return argumentSortOrder;
    } else {
      throw new NotInvokableException(callable, null);
    }
  }
}
