package org.enso.interpreter.node.function;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.enso.interpreter.node.ExpressionNode;
import org.enso.interpreter.node.function.argument.ArgumentDefinition;
import org.enso.interpreter.node.function.argument.CallArgument;
import org.enso.interpreter.node.function.argument.DefaultedArgument;
import org.enso.interpreter.node.function.argument.UnappliedArgument;
import org.enso.interpreter.optimiser.TailCallException;
import org.enso.interpreter.runtime.Atom;
import org.enso.interpreter.runtime.AtomConstructor;
import org.enso.interpreter.runtime.Callable;
import org.enso.interpreter.runtime.Function;
import org.enso.interpreter.runtime.TypesGen;
import org.enso.interpreter.runtime.errors.ArityException;
import org.enso.interpreter.runtime.errors.NotInvokableException;

@NodeInfo(shortName = "@", description = "Executes function")
@NodeChild("target")
public abstract class InvokeNode extends ExpressionNode {
  @Children private final CallArgument[] callArguments;
  @Child private DispatchNode dispatchNode;
  private final Map<String, Integer> callArgsByName;
  private boolean isSaturatedApplication;

  public InvokeNode(CallArgument[] callArguments) {
    this.callArguments = callArguments;
    this.dispatchNode = new SimpleDispatchNode();
    this.isSaturatedApplication = true;

    // Note [Call Arguments by Name]
    this.callArgsByName =
        IntStream.range(0, callArguments.length)
            .filter(idx -> callArguments[idx].getName() != null)
            .boxed()
            .collect(Collectors.toMap(idx -> callArguments[idx].getName(), idx -> idx));
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
   * @param <V> The value type of the map.
   * @return `true` if the key exists, otherwise `false`.
   */
  @TruffleBoundary
  public static <K, V> boolean hasArgByKey(Map<K, V> map, K key) {
    return map.containsKey(key);
  }

  /**
   * Retrieves the argument for a given key from the provided map.
   *
   * @param map The map in which to look up the argument.
   * @param key The key to use to find the argument.
   * @param <K> The type of the key in the map.
   * @param <V> The type of the value returned.
   * @return The value, if found, `null` otherwise.
   */
  @TruffleBoundary
  public static <K, V> V getArgByKey(Map<K, V> map, K key) {
    return map.get(key);
  }

  // TODO [AA] Use specialisation and rewriting with an inline cache to speed this up.
  public Object[] computeArguments(VirtualFrame frame, Callable callable) {
    ArgumentDefinition[] definedArgs = callable.getArgs();

    if (callArguments.length > definedArgs.length) {
      throw new ArityException(definedArgs.length, callArguments.length);
    }

    Object[] positionalArguments = new Object[definedArgs.length]; // Note [Positional Arguments]

    for (ArgumentDefinition definedArg : definedArgs) {
      int definedArgPosition = definedArg.getPosition();
      String definedArgName = definedArg.getName();

      if (hasArgByKey(callArgsByName, definedArgName)) {
        CallArgument callArg = callArguments[getArgByKey(callArgsByName, definedArgName)];
        positionalArguments[definedArgPosition] = callArg.executeGeneric(frame);

      } else if (hasArgByKey(callArgsByPosition, definedArgPosition)) {
        CallArgument callArg = getArgByKey(callArgsByPosition, definedArgPosition);
        positionalArguments[definedArgPosition] = callArg.executeGeneric(frame);

      } else if (definedArg.hasDefaultValue()) {
        positionalArguments[definedArgPosition] = new DefaultedArgument(definedArg);
      } else {
        positionalArguments[definedArgPosition] = new UnappliedArgument(definedArg);
        this.isSaturatedApplication = false;
      }
    }

    return positionalArguments;
  }

  /* Note [Positional Arguments]
   * ~~~~~~~~~~~~~~~~~~~~~~~~~~~
   * // TODO [AA] Rewrite this
   * As far as Graal itself is concerned, the only kind of argument that can be passed to a call
   * target are positional ones. This means that we need our own scheme for pulling the types of
   * arguments we care about back out of the call when it reaches our code.
   *
   * However, at the point of calling `computeArguments` above, we actually have both the defined
   * arguments to the function, and the arguments with which it's actually being called. This means
   * that we can actually treat them all as positional, using the following algorithm:
   *
   * 1.
   *
   * After the loop completes, any arguments that have not yet been applied are `null`. This means
   * that we can use this as a sentinel for whether a function is fully-applied or not.
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
