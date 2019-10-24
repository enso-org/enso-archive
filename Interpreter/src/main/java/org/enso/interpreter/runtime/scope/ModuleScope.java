package org.enso.interpreter.runtime.scope;

import com.oracle.truffle.api.CompilerDirectives;
import org.enso.interpreter.runtime.Builtins;
import org.enso.interpreter.runtime.Module;
import org.enso.interpreter.runtime.callable.atom.AtomConstructor;
import org.enso.interpreter.runtime.callable.function.Function;

import javax.swing.text.html.Option;
import java.util.*;

/** A representation of Enso's per-file top-level scope. */
public class ModuleScope {

  private final Map<String, AtomConstructor> constructors = new HashMap<>();
  private final Map<AtomConstructor, Map<String, Function>> methods = new HashMap<>();
  private final Map<String, Function> anyMethods = new HashMap<>();
  private final Set<ModuleScope> imports = new HashSet<>();
  private final Set<ModuleScope> transitiveImports = new HashSet<>();

  /**
   * Adds an Atom constructor definition to the module scope.
   *
   * @param constructor the constructor to register
   */
  public void registerConstructor(AtomConstructor constructor) {
    constructors.put(constructor.getName(), constructor);
  }

  /**
   * Looks up a constructor in the module scope.
   *
   * @param name the name of the module binding
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

  /**
   * Returns a map of methods defined in this module for a given constructor.
   *
   * @param cons the constructor for which method map is requested
   * @return a map containing all the defined methods by name
   */
  private Map<String, Function> getMethodMapFor(AtomConstructor cons) {
    Map<String, Function> result = methods.get(cons);
    if (result == null) {
      result = new HashMap<>();
      methods.put(cons, result);
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

  public void registerAnyMethod(String method, Function function) {
    anyMethods.put(method, function);
  }

  /**
   * Looks up the definition for a given type and method name.
   *
   * <p>The resolution algorithm is first looking for methods defined at the constructor definition
   * site (i.e. non-overloads), then looks for methods defined in this scope and finally tries to
   * resolve the method in all transitive dependencies of this module.
   *
   * <p>If the specific search fails, methods defined for any type are searched, first looking at
   * locally defined methods and then all the transitive imports.
   *
   * @param atom type to lookup the method for.
   * @param name the method name.
   * @return the matching method definition or null if not found.
   */
  @CompilerDirectives.TruffleBoundary
  public Function lookupMethodDefinition(AtomConstructor atom, String name) {
    return lookupMethodDefinitionForAtom(atom, name)
        .orElseGet(() -> lookupMethodDefinitionForAny(name).orElse(null));
  }

  private Optional<Function> lookupMethodDefinitionForAny(String name) {
    Function definedHere = anyMethods.get(name);
    if (definedHere != null) {
      return Optional.of(definedHere);
    }
    return transitiveImports.stream()
        .map(scope -> scope.getAnyMethods().get(name))
        .filter(Objects::nonNull)
        .findFirst();
  }

  private Optional<Function> lookupMethodDefinitionForAtom(AtomConstructor atom, String name) {
    Function definedWithAtom = atom.getDefinitionScope().getMethodMapFor(atom).get(name);
    if (definedWithAtom != null) {
      return Optional.of(definedWithAtom);
    }
    Function definedHere = getMethodMapFor(atom).get(name);
    if (definedHere != null) {
      return Optional.of(definedHere);
    }
    return transitiveImports.stream()
        .map(scope -> scope.getMethodMapFor(atom).get(name))
        .filter(Objects::nonNull)
        .findFirst();
  }

  /**
   * Returns all the transitive dependencies of this module.
   *
   * @return a set of all the transitive dependencies of this module
   */
  private Set<ModuleScope> getTransitiveImports() {
    return transitiveImports;
  }

  private Map<String, Function> getAnyMethods() {
    return anyMethods;
  }

  /**
   * Adds a dependency for this module.
   *
   * @param scope the scope of the newly added dependency
   */
  public void addImport(ModuleScope scope) {
    imports.add(scope);
    transitiveImports.add(scope);
    transitiveImports.addAll(scope.transitiveImports);
  }
}
