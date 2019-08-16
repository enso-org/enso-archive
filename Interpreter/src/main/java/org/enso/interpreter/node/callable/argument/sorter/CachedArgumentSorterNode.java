package org.enso.interpreter.node.callable.argument.sorter;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.nodes.NodeInfo;
import org.enso.interpreter.node.BaseNode;
import org.enso.interpreter.runtime.callable.argument.CallArgumentInfo;
import org.enso.interpreter.runtime.callable.function.ArgumentSchema;
import org.enso.interpreter.runtime.callable.function.Function;

/**
 * This class handles the case where a mapping for reordering arguments to a given callable has
 * already been computed.
 */
@NodeInfo(shortName = "CachedArgumentSorter")
public class CachedArgumentSorterNode extends BaseNode {
  private final Function originalFunction;
  private final @CompilationFinal(dimensions = 1) int[] mapping;
  private final ArgumentSchema postApplicationSchema;
  private final boolean appliesFully;

  /**
   * Creates a node that generates and then caches the argument mapping.
   *
   * @param callable the callable to sort arguments for
   * @param schema information on the calling arguments
   */
  public CachedArgumentSorterNode(Function function, CallArgumentInfo[] schema) {
    this.originalFunction = function;
    CallArgumentInfo.ArgumentMapping mapping =
        CallArgumentInfo.ArgumentMapping.generate(function.getSchema(), schema);
    this.mapping = mapping.getAppliedMapping();
    this.postApplicationSchema = mapping.getPostApplicationSchema();

    boolean fullApplication = true;
    for (int i = 0; i < postApplicationSchema.getArgumentsCount(); i++) {
      if (!(postApplicationSchema.hasDefaultAt(i) || postApplicationSchema.hasPreAppliedAt(i))) {
        fullApplication = false;
        break;
      }
    }
    appliesFully = fullApplication;
  }

  /**
   * Creates a node that generates and then caches the argument mapping.
   *
   * @param callable the callable to sort arguments for
   * @param schema information on the calling arguments
   * @return a sorter node for the arguments in {@code schema} being passed to {@code callable}
   */
  public static CachedArgumentSorterNode create(
      Function function, CallArgumentInfo[] schema) {
    return new CachedArgumentSorterNode(function, schema);
  }

  /**
   * Reorders the provided arguments into the necessary order for the cached callable.
   *
   * @param arguments the arguments to reorder
   * @param numArgsDefinedForCallable the number of arguments that the cached callable was defined
   *     for
   * @return the provided {@code arguments} in the order expected by the cached {@link Callable}
   */
  public Object[] execute(Function function, Object[] arguments) {
    Object[] result;
    if (originalFunction.getSchema().hasAnyPreApplied()) {
      result = function.clonePreAppliedArguments();
    } else {
      result = new Object[this.postApplicationSchema.getArgumentsCount()];
    }
    CallArgumentInfo.reorderArguments(this.mapping, arguments, result);
    return result;
  }

  /**
   * Determines if the provided callable is the same as the cached one.
   *
   * @param other the callable to check for equality
   * @return {@code true} if {@code other} matches the cached callable, otherwise {@code false}
   */
  public boolean isCompatible(Function other) {
    return originalFunction.getCallTarget() == other.getCallTarget()
        && originalFunction.getSchema() == other.getSchema();
  }

  public boolean appliesFully() {
    return appliesFully;
  }

  public ArgumentSchema getPostApplicationSchema() {
    return postApplicationSchema;
  }

  public Function getOriginalFunction() {
    return originalFunction;
  }
}
