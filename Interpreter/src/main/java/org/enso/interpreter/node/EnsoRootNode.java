package org.enso.interpreter.node;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;
import org.enso.interpreter.Language;
import org.enso.interpreter.runtime.Context;

public abstract class EnsoRootNode extends RootNode {
  private final Language language;
  private final String name;
  private final SourceSection sourceSection;
  private final FrameSlot stateFrameSlot;
  private @CompilerDirectives.CompilationFinal TruffleLanguage.ContextReference<Context>
      contextReference;

  public EnsoRootNode(
      Language language,
      FrameDescriptor frameDescriptor,
      String name,
      SourceSection sourceSection,
      FrameSlot stateFrameSlot) {
    super(language, frameDescriptor);
    this.language = language;
    this.name = name;
    this.sourceSection = sourceSection;
    this.stateFrameSlot = stateFrameSlot;
  }

  public Context getContext() {
    if (contextReference == null) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      contextReference = lookupContextReference(Language.class);
    }

    return contextReference.get();
  }

  @Override
  public String toString() {
    return this.name;
  }

  public Language getLanguage() {
    return language;
  }

  public abstract void setTail(boolean isTail);

  public FrameSlot getStateFrameSlot() {
    return this.stateFrameSlot;
  }

  @Override
  public SourceSection getSourceSection() {
    return sourceSection;
  }
}
