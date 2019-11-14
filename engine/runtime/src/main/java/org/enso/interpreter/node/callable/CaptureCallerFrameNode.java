package org.enso.interpreter.node.callable;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import org.enso.interpreter.node.EnsoRootNode;
import org.enso.interpreter.runtime.callable.CallerInfo;
import org.enso.interpreter.runtime.scope.LocalScope;
import org.enso.interpreter.runtime.scope.ModuleScope;

public class CaptureCallerFrameNode extends Node {
  private @CompilerDirectives.CompilationFinal LocalScope localScope;
  private @CompilerDirectives.CompilationFinal ModuleScope moduleScope;

  public CallerInfo execute(VirtualFrame frame) {
    if (localScope == null) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      EnsoRootNode rootNode = (EnsoRootNode) getRootNode();
      localScope = rootNode.getLocalScope();
      moduleScope = rootNode.getModuleScope();
    }
    return new CallerInfo(frame.materialize(), localScope, moduleScope);
  }

  public static CaptureCallerFrameNode build() {
    return new CaptureCallerFrameNode();
  }
}
