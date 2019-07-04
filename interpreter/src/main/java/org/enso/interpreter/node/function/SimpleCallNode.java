package org.enso.interpreter.node.function;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import org.enso.interpreter.TailCallException;
import org.enso.interpreter.TypeError;

public class SimpleCallNode extends CallNode {
  @Child private InteropLibrary library = InteropLibrary.getFactory().createDispatched(3);


  @Override
  public Object doCall(VirtualFrame frame, Object receiver, Object[] arguments) {
    try {
      return library.execute(receiver, arguments);
    } catch (UnsupportedTypeException | UnsupportedMessageException | ArityException e) {
      throw new TypeError("Function expected.", this);
    } catch (TailCallException e) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      CallNode replacement = new LoopingCallNode(frame.getFrameDescriptor());
      this.replace(replacement);
      return replacement.doCall(frame, e.getFunction(), e.getArguments());
    }
  }
}
