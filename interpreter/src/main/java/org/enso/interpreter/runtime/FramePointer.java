package org.enso.interpreter.runtime;

import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;

public class FramePointer {
  private final FrameDescriptor frameDescriptor;
  private final FrameSlot frameSlot;

  public FrameDescriptor getFrameDescriptor() {
    return frameDescriptor;
  }

  public FrameSlot getFrameSlot() {
    return frameSlot;
  }

  public FramePointer(FrameDescriptor frameDescriptor, FrameSlot frameSlot) {
    this.frameDescriptor = frameDescriptor;
    this.frameSlot = frameSlot;
  }
}

