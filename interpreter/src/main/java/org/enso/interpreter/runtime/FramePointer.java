package org.enso.interpreter.runtime;

import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;

public class FramePointer {
  private FrameDescriptor descriptor;
  private FrameSlot slot;


  public FramePointer(FrameDescriptor descriptor, FrameSlot slot) {
    this.descriptor = descriptor;
    this.slot = slot;
  }

  public Object lookup(Frame frame) {
    if (frame.getFrameDescriptor() == descriptor) return frame.getValue(slot);
    return null;
  }
}
