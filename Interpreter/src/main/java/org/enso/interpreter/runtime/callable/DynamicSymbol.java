package org.enso.interpreter.runtime.callable;

public class DynamicSymbol {
  private final String name;

  public DynamicSymbol(String name) {
    this.name = name.intern();
  }

  public String getName() {
    return name;
  }
}
