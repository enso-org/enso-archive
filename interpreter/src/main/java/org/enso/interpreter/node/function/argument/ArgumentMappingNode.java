package org.enso.interpreter.node.function.argument;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import java.util.Arrays;
import org.enso.interpreter.node.BaseNode;
import org.enso.interpreter.node.function.argument.UncachedArgumentMappingNode.CallArgumentInfo;
import org.enso.interpreter.node.function.dispatch.DispatchNode;
import org.enso.interpreter.node.function.dispatch.SimpleDispatchNode;
import org.enso.interpreter.optimiser.tco.TailCallException;
import org.enso.interpreter.runtime.Callable;
import org.enso.interpreter.runtime.error.NotInvokableException;
import org.enso.interpreter.runtime.function.Function;
import org.enso.interpreter.runtime.function.argument.ArgumentDefinition;
import org.enso.interpreter.runtime.type.AtomConstructor;

public abstract class ArgumentMappingNode extends BaseNode {
  private final CallArgumentInfo[] schema;
  private final boolean isFullyPositional;
  @Child private DispatchNode dispatchNode;

  public ArgumentMappingNode(CallArgumentInfo[] schema) {
    this.schema = schema;
    this.isFullyPositional = Arrays.stream(schema).allMatch(CallArgumentInfo::isPositional);
    this.dispatchNode = new SimpleDispatchNode();
  }

  public static int[] generateArgMapping(Object callable, CallArgumentInfo[] callArgs) {
    if (callable instanceof Callable) {
      Callable realCallable = (Callable) callable;
      // TODO [AA] Factor out
      int numberOfDefinedArgs = realCallable.getArgs().length;

      ArgumentDefinition[] definedArgs = realCallable.getArgs();

      //    if (this.schema.length != numberOfDefinedArgs) {
      //      throw new ArityException(numberOfDefinedArgs, this.schema.length);
      //    }

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
    //    if (callable instanceof Function) {
    Function actualCallable = (Function) callable;
    if (this.isTail()) {
      throw new TailCallException(actualCallable, arguments);
    } else {
      return dispatchNode.executeDispatch(actualCallable, arguments);
    }
    //
    //    } else if (callable instanceof AtomConstructor) {
    //      AtomConstructor actualCallable = (AtomConstructor) callable;
    //      return actualCallable.newInstance(arguments);
    //
    //    } else {
    //      throw new NotInvokableException(callable, this);
    //    }
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

    //    if (callable instanceof Function) {
    Function actualCallable = (Function) callable;
    if (this.isTail()) {
      throw new TailCallException(actualCallable, arguments);
    } else {
      return dispatchNode.executeDispatch(actualCallable, arguments);
    }

    //    } else if (callable instanceof AtomConstructor) {
    //      AtomConstructor actualCallable = (AtomConstructor) callable;
    //      return actualCallable.newInstance(arguments);
    //
    //    } else {
    //      throw new NotInvokableException(callable, this);
    //    }
  }

  @Specialization
  public Object invokeUncached(
      Object callable,
      Object[] arguments,
      @Cached("create(getSchema())") UncachedArgumentMappingNode mappingNode) {
    arguments = mappingNode.execute(callable, arguments);

    //    if (callable instanceof Function) {
    Function actualCallable = (Function) callable;
    if (this.isTail()) {
      throw new TailCallException(actualCallable, arguments);
    } else {
      return dispatchNode.executeDispatch(actualCallable, arguments);
    }
    //
    //    } else if (callable instanceof AtomConstructor) {
    //      AtomConstructor actualCallable = (AtomConstructor) callable;
    //      return actualCallable.newInstance(arguments);
    //
    //    } else {
    //      throw new NotInvokableException(callable, this);
    //    }
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

  private Object doCall(Object callable, Object[] arguments) {
    //    if (callable instanceof Function) {
    Function actualCallable = (Function) callable;
    if (this.isTail()) {
      throw new TailCallException(actualCallable, arguments);
    } else {
      return dispatchNode.executeDispatch(actualCallable, arguments);
    }
    //
    //    } else if (callable instanceof AtomConstructor) {
    //      AtomConstructor actualCallable = (AtomConstructor) callable;
    //      return actualCallable.newInstance(arguments);
    //
    //    } else {
    //      throw new NotInvokableException(callable, this);
    //    }
  }
}
