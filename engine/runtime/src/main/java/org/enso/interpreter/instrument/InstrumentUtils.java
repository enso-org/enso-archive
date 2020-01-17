package org.enso.interpreter.instrument;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstanceVisitor;

public class InstrumentUtils {

  public static boolean isTopFrame(String rootName) {
    Object result =
        Truffle.getRuntime()
            .iterateFrames(
                new FrameInstanceVisitor<Object>() {
                  boolean seenFirst = false;

                  @Override
                  public Object visitFrame(FrameInstance frameInstance) {
                    CallTarget ct = frameInstance.getCallTarget();
                    if (ct instanceof RootCallTarget
                        && !rootName.equals(((RootCallTarget) ct).getRootNode().getName())) {
                      return null;
                    }
                    if (seenFirst) {
                      return new Object();
                    } else {
                      seenFirst = true;
                      return null;
                    }
                  }
                });
    return result == null;
  }
}
