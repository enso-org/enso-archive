package org.enso.interpreter.node.callable;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.ConditionProfile;
import org.enso.interpreter.Constants;
import org.enso.interpreter.node.BaseNode;
import org.enso.interpreter.node.callable.argument.sorter.ArgumentSorterNode;
import org.enso.interpreter.node.callable.argument.sorter.ArgumentSorterNodeGen;
import org.enso.interpreter.runtime.callable.argument.CallArgumentInfo;
import org.enso.interpreter.runtime.callable.function.Function;

public abstract class InvokeCallableNode extends BaseNode {

  @Child private ArgumentSorterNode argumentSorter;
  @Child private MethodResolverNode methodResolverNode;

  private final boolean canApplyThis;
  private final int thisArgumentPosition;

  private final ConditionProfile methodCalledOnNonAtom = ConditionProfile.createCountingProfile();

  public InvokeCallableNode(CallArgumentInfo[] schema, boolean hasDefaultsSuspended) {

    boolean appliesThis = false;
    int idx = 0;
    for (; idx < schema.length; idx++) {
      CallArgumentInfo arg = schema[idx];

      boolean isNamedThis = arg.isNamed() && arg.getName().equals(Constants.THIS_ARGUMENT_NAME);
      if (arg.isPositional() || isNamedThis) {
        appliesThis = true;
        break;
      }
    }

    this.canApplyThis = appliesThis;
    this.thisArgumentPosition = idx;

    this.argumentSorter = ArgumentSorterNodeGen.create(schema, hasDefaultsSuspended);
    this.methodResolverNode = MethodResolverNodeGen.create();
  }

  /**
   * Invokes a function directly on the arguments contained in this node.
   *
   * @param frame the stack frame in which to execute
   * @param function the function to be executed
   * @return the result of executing {@code callable} on the known arguments
   */
  @Specialization
  public Object invokeFunction(Function function, Object[] arguments) {
    return this.argumentSorter.execute(function, arguments);
  }

  public abstract Object execute(Object callable, Object[] arguments);
}
