package org.enso.interpreter.runtime.callable;

import com.oracle.truffle.api.interop.TruffleObject;

/** Simple runtime value representing a yet-unresolved by-name symbol. */
public class DynamicSymbol implements TruffleObject {
  private final String name;

  /** @param name the name of this symbol. */
  public DynamicSymbol(String name) {
    this.name = name.intern();
  }

  /**
   * @return the name of this symbol. NB: names of symbols are interned, so it's safe to compare
   *     them using `==`.
   */
  public String getName() {
    return name;
  }
}
