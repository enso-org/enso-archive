package org.enso.interpreter.node.function.argument;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.NodeInfo;
import java.util.Arrays;
import org.enso.interpreter.node.BaseNode;
import org.enso.interpreter.node.function.DoCallNode;
import org.enso.interpreter.node.function.DoCallNodeGen;
import org.enso.interpreter.runtime.Callable;
import org.enso.interpreter.runtime.error.NotInvokableException;
import org.enso.interpreter.runtime.function.Function;
import org.enso.interpreter.runtime.function.argument.ArgumentDefinition;
import org.enso.interpreter.runtime.function.argument.CallArgument;
import org.enso.interpreter.runtime.type.AtomConstructor;

@NodeInfo(shortName = "ArgMap")
public abstract class ArgumentMappingNode extends BaseNode {
  private @CompilationFinal(dimensions = 1) CallArgumentInfo[] schema;
  private final boolean isFullyPositional;
  @Child private DoCallNode doCallNode;

  public ArgumentMappingNode(CallArgumentInfo[] schema) {
    this.schema = schema;
    this.isFullyPositional = Arrays.stream(schema).allMatch(CallArgumentInfo::isPositional);
    this.doCallNode = DoCallNodeGen.create();
  }

  @Override
  public void markTail() {
    super.markTail();
    this.doCallNode.markTail();
  }

  @Override
  public void markNotTail() {
    super.markNotTail();
    this.doCallNode.markNotTail();
  }

  @Override
  public void setTail(boolean isTail) {
    super.setTail(isTail);
    this.doCallNode.setTail(isTail);
  }

  // TODO [AA] Have a doCallNode that takes a callable and the reordered arguments
  // TODO [AA] Specialise on Function/AtomConstructor inside that

  // TODO use specialisations here too
  public static int[] generateArgMapping(Object callable, CallArgumentInfo[] callArgs) {
    if (callable instanceof Callable) {
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
      // FIXME [AA] Shouldn't be null.
      throw new NotInvokableException(callable, null);
    }
  }

  @Specialization(guards = "isFullyPositional()")
  public Object invokePositional(Object callable, Object[] arguments) {
    return this.doCallNode.execute(callable, arguments);
  }

  @Specialization(guards = "isSameCallable(cachedCallable, callable)")
  @ExplodeLoop
  public Object invokeCached(
      Object callable,
      Object[] arguments,
      @Cached("callable") Object cachedCallable,
      @Cached(value = "generateArgMapping(callable, getSchema())", dimensions = 1) int[] mapping) {
    Object[] result = new Object[arguments.length];
    for (int i = 0; i < arguments.length; i++) {
      result[mapping[i]] = arguments[i];
    }

    arguments = result;

    return this.doCallNode.execute(callable, arguments);
  }

  @Specialization
  public Object invokeUncached(
      Object callable,
      Object[] arguments,
      @Cached("create(getSchema())") UncachedArgumentMappingNode mappingNode) {
    arguments = mappingNode.execute(callable, arguments);

    return this.doCallNode.execute(callable, arguments);
  }

  public boolean isSameCallable(Object left, Object right) {
    if (left instanceof AtomConstructor) {
      return left == right;
    } else if (left instanceof Function) {
      if (right instanceof Function) {
        return ((Function) left).getCallTarget() == ((Function) right).getCallTarget();
      }
    }
    return false;
  }

  public abstract Object execute(Object callable, Object[] arguments);

  public CallArgumentInfo[] getSchema() {
    return schema;
  }

  public boolean isFullyPositional() {
    return isFullyPositional;
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
