package org.enso.interpreter.runtime;

import org.enso.interpreter.Language;
import org.enso.interpreter.runtime.callable.argument.ArgumentDefinition;
import org.enso.interpreter.runtime.callable.atom.AtomConstructor;
import org.enso.interpreter.runtime.scope.ModuleScope;

/** Container class for static predefined atoms and their containing scope. */
public class Builtins {
  private final ModuleScope scope = new ModuleScope();
  private final AtomConstructor unit = new AtomConstructor("Unit", scope).initializeFields();
  private final AtomConstructor nil = new AtomConstructor("Nil", scope).initializeFields();
  private final AtomConstructor cons =
      new AtomConstructor("Cons", scope)
          .initializeFields(
              new ArgumentDefinition(0, "head", false), new ArgumentDefinition(1, "rest", false));

  public Builtins(Language language) {
    scope.registerConstructor(cons);
    scope.registerConstructor(nil);
    scope.registerConstructor(unit);
  }

  public AtomConstructor getUnit() {
    return unit;
  }

  public ModuleScope getScope() {
    return scope;
  }
}
