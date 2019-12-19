package org.enso.interpreter.runtime.scope;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import org.enso.interpreter.runtime.callable.atom.AtomConstructor;
import org.enso.interpreter.runtime.callable.function.Function;
import org.enso.interpreter.runtime.data.Vector;

import java.util.*;

/** A representation of Enso's per-file top-level scope. */
@ExportLibrary(InteropLibrary.class)
public class ModuleScope implements TruffleObject {
  private final AtomConstructor associatedType;
  private final Map<String, AtomConstructor> constructors = new HashMap<>();
  private final Map<AtomConstructor, Map<String, Function>> methods = new HashMap<>();
  private final Map<String, Function> anyMethods = new HashMap<>();
  private final Map<String, Function> numberMethods = new HashMap<>();
  private final Map<String, Function> functionMethods = new HashMap<>();
  private final Set<ModuleScope> imports = new HashSet<>();
  private final Set<ModuleScope> transitiveImports = new HashSet<>();

  public ModuleScope(String name) {
    associatedType = new AtomConstructor(name, this).initializeFields();
  }

  /**
   * Adds an Atom constructor definition to the module scope.
   *
   * @param constructor the constructor to register
   */
  public void registerConstructor(AtomConstructor constructor) {
    constructors.put(constructor.getName(), constructor);
  }

  public AtomConstructor getAssociatedType() {
    return associatedType;
  }

  /**
   * Looks up a constructor in the module scope.
   *
   * @param name the name of the module binding
   * @return the Atom constructor associated with {@code name}, or {@link Optional#empty()}
   */
  public Optional<AtomConstructor> getConstructor(String name) {
    if (associatedType.getName().equals(name)) {
      return Optional.of(associatedType);
    }
    Optional<AtomConstructor> locallyDefined = Optional.ofNullable(this.constructors.get(name));
    if (locallyDefined.isPresent()) return locallyDefined;
    return imports.stream()
        .map(scope -> scope.getConstructor(name))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .findFirst();
  }

  /**
   * Returns a map of methods defined in this module for a given constructor.
   *
   * @param cons the constructor for which method map is requested
   * @return a map containing all the defined methods by name
   */
  private Map<String, Function> getMethodMapFor(AtomConstructor cons) {
    return methods.computeIfAbsent(cons, k -> new HashMap<>());
  }

  /**
   * Registers a method defined for a given type.
   *
   * @param atom type the method was defined for
   * @param method method name
   * @param function the {@link Function} associated with this definition
   */
  public void registerMethod(AtomConstructor atom, String method, Function function) {
    getMethodMapFor(atom).put(method, function);
  }

  /**
   * Registers a method for the {@code Any} type.
   *
   * @param methodName the name of the method to register
   * @param function the {@link Function} associated with this definition
   */
  public void registerMethodForAny(String methodName, Function function) {
    anyMethods.put(methodName, function);
  }

  /**
   * Registers a method for the {@code Number} type.
   *
   * @param methodName the name of the method to register
   * @param function the {@link Function} associated with this definition
   */
  public void registerMethodForNumber(String methodName, Function function) {
    numberMethods.put(methodName, function);
  }

