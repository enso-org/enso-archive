package org.enso.interpreter.runtime.interop;

import com.oracle.truffle.api.interop.TruffleObject;

public class JavaObject implements TruffleObject {
  private final Object obj;

  public JavaObject(Object obj) {
    this.obj = obj;
  }

  public Object getObj() {
    return obj;
  }

  @Override
  public String toString() {
    return obj.toString();
  }
}
