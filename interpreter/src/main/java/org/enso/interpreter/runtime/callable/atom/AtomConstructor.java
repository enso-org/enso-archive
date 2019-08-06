package org.enso.interpreter.runtime.callable.atom;

import com.oracle.truffle.api.interop.TruffleObject;
import org.enso.interpreter.runtime.callable.Callable;
import org.enso.interpreter.runtime.callable.argument.ArgumentDefinition;
import org.enso.interpreter.runtime.error.ArityException;

public class AtomConstructor extends Callable implements TruffleObject {
  public static final AtomConstructor CONS =
      new AtomConstructor(
          "Cons",
          new ArgumentDefinition[] {
            new ArgumentDefinition(0, "head"), new ArgumentDefinition(1, "rest")
          });
  public static final AtomConstructor NIL = new AtomConstructor("Nil", new ArgumentDefinition[0]);
  public static final AtomConstructor UNIT = new AtomConstructor("Unit", new ArgumentDefinition[0]);

  private final String name;
  private final Atom cachedInstance;

  public AtomConstructor(String name, ArgumentDefinition[] args) {
    super(args);
    this.name = name;
    if (args.length == 0) {
      cachedInstance = new Atom(this);
    } else {
      cachedInstance = null;
    }
  }

  public String getName() {
    return name;
  }

  public int getArity() {
    return getArgs().length;
  }

  public Atom newInstance(Object... arguments) {
    if (arguments.length > getArity()) {
      throw new ArityException(getArity(), arguments.length);
    }
    if (cachedInstance != null) return cachedInstance;
    return new Atom(this, arguments);
  }

  @Override
  public String toString() {
    return super.toString() + "<" + name + "/" + getArity() + ">";
  }
}
