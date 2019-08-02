package org.enso.interpreter.node;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeInfo;

@NodeInfo(shortName = "Base", description = "A base node for the Enso AST")
@ReportPolymorphism
public class BaseNode extends Node {
  @CompilerDirectives.CompilationFinal private boolean isTail = false;

  public void markTail() {
    isTail = true;
  }

  public void markNotTail() {
    isTail = false;
  }

  public boolean isTail() {
    return isTail;
  }
}
