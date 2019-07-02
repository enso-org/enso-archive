package org.enso.interpreter;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.ImplicitCast;
import com.oracle.truffle.api.dsl.TypeSystem;
import org.enso.interpreter.runtime.Function;

@TypeSystem({long.class, Function.class})
public class Types {

  @ImplicitCast
  @CompilerDirectives.TruffleBoundary
  public static long castLong(int value) {
    return value;
  }
}
