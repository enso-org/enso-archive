package org.enso.interpreter.runtime.callable;

import com.oracle.truffle.api.frame.MaterializedFrame;
import org.enso.interpreter.runtime.scope.LocalScope;
import org.enso.interpreter.runtime.scope.ModuleScope;

public class CallerInfo {
  private final MaterializedFrame frame;
  private final LocalScope localScope;
  private final ModuleScope moduleScope;

  public CallerInfo(MaterializedFrame frame, LocalScope localScope, ModuleScope moduleScope) {
    this.frame = frame;
    this.localScope = localScope;
    this.moduleScope = moduleScope;
  }

  public MaterializedFrame getFrame() {
    return frame;
  }

  public LocalScope getLocalScope() {
    return localScope;
  }

  public ModuleScope getModuleScope() {
    return moduleScope;
  }
}
