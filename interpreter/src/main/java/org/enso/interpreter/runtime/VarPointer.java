package org.enso.interpreter.runtime;

import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;

public class VarPointer implements FramePointer {
  private FrameDescriptor descriptor;
  private FrameSlot slot;


  public VarPointer(FrameDescriptor descriptor, FrameSlot slot) {
    this.descriptor = descriptor;
    this.slot = slot;
  }

  public Object lookup(Frame frame) {
    if (frame.getFrameDescriptor() == descriptor) return frame.getValue(slot);
    return null;
  }

  @Override
  public void store(Frame frame, Object val) {
    frame.setObject(slot, val);
  }
}
