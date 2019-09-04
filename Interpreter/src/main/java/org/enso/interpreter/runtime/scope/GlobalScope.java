package org.enso.interpreter.runtime.scope;

import org.enso.interpreter.runtime.Builtins;
import org.enso.interpreter.runtime.callable.atom.AtomConstructor;
import org.enso.interpreter.runtime.callable.function.Function;

import java.util.*;

/** A representation of Enso's top-level scope. */
public class GlobalScope {

  private final Map<String, AtomConstructor> constructors = new HashMap<>();
  private final Map<AtomConstructor, Map<String, Function>> methods = new HashMap<>();
  private final Set<GlobalScope> imports = new HashSet<>();
  private final Set<GlobalScope> transitiveImports = new HashSet<>();

  public GlobalScope() {
    imports.add(Builtins.BUILTIN_SCOPE);
  }

  /**
   * Adds an Atom constructor definition to the global scope.
   *
   * @param constructor the constructor to register
   */
  public void registerConstructor(AtomConstructor constructor) {
    constructors.put(constructor.getName(), constructor);
  }

  /**
   * Looks up a constructor in the global scope.
   *
   * @param name the name of the global binding
   * @return the Atom constructor associated with {@code name}, or {@link Optional#empty()}
   */
  public Optional<AtomConstructor> getConstructor(String name) {
    Optional<AtomConstructor> locallyDefined = Optional.ofNullable(this.constructors.get(name));
    if (locallyDefined.isPresent()) return locallyDefined;
    return imports.stream()
        .map(scope -> scope.constructors.get(name))
        .filter(Objects::nonNull)
        .findFirst();
  }

  private Map<String, Function> getMethodMapFor(AtomConstructor atom) {
    Map<String, Function> result = methods.get(atom);
    if (result == null) {
      result = new HashMap<>();
      methods.put(atom, result);
    }
    return result;
  }

  /**
   * Registers a method defined for a given type.
   *
   * @param atom type the method was defined for.
   * @param method method name.
   * @param function the {@link Function} associated with this definition.
   */
  public void registerMethod(AtomConstructor atom, String method, Function function) {
    getMethodMapFor(atom).put(method, function);
  }

  /**
   * Looks up the definition for a given type and method name.
   *
   * @param atom type to lookup the method for.
   * @param name the method name.
   * @return the matching method definition or null if not found.
   */
  public Function lookupMethodDefinition(AtomConstructor atom, String name) {
    Function definedWithAtom = atom.getDefinitionScope().getMethodMapFor(atom).get(name);
    if (definedWithAtom != null) return definedWithAtom;
    Function definedHere = getMethodMapFor(atom).get(name);
    if (definedHere != null) return definedHere;
    return transitiveImports.stream()
        .map(scope -> scope.getMethodMapFor(atom).get(name))
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(null);
  }

  protected Set<GlobalScope> getTransitiveImports() {
    return transitiveImports;
  }

  public void addImport(GlobalScope scope) {
    imports.add(scope);
    transitiveImports.add(scope);
    transitiveImports.addAll(scope.transitiveImports);
  }
}
