package org.enso.interpreter.runtime;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.ImplicitCast;
import com.oracle.truffle.api.dsl.TypeSystem;
import org.enso.interpreter.runtime.function.Function;
import org.enso.interpreter.runtime.type.Atom;
import org.enso.interpreter.runtime.type.AtomConstructor;

@TypeSystem({long.class, Function.class, Atom.class, AtomConstructor.class, Callable.class})
public class Types {

  @ImplicitCast
  @CompilerDirectives.TruffleBoundary
  public static long castLong(int value) {
    return value;
  }

}
