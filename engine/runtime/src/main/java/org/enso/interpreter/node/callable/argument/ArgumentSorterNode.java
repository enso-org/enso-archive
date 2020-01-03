package org.enso.interpreter.node.callable.argument;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.NodeInfo;
import org.enso.interpreter.node.BaseNode;
import org.enso.interpreter.node.callable.InvokeCallableNode;
import org.enso.interpreter.node.callable.thunk.ThunkExecutorNode;
import org.enso.interpreter.runtime.callable.argument.CallArgumentInfo.ArgumentMapping;
import org.enso.interpreter.runtime.callable.argument.Thunk;
import org.enso.interpreter.runtime.callable.function.Function;
import org.enso.interpreter.runtime.callable.function.FunctionSchema;
import org.enso.interpreter.runtime.state.Stateful;

/**
 * This class handles the case where a mapping for reordering arguments to a given callable has
 * already been computed.
 */
@NodeInfo(shortName = "CachedArgumentSorter")
public class ArgumentSorterNode extends BaseNode {
  private final FunctionSchema preApplicationSchema;
  private final FunctionSchema postApplicationSchema;
  private final ArgumentMapping mapping;
  private @Children ThunkExecutorNode[] executors;
  private final InvokeCallableNode.ArgumentsExecutionMode argumentsExecutionMode;

  /**
   * Creates a node that generates and then caches the argument mapping.
   *
   * @param function the function to sort arguments for
   * @param schema information on the calling argument
   * @param argumentsExecutionMode the arguments execution mode for this function invocation
   */
  public ArgumentSorterNode(
      FunctionSchema preApplicationSchema,
      FunctionSchema postApplicationSchema,
      ArgumentMapping mapping,
      InvokeCallableNode.ArgumentsExecutionMode argumentsExecutionMode) {
    this.preApplicationSchema = preApplicationSchema;
    this.argumentsExecutionMode = argumentsExecutionMode;
    this.mapping = mapping;
    this.postApplicationSchema = postApplicationSchema;
  }

  /**
   * Creates a node that generates and then caches the argument mapping.
   *
   * @param function the function to sort arguments for
   * @param schema information on the calling arguments
   * @param defaultsExecutionMode the defaulted arguments execution mode for this function
   *     invocation
   * @param argumentsExecutionMode the arguments execution mode for this function invocation
   * @param isTail whether or not this node is a tail call
   * @return a sorter node for the arguments in {@code schema} being passed to {@code callable}
   */
  public static ArgumentSorterNode build(
      FunctionSchema preApplicationSchema,
      FunctionSchema postApplicationSchema,
      ArgumentMapping mapping,
      InvokeCallableNode.ArgumentsExecutionMode argumentsExecutionMode) {
    return new ArgumentSorterNode(
        preApplicationSchema, postApplicationSchema, mapping, argumentsExecutionMode);
  }

  private void initArgumentExecutors(Object[] arguments) {
    CompilerDirectives.transferToInterpreterAndInvalidate();
    executors = new ThunkExecutorNode[mapping.getArgumentShouldExecute().length];
    for (int i = 0; i < mapping.getArgumentShouldExecute().length; i++) {
      if (mapping.getArgumentShouldExecute()[i] && arguments[i] instanceof Thunk) {
        executors[i] = insert(ThunkExecutorNode.build(false));
      }
    }
  }

  @ExplodeLoop
  private Object executeArguments(Object[] arguments, Object state) {
    if (executors == null) {
      initArgumentExecutors(arguments);
    }
    for (int i = 0; i < mapping.getArgumentShouldExecute().length; i++) {
      if (executors[i] != null) {
        Stateful result = executors[i].executeThunk((Thunk) arguments[i], state);
        arguments[i] = result.getValue();
        state = result.getState();
      }
    }
    return state;
  }

  /**
   * Reorders the provided arguments into the necessary order for the cached callable.
   *
   * @param function the function this node is reordering arguments for
   * @param state the state to pass to the function
   * @param arguments the arguments to reorder
   * @return the provided {@code arguments} in the order expected by the cached {@link Function}
   */
  public Result execute(Function function, Object state, Object[] arguments) {
    if (argumentsExecutionMode.shouldExecute()) {
      state = executeArguments(arguments, state);
    }
    Object[] mappedAppliedArguments = prepareArguments(function, arguments);
    Object[] oversaturatedArguments = null;
    if (postApplicationSchema.hasOversaturatedArgs()) {
      oversaturatedArguments = generateOversaturatedArguments(function, arguments);
    }
    return new Result(state, mappedAppliedArguments, oversaturatedArguments);
  }

  public static class Result {
    private final Object state;
    private final @CompilerDirectives.CompilationFinal(dimensions = 1) Object[] sortedArguments;
    private final @CompilerDirectives.CompilationFinal(dimensions = 1) Object[]
        oversaturatedArguments;

    public Result(Object state, Object[] sortedArguments, Object[] oversaturatedArguments) {
      this.state = state;
      this.sortedArguments = sortedArguments;
      this.oversaturatedArguments = oversaturatedArguments;
    }

    public Object getState() {
      return state;
    }

    public Object[] getSortedArguments() {
      return sortedArguments;
    }

    public Object[] getOversaturatedArguments() {
      return oversaturatedArguments;
    }
  }

  private Object[] prepareArguments(Function function, Object[] arguments) {
    Object[] mappedAppliedArguments;
    if (preApplicationSchema.hasAnyPreApplied()) {
      mappedAppliedArguments = function.clonePreAppliedArguments();
    } else {
      mappedAppliedArguments = new Object[this.postApplicationSchema.getArgumentsCount()];
    }
    mapping.reorderAppliedArguments(arguments, mappedAppliedArguments);
    return mappedAppliedArguments;
  }

  /**
   * Generates an array containing the oversaturated arguments for the function being executed (if
   * any).
   *
   * <p>It accounts for oversaturated arguments at the function call site, as well as any that have
   * been 'remembered' in the passed {@link Function} object.
   *
   * @param function the function being executed
   * @param arguments the arguments being applied to {@code function}
   * @return any oversaturated arguments on {@code function}
   */
  private Object[] generateOversaturatedArguments(Function function, Object[] arguments) {
    Object[] oversaturatedArguments =
        new Object[this.postApplicationSchema.getOversaturatedArguments().length];

    System.arraycopy(
        function.getOversaturatedArguments(),
        0,
        oversaturatedArguments,
        0,
        preApplicationSchema.getOversaturatedArguments().length);

    mapping.obtainOversaturatedArguments(
        arguments, oversaturatedArguments, preApplicationSchema.getOversaturatedArguments().length);

    return oversaturatedArguments;
  }
}
