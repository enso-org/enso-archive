package org.enso.interpreter.node.function;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.FrameUtil;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RepeatingNode;
import org.enso.interpreter.TailCallException;
import org.enso.interpreter.TypeError;

public class LoopingCallNode extends CallNode {

  @Child private LoopNode loopNode;

  public LoopingCallNode(FrameDescriptor descriptor) {
    loopNode = Truffle.getRuntime().createLoopNode(new RepeatedCallNode(descriptor));
  }

  @Override
  public Object doCall(VirtualFrame frame, Object receiver, Object[] arguments) {
    ((RepeatedCallNode) loopNode.getRepeatingNode()).setNextCall(frame, receiver, arguments);
    loopNode.executeLoop(frame);
    return ((RepeatedCallNode) loopNode.getRepeatingNode()).getResult(frame);
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

    public void setNextCall(VirtualFrame frame, Object function, Object[] arguments) {
      frame.setObject(functionSlot, function);
      frame.setObject(argsSlot, arguments);
    }

    public Object getResult(VirtualFrame frame) {
      return FrameUtil.getObjectSafe(frame, resultSlot);
    }

    public Object getNextFunction(VirtualFrame frame) {
      Object result = FrameUtil.getObjectSafe(frame, functionSlot);
      frame.setObject(functionSlot, null);
      return result;
    }

    public Object[] getNextArgs(VirtualFrame frame) {
      Object[] result = new Object[0];
      try {
        result = (Object[]) frame.getObject(argsSlot);
      } catch (FrameSlotTypeException e) {
        e.printStackTrace();
      }
      frame.setObject(argsSlot, null);
      return result;
    }

    @Override
    public boolean executeRepeating(VirtualFrame frame) {
      try {
        Object function = getNextFunction(frame);
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
