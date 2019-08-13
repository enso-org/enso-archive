package org.enso.interpreter.node.callable;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import org.enso.interpreter.Language;
import org.enso.interpreter.runtime.Context;
import org.enso.interpreter.runtime.callable.DynamicSymbol;
import org.enso.interpreter.runtime.callable.atom.Atom;
import org.enso.interpreter.runtime.callable.atom.AtomConstructor;
import org.enso.interpreter.runtime.callable.function.Function;
import org.enso.interpreter.runtime.error.NoMethodErrorException;

/**
 * A node performing lookups of method definitions. Uses a polymorphic inline cache to ensure best
 * performance.
 */
public abstract class MethodResolverNode extends Node {

  /**
   * DSL method to generate the actual cached code. Uses Cached arguments and performs all the logic
   * through the DSL. Not for manual use.
   */
  @Specialization(guards = "isValidCache(symbol, cachedName, atom, cachedConstructor)")
  public Function resolveCached(
      DynamicSymbol symbol,
      Atom atom,
      @CachedContext(Language.class) TruffleLanguage.ContextReference<Context> contextRef,
      @Cached("symbol.getName()") String cachedName,
      @Cached("atom.getConstructor()") AtomConstructor cachedConstructor,
      @Cached("resolveMethod(contextRef, cachedConstructor, cachedName)") Function function) {
    return function;
  }

  /**
   * Entry point for this node.
   *
   * @param symbol Method name to resolve.
   * @param atom Object for which to resolve the method.
   * @return Resolved method.
   */
  public abstract Function execute(DynamicSymbol symbol, Atom atom);

  /**
   * Handles the actual method lookup. Not for manual use.
   *
   * @param contextReference Reference for the current language context.
   * @param cons Type for which to resolve the method.
   * @param name Name of the method.
   * @return Resolved method definition.
   */
  public Function resolveMethod(
      TruffleLanguage.ContextReference<Context> contextReference,
      AtomConstructor cons,
      String name) {
    Function result = contextReference.get().getGlobalScope().lookupMethodDefinition(cons, name);
    if (result == null) {
      throw new NoMethodErrorException(cons, name, this);
    }
    return result;
  }

  /**
   * Checks the cache validity. For use by the DSL. The cache entry is valid if it's resolved for
   * the same method name and this argument type. Not for manual use.
   */
  public boolean isValidCache(
      DynamicSymbol symbol, String cachedName, Atom atom, AtomConstructor cachedConstructor) {
    // This comparison by `==` is safe, because all the symbol names are interned.
    //noinspection StringEquality
    return (symbol.getName() == cachedName) && (atom.getConstructor() == cachedConstructor);
  }
}
