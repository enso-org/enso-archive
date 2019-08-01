package org.enso.interpreter.node.function;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.NodeInfo;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.enso.interpreter.node.ExpressionNode;
import org.enso.interpreter.node.function.argument.ArgumentMappingNode;
import org.enso.interpreter.node.function.argument.CallArgumentNode;
import org.enso.interpreter.optimiser.tco.TailCallException;
import org.enso.interpreter.runtime.Callable;
import org.enso.interpreter.runtime.TypesGen;
import org.enso.interpreter.runtime.error.ArityException;
import org.enso.interpreter.runtime.error.NotInvokableException;
import org.enso.interpreter.runtime.function.Function;
import org.enso.interpreter.runtime.type.Atom;
import org.enso.interpreter.runtime.type.AtomConstructor;

@NodeInfo(shortName = "@", description = "Executes function")
@NodeChild("target")
public abstract class InvokeNode extends ExpressionNode {
  @Children private final CallArgumentNode[] callArgumentNodes;
  @Child private ArgumentMappingNode argumentsMap;
  private final Map<String, Integer> callArgsByName;
  private boolean isSaturatedApplication;

  public InvokeNode(CallArgumentNode[] callArgumentNodes) {
    this.callArgumentNodes = callArgumentNodes;
    //    this.dispatchNode = new SimpleDispatchNode();
    this.isSaturatedApplication = true;

    // Note [Call Arguments by Name]
    this.callArgsByName =
        IntStream.range(0, callArgumentNodes.length)
            .filter(idx -> callArgumentNodes[idx].getName() != null)
            .boxed()
            .collect(Collectors.toMap(idx -> callArgumentNodes[idx].getName(), idx -> idx));

//    CallArgumentInfo[] argSchema =
//        IntStream.range(0, callArgumentNodes.length)
//            .mapToObj(i -> callArgumentNodes[i])
//            .map(node -> new CallArgumentInfo(null));

    this.argumentsMap = null;
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
  @ExplodeLoop
  public Object[] computeArguments(VirtualFrame frame, Callable callable) {
    int definedArgsLength = callable.getArgs().length;

    // Temporary failure condition
    if (callable.getArgs().length != this.callArgumentNodes.length) {
      throw new ArityException(definedArgsLength, this.callArgumentNodes.length);
    }

    Object[] computedArguments = new Object[definedArgsLength]; // Note [Positional Arguments]

    for (int i = 0; i < this.callArgumentNodes.length; ++i) {
      computedArguments[i] = this.callArgumentNodes[i].executeGeneric(frame);
    }

    return computedArguments;
  }

  /* Note [Positional Arguments]
   * ~~~~~~~~~~~~~~~~~~~~~~~~~~~
   * // TODO [AA] Rewrite this
   */

  @Specialization
  public Object invokeFunction(VirtualFrame frame, Function target) {
    // This should just become value computation _without_ any unscrambling
    Object[] computedArguments = computeArguments(frame, target);

    CompilerAsserts.compilationConstant(this.isTail());
    // The TCO Logic should land inside `DispatchNode` after this change. `InvokeNode` will only
    // care about computing the target and arguments, pushing every other decision down the chain.

    // This is hard to push in further as the next point in the chain is not an ExpressionNode and
    // hence has no concept of a tail call.
    if (this.isTail()) {
      throw new TailCallException(target, computedArguments);
    } else {
      return argumentsMap.execute(target, computedArguments);
    }
  }

  // TODO [AA] Need to handle named and defaulted args for constructors as well.
  @Specialization
  public Atom invokeConstructor(VirtualFrame frame, AtomConstructor constructor) {
    Object[] computedArguments = computeArguments(frame, constructor);
    return constructor.newInstance(computedArguments);
  }

  @Fallback
  public Object invokeGeneric(VirtualFrame frame, Object target) {
    if (TypesGen.isFunction(target)) return invokeFunction(frame, (Function) target);
    if (TypesGen.isAtomConstructor(target))
      return invokeConstructor(frame, (AtomConstructor) target);
    throw new NotInvokableException(target, this);
  }
}