  /**
   * Registers a method for the {@link Function} type.
   *
   * @param methodName the name of the method to register
   * @param function the {@link Function} associated with this definition
   */
  public void registerMethodForFunction(String methodName, Function function) {
    functionMethods.put(methodName, function);
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
  public Function lookupMethodDefinitionForAtom(AtomConstructor atom, String name) {
    return lookupSpecificMethodDefinitionForAtom(atom, name)
        .orElseGet(() -> lookupMethodDefinitionForAny(name).orElse(null));
  }

  /**
   * Looks up a method definition by-name, for methods defined on the type Any.
   *
   * <p>The resolution algorithm prefers methods defined locally over any other method. The
   * definitions are imported into scope transitively.
   *
   * @param name the name of the method to look up
   * @return {@code Optional.of(resultMethod)} if the method existed, {@code Optional.empty()}
   *     otherwise
   */
  @CompilerDirectives.TruffleBoundary
  public Optional<Function> lookupMethodDefinitionForAny(String name) {
    return searchAuxiliaryMethodsMap(ModuleScope::getMethodsOfAny, name);
  }

  private Optional<Function> lookupSpecificMethodDefinitionForAtom(
      AtomConstructor atom, String name) {
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

  private Optional<Function> lookupSpecificMethodDefinitionForNumber(String name) {
    return searchAuxiliaryMethodsMap(ModuleScope::getMethodsOfNumber, name);
  }

  private Optional<Function> lookupSpecificMethodDefinitionForFunction(String name) {
    return searchAuxiliaryMethodsMap(ModuleScope::getMethodsOfFunction, name);
  }

  /**
   * Looks up a method definition by-name, for methods defined on the type Number.
   *
   * <p>The resolution algorithm prefers methods defined locally over any other method. The
   * definitions are imported into scope transitively.
   *
   * <p>If the specific search fails, methods defined for any type are searched, first looking at *
   * locally defined methods and then all the transitive imports.
   *
   * @param name the name of the method to look up
   * @return {@code Optional.of(resultMethod)} if the method existed, {@code Optional.empty()}
   *     otherwise
   */
  @CompilerDirectives.TruffleBoundary
  public Optional<Function> lookupMethodDefinitionForNumber(String name) {
    return Optional.ofNullable(
        lookupSpecificMethodDefinitionForNumber(name)
            .orElseGet(() -> lookupMethodDefinitionForAny(name).orElse(null)));
  }

  /**
   * Looks up a method definition by-name, for methods defined on the type {@link Function}.
   *
   * <p>The resolution algorithm prefers methods defined locally over any other method. The
   * definitions are imported into scope transitively.
   *
   * <p>If the specific search fails, methods defined for any type are searched, first looking at *
   * locally defined methods and then all the transitive imports.
   *
   * @param name the name of the method to look up
   * @return {@code Optional.of(resultMethod)} if the method existed, {@code Optional.empty()}
   *     otherwise
   */
  @CompilerDirectives.TruffleBoundary
  public Optional<Function> lookupMethodDefinitionForFunction(String name) {
    return Optional.ofNullable(
        lookupSpecificMethodDefinitionForFunction(name)
            .orElseGet(() -> lookupMethodDefinitionForAny(name).orElse(null)));
  }

  private Optional<Function> searchAuxiliaryMethodsMap(
      java.util.function.Function<ModuleScope, Map<String, Function>> mapGetter,
      String methodName) {
    Function definedHere = mapGetter.apply(this).get(methodName);
    if (definedHere != null) {
      return Optional.of(definedHere);
    }
    return transitiveImports.stream()
        .map(scope -> mapGetter.apply(scope).get(methodName))
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

  private Map<String, Function> getMethodsOfAny() {
    return anyMethods;
  }

  private Map<String, Function> getMethodsOfNumber() {
    return numberMethods;
  }

  private Map<String, Function> getMethodsOfFunction() {
    return functionMethods;
  }

  /**
   * Adds a dependency for this module.
   *
   * @param scope the scope of the newly added dependency
   */
  public void addImport(ModuleScope scope) {
    imports.add(scope);
    transitiveImports.add(scope);
    transitiveImports.addAll(scope.getTransitiveImports());
  }

  private static final String ASSOCIATED_CONSTRUCTOR_KEY = "associated_constructor";
  private static final String METHODS_KEY = "get_method";
  private static final String CONSTRUCTORS_KEY = "get_constructor";

  @ExportMessage
  public Object invokeMember(String member, Object... arguments) throws UnknownIdentifierException {
    if (member.equals(METHODS_KEY)) {
      AtomConstructor c = (AtomConstructor) arguments[0];
      String name = (String) arguments[1];
      return methods.get(c).get(name);
    } else if (member.equals(CONSTRUCTORS_KEY)) {
      String name = (String) arguments[0];
      return constructors.get(name);
    } else {
      throw UnknownIdentifierException.create(member);
    }
  }

  @ExportMessage
  public Object readMember(String member) throws UnknownIdentifierException {
    if (member.equals(ASSOCIATED_CONSTRUCTOR_KEY)) {
      return associatedType;
    }
    throw UnknownIdentifierException.create(member);
  }

  @ExportMessage
  public boolean hasMembers() {
    return true;
  }

  @ExportMessage
  public boolean isMemberInvocable(String member) {
    return member.equals(METHODS_KEY) || member.equals(CONSTRUCTORS_KEY);
  }

  @ExportMessage
  public Object getMembers(boolean includeInternal) {
    return new Vector(new Object[] {METHODS_KEY, CONSTRUCTORS_KEY, ASSOCIATED_CONSTRUCTOR_KEY});
  }

  @ExportMessage
  public boolean isMemberReadable(String member) {
    return member.equals(ASSOCIATED_CONSTRUCTOR_KEY);
  }
}
