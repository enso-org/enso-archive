package org.enso.interpreter.node.function;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.enso.interpreter.node.ExpressionNode;
import org.enso.interpreter.node.function.argument.CallArgumentNode;
import org.enso.interpreter.runtime.function.argument.ArgumentDefinition;
import org.enso.interpreter.optimiser.tco.TailCallException;
import org.enso.interpreter.runtime.type.Atom;
import org.enso.interpreter.runtime.type.AtomConstructor;
import org.enso.interpreter.runtime.function.Callable;
import org.enso.interpreter.runtime.function.Function;
import org.enso.interpreter.runtime.TypesGen;
import org.enso.interpreter.runtime.error.ArityException;
import org.enso.interpreter.runtime.error.NotInvokableException;

@NodeInfo(shortName = "@", description = "Executes function")
@NodeChild("target")
public abstract class InvokeNode extends ExpressionNode {
  @Children private final CallArgumentNode[] callArgumentNodes;
  @Child private DispatchNode dispatchNode;
  private final Map<String, Integer> callArgsByName;
  private boolean isSaturatedApplication;

  public InvokeNode(CallArgumentNode[] callArgumentNodes) {
    this.callArgumentNodes = callArgumentNodes;
    this.dispatchNode = new SimpleDispatchNode();
    this.isSaturatedApplication = true;

    // Note [Call Arguments by Name]
    this.callArgsByName =
        IntStream.range(0, callArgumentNodes.length)
            .filter(idx -> callArgumentNodes[idx].getName() != null)
            .boxed()
            .collect(Collectors.toMap(idx -> callArgumentNodes[idx].getName(), idx -> idx));
  }

  /* Note [Call Arguments by Name]
   * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
   * Not all arguments passed into a function call are given by name, however, we need to be able to
   * look up those that _are_ named efficiently.
   *
   * TODO [AA] Finish this comment
   */

  /**
   * Looks up the argument by the provided key type in the appropriate map.
   *
   * <p>This method exists because the lookups need to take place in the interpreter (behind the
   * truffle boundary). If they do not, then the partial evaluator tries to inline the map lookups
   * to a significant depth.
   *
   * @param map The map in which to look up the key.
   * @param key The key to use for lookup.
   * @param <K> The key type of the map.
   * @return `true` if the key exists, otherwise `false`.
   */
  @TruffleBoundary
  public static <K> boolean hasArgByKey(Map<K, Integer> map, K key) {
    return map.containsKey(key);
  }

  /**
   * Retrieves the argument for a given key from the provided map.
   *
   * @param map The map in which to look up the argument.
   * @param key The key to use to find the argument.
   * @param <K> The type of the key in the map.
   * @return The value, if found, `null` otherwise.
   */
  @TruffleBoundary
  public <K> CallArgumentNode getArgByKey(Map<K, Integer> map, K key) {
    return callArgumentNodes[map.get(key)];
  }

  // TODO [AA] Use specialisation and rewriting with an inline cache to speed this up.
  public Object[] computeArguments(VirtualFrame frame, Callable callable) {
    ArgumentDefinition[] definedArgs = callable.getArgs();

    // TODO [AA] Handle the case where we have too many arguments
    // TODO [AA] Handle the case where we have too few arguments

    // Temporary failure conditions
    if (definedArgs.length != this.callArgumentNodes.length) {
      throw new ArityException(definedArgs.length, this.callArgumentNodes.length);
    }

    /* TODO [AA] Mapping between call site args and function args
     * Can do a child node that handles the argument matching to the Function purely on runtime
     * values.
     * - General case variant (matching below).
     * - An optimised variant that works on a precomputed mapping.
     */

    Object[] positionalArguments = new Object[definedArgs.length]; // Note [Positional Arguments]
//
//    for (ArgumentDefinition definedArg : definedArgs) {
//      int definedArgPosition = definedArg.getPosition();
//      String definedArgName = definedArg.getName();
//
//      if (hasArgByKey(callArgsByName, definedArgName)) {
//        CallArgument callArg = callArguments[getArgByKey(callArgsByName, definedArgName)];
//        positionalArguments[definedArgPosition] = callArg.executeGeneric(frame);
//
//      } else if (hasArgByKey(callArgsByPosition, definedArgPosition)) {
//        CallArgument callArg = getArgByKey(callArgsByPosition, definedArgPosition);
//        positionalArguments[definedArgPosition] = callArg.executeGeneric(frame);
//
//      } else if (definedArg.hasDefaultValue()) {
//        positionalArguments[definedArgPosition] = new DefaultedArgument(definedArg);
//      } else {
//        positionalArguments[definedArgPosition] = new UnappliedArgument(definedArg);
//        this.isSaturatedApplication = false;
//      }
//    }

    // TODO [AA] Return a function with some arguments applied.
    // TODO [AA] Loop nodes, returning new call targrets for under-saturated.
    // TODO [AA] Too many arguments need to execute. Overflow args in an array.
    // TODO [AA] Looping until done.

    return positionalArguments;
  }

  /* Note [Positional Arguments]
   * ~~~~~~~~~~~~~~~~~~~~~~~~~~~
   * // TODO [AA] Rewrite this
   */

  // You can query this function about its arguments.
  @Specialization
  public Object invokeFunction(VirtualFrame frame, Function target) {
    Object[] positionalArguments = computeArguments(frame, target);

    CompilerAsserts.compilationConstant(this.isTail());
    if (this.isTail()) {
      throw new TailCallException(target, positionalArguments);
    } else {
      return dispatchNode.executeDispatch(target, positionalArguments);
    }
  }

  // TODO [AA] Need to handle named and defaulted args for constructors as well.
  @Specialization
  public Atom invokeConstructor(VirtualFrame frame, AtomConstructor constructor) {
    Object[] positionalArguments = computeArguments(frame, constructor);
    return constructor.newInstance(positionalArguments);
  }

  @Fallback
  public Object invokeGeneric(VirtualFrame frame, Object target) {
    if (TypesGen.isFunction(target)) return invokeFunction(frame, (Function) target);
    if (TypesGen.isAtomConstructor(target))
      return invokeConstructor(frame, (AtomConstructor) target);
    throw new NotInvokableException(target, this);
  }
}
