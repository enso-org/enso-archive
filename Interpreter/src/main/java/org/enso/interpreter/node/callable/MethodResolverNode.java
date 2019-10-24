package org.enso.interpreter.node.callable;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import org.enso.interpreter.Language;
import org.enso.interpreter.runtime.Context;
import org.enso.interpreter.runtime.callable.UnresolvedSymbol;
import org.enso.interpreter.runtime.callable.atom.Atom;
import org.enso.interpreter.runtime.callable.atom.AtomConstructor;
import org.enso.interpreter.runtime.callable.function.Function;
import org.enso.interpreter.runtime.error.MethodDoesNotExistException;

/**
 * A node performing lookups of method definitions. Uses a polymorphic inline cache to ensure the
 * best performance.
 */
public abstract class MethodResolverNode extends Node {

  @Specialization(guards = "isValidAtomCache(symbol, cachedSymbol, atom, cachedConstructor)")
  protected Function resolveAtomCached(
      UnresolvedSymbol symbol,
      Atom atom,
      @CachedContext(Language.class) TruffleLanguage.ContextReference<Context> contextRef,
      @Cached("symbol") UnresolvedSymbol cachedSymbol,
      @Cached("atom.getConstructor()") AtomConstructor cachedConstructor,
      @Cached("resolveAtomMethod(cachedConstructor, cachedSymbol)") Function function) {
    return function;
  }

  @Specialization(guards = "cachedSymbol == symbol")
  protected Function resolveNumberCached(
      UnresolvedSymbol symbol,
      long self,
      @CachedContext(Language.class) TruffleLanguage.ContextReference<Context> contextReference,
      @Cached("symbol") UnresolvedSymbol cachedSymbol,
      @Cached("resolveNumberMethod(cachedSymbol)") Function function) {
    return function;
  }

  @Specialization(guards = "cachedSymbol == symbol")
  protected Function resolveFunctionCached(
      UnresolvedSymbol symbol,
      Function self,
      @CachedContext(Language.class) TruffleLanguage.ContextReference<Context> contextReference,
      @Cached("symbol") UnresolvedSymbol cachedSymbol,
      @Cached("resolveFunctionMethod(cachedSymbol)") Function function) {
    return function;
  }

  /**
   * Entry point for this node.
   *
   * @param symbol Method name to resolve.
   * @param self Object for which to resolve the method.
   * @return Resolved method.
   */
  public abstract Function execute(UnresolvedSymbol symbol, Object self);

  /**
   * Handles the actual method lookup. Not for manual use.
   *
   * @param cons Type for which to resolve the method.
   * @param symbol symbol representing the method to resolve
   * @return Resolved method definition.
   */
  public Function resolveAtomMethod(AtomConstructor cons, UnresolvedSymbol symbol) {
    Function result = symbol.resolveFor(cons);
    if (result == null) {
      throw new MethodDoesNotExistException(cons, symbol.getName(), this);
    }
    return result;
  }

  protected Function resolveNumberMethod(UnresolvedSymbol symbol) {
    Function result = symbol.resolveForNumber();
    if (result == null) {
      throw new MethodDoesNotExistException("Number", symbol.getName(), this);
    }
    return result;
  }

  protected Function resolveFunctionMethod(UnresolvedSymbol symbol) {
    Function result = symbol.resolveForFunction();
    if (result == null) {
      throw new MethodDoesNotExistException("Function", symbol.getName(), this);
    }
    return result;
  }

  /**
   * Checks the cache validity. For use by the DSL. The cache entry is valid if it's resolved for
   * the same method name and this argument type. Not for manual use.
   */
  public boolean isValidAtomCache(
      UnresolvedSymbol symbol,
      UnresolvedSymbol cachedSymbol,
      Atom atom,
      AtomConstructor cachedConstructor) {
    return (symbol == cachedSymbol) && (atom.getConstructor() == cachedConstructor);
  }
}
