package org.enso.interpreter.runtime;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import org.enso.interpreter.Language;
import org.enso.interpreter.node.expression.builtin.PrintNode;
import org.enso.interpreter.node.expression.builtin.PrintNodeGen;
import org.enso.interpreter.runtime.callable.argument.ArgumentDefinition;
import org.enso.interpreter.runtime.callable.atom.AtomConstructor;
import org.enso.interpreter.runtime.callable.function.ArgumentSchema;
import org.enso.interpreter.runtime.callable.function.Function;
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
  private final AtomConstructor builtins =
      new AtomConstructor("Builtins", scope).initializeFields();

  public Builtins(Language language) {
    scope.registerConstructor(cons);
    scope.registerConstructor(nil);
    scope.registerConstructor(unit);
    scope.registerConstructor(builtins);

    installBuiltinMethods(builtins, language);
  }

  private void installBuiltinMethods(AtomConstructor atom, Language language) {
    scope.registerMethod(
        atom,
        "print",
        new Function(
            printCallTarget(language),
            null,
            new ArgumentSchema(
                new ArgumentDefinition(0, "this", false),
                new ArgumentDefinition(1, "value", false))));
  }

  private RootCallTarget printCallTarget(Language language) {
    PrintNode node = PrintNodeGen.create(language);
    return Truffle.getRuntime().createCallTarget(node);
  }

  public AtomConstructor getUnit() {
    return unit;
  }

  public ModuleScope getScope() {
    return scope;
  }
}
