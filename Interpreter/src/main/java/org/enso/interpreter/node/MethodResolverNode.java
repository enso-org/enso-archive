package org.enso.interpreter.node;

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

public abstract class MethodResolverNode extends Node {
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

  public abstract Function execute(DynamicSymbol symbol, Atom atom);

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

  public boolean isValidCache(
      DynamicSymbol symbol, String cachedName, Atom atom, AtomConstructor cachedConstructor) {
    // This comparison by `==` is safe, because all the symbol names are interned.
    //noinspection StringEquality
    return (symbol.getName() == cachedName) && (atom.getConstructor() == cachedConstructor);
  }
}
