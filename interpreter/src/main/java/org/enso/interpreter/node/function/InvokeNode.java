package org.enso.interpreter.node.function;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.LoopConditionProfile;
import org.enso.interpreter.TailCallException;
import org.enso.interpreter.TypeError;
import org.enso.interpreter.node.ExpressionNode;
import org.enso.interpreter.runtime.Function;

@NodeInfo(shortName = "@", description = "Executes block from child expression")
public final class InvokeNode extends ExpressionNode {
  @Child private ExpressionNode expression;
  @Children private final ExpressionNode[] arguments;
  @Child private LoopNode loopNode = null;

  public InvokeNode(ExpressionNode expression, ExpressionNode[] arguments) {
    this.expression = expression;
    this.arguments = arguments;
  }

  @Override
  @ExplodeLoop
  public Object executeGeneric(VirtualFrame frame) {
    Function function = (Function) expression.executeGeneric(frame);
    Object[] positionalArguments = new Object[arguments.length];
    for (int i = 0; i < arguments.length; i++) {
      positionalArguments[i] = arguments[i].executeGeneric(frame);
    }

    CompilerAsserts.compilationConstant(this.isTail());
    if (this.isTail()) {
      throw new TailCallException(function, positionalArguments);
    } else {
      if (loopNode == null) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        loopNode =
            insert(
                Truffle.getRuntime()
                    .createLoopNode(new RepeatedCallNode(frame.getFrameDescriptor())));
      }
      ((RepeatedCallNode) loopNode.getRepeatingNode())
          .setNextCall(frame, function, positionalArguments);
      loopNode.executeLoop(frame);
      return ((RepeatedCallNode) loopNode.getRepeatingNode()).getResult(frame);
    }
  }

  public static final class RepeatedCallNode extends Node implements RepeatingNode {
    private final FrameSlot resultSlot;
    private final FrameSlot functionSlot;
    private final FrameSlot argsSlot;
    @Child private InteropLibrary library;

    //    BranchProfile normalProfile = BranchProfile.create();
    //    BranchProfile tailProfile = BranchProfile.create();
    //    LoopConditionProfile loopConditionProfile = LoopConditionProfile.createCountingProfile();

    public RepeatedCallNode(FrameDescriptor descriptor) {
      functionSlot = descriptor.findOrAddFrameSlot("<TCO Function>", FrameSlotKind.Object);
      resultSlot = descriptor.findOrAddFrameSlot("<TCO Result>", FrameSlotKind.Object);
      argsSlot = descriptor.findOrAddFrameSlot("<TCO Arguments>", FrameSlotKind.Object);
      library = InteropLibrary.getFactory().createDispatched(3);
    }

    public void setNextCall(VirtualFrame frame, Function function, Object[] arguments) {
      frame.setObject(functionSlot, function);
      frame.setObject(argsSlot, arguments);
    }

    public Object getResult(VirtualFrame frame) {
      return FrameUtil.getObjectSafe(frame, resultSlot);
    }

    public Function getNextFunction(VirtualFrame frame) {
      Function result = (Function) FrameUtil.getObjectSafe(frame, functionSlot);
      frame.setObject(functionSlot, null);
      return result;
    }

    public Object[] getNextArgs(VirtualFrame frame) {
      Object[] result = (Object[]) FrameUtil.getObjectSafe(frame, argsSlot);
      frame.setObject(argsSlot, null);
      return result;
    }

    @Override
    public boolean executeRepeating(VirtualFrame frame) {
      try {
        Function function = getNextFunction(frame);
        Object[] arguments = getNextArgs(frame);
        frame.setObject(resultSlot, library.execute(function, arguments));
        //        normalProfile.enter();
        return false;
      } catch (TailCallException e) {
        setNextCall(frame, e.getFunction(), e.getArguments());
        //        tailProfile.enter();
        return true;
      } catch (UnsupportedTypeException | UnsupportedMessageException | ArityException e) {
        throw new TypeError("Function expected.", this);
      }
    }
  }
}
