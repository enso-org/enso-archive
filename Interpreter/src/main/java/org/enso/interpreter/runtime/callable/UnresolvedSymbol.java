package org.enso.interpreter.runtime.callable;

import com.oracle.truffle.api.interop.TruffleObject;
import org.enso.interpreter.runtime.callable.atom.AtomConstructor;
import org.enso.interpreter.runtime.callable.function.Function;
import org.enso.interpreter.runtime.scope.ModuleScope;

/** Simple runtime value representing a yet-unresolved by-name symbol. */
public class UnresolvedSymbol implements TruffleObject {
  private final String name;
  private final ModuleScope scope;

  /**
   * Creates a new unresolved symbol.
   *
   * @param name the name of this symbol.
   */
  public UnresolvedSymbol(String name, ModuleScope scope) {
    this.name = name.intern();
    this.scope = scope;
  }

  /**
   * Gets the symbol name.
   *
   * <p>All names for dynamic symbols are interned, making it safe to compare symbol names using the
   * standard {@code ==} equality operator.
   *
   * @return the name of this symbol.
   */
  public String getName() {
    return name;
  }

  public Function resolveFor(AtomConstructor cons) {
    return scope.lookupMethodDefinition(cons, name);
  }
}
