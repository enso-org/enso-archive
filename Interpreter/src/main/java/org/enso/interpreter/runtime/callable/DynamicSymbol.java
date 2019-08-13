package org.enso.interpreter.runtime.callable;

import com.oracle.truffle.api.interop.TruffleObject;

public class DynamicSymbol implements TruffleObject {
  private final String name;

  public DynamicSymbol(String name) {
    this.name = name.intern();
  }

  public String getName() {
    return name;
  }
}
