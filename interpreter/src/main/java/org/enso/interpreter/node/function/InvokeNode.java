package org.enso.interpreter.node.function;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.NodeInfo;
import java.util.List;
import java.util.Map;
import org.enso.interpreter.node.ExpressionNode;
import org.enso.interpreter.node.function.argument.ArgumentDefinition;
import org.enso.interpreter.node.function.argument.CallArgument;
import org.enso.interpreter.optimiser.TailCallException;
import org.enso.interpreter.runtime.Atom;
import org.enso.interpreter.runtime.AtomConstructor;
import org.enso.interpreter.runtime.Callable;
import org.enso.interpreter.runtime.Function;
import org.enso.interpreter.runtime.TypesGen;
import org.enso.interpreter.runtime.errors.ArgumentNameException;
import org.enso.interpreter.runtime.errors.ArityException;
import org.enso.interpreter.runtime.errors.NotInvokableException;

@NodeInfo(shortName = "@", description = "Executes function")
@NodeChild("target")
public abstract class InvokeNode extends ExpressionNode {
  @Children private final CallArgument[] callArguments;
  @Child private DispatchNode dispatchNode;

  public InvokeNode(CallArgument[] callArguments) {
    this.callArguments = callArguments;
    this.dispatchNode = new SimpleDispatchNode();
  }

  // TODO [AA] Use specialisation and rewriting with an inline cache to speed this up.
  // TODO [AA] Actually make use of the other argument types.
  @ExplodeLoop
  public Object[] computeArguments(VirtualFrame frame, Callable callable) {
    List<ArgumentDefinition> definedArgs = callable.getArgs();
    Map<String, ArgumentDefinition> fnArgsByName = callable.getFnArgsByName();
    Map<Integer, ArgumentDefinition> fnArgsByPosition = callable.getFnArgsByPosition();

    if (callArguments.length > definedArgs.size()) {
      throw new ArityException(definedArgs.size(), callArguments.length);
    }

    Object[] positionalArguments = new Object[definedArgs.size()]; // Note [Positional Arguments]

    for (int i = 0; i < this.callArguments.length; i++) {
      CallArgument arg = this.callArguments[i];

      // In order handle positional, named, then defaults, then ignored
      if (arg.isPositional()) {
        positionalArguments[i] = arg.executeGeneric(frame);
      } else if (arg.isNamed()) {
        String inputArgName = arg.getName().get();

        if (!fnArgsByName.containsKey(inputArgName)) {
          throw new ArgumentNameException(inputArgName, fnArgsByName.keySet());
        }

        int realPosition = fnArgsByName.get(inputArgName).getPosition();
        positionalArguments[realPosition] = arg.executeGeneric(frame);
      } else if (arg.isIgnored()) {
        // TODO [AA] Sentinel ignored so as not to apply defaults to the position.
      } else {
        // TODO [AA] Deal with argument defaults.
      }
    }

    return positionalArguments;
  }

  /* Note [Positional Arguments]
   * ~~~~~~~~~~~~~~~~~~~~~~~~~~~
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
