package org.enso.interpreter.runtime;

import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;

public interface FramePointer {
  Object lookup(Frame frame);

  void store(Frame frame, Object val);
}

