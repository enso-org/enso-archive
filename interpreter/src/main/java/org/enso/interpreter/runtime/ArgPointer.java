package org.enso.interpreter.runtime;

import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;

public class ArgPointer implements FramePointer {
  private FrameDescriptor fd;
  private int position;

  public ArgPointer(FrameDescriptor fd, int position) {
    this.fd = fd;
    this.position = position;
  }

  @Override
  public Object lookup(Frame frame) {
    if (fd == frame.getFrameDescriptor()) return frame.getArguments()[position];
    return null;
  }

  @Override
  public void store(Frame frame, Object val) {}
}
