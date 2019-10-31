package org.enso.interpreter.runtime.state;

public class Stateful {
  private final Object state;
  private final Object value;

  public Stateful(Object state, Object value) {
    this.state = state;
    this.value = value;
  }

  public Object getState() {
    return state;
  }

  public Object getValue() {
    return value;
  }
}
