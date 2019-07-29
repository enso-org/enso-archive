package org.enso.interpreter.runtime;

import com.oracle.truffle.api.interop.TruffleObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.enso.interpreter.node.function.argument.ArgumentDefinition;
import org.enso.interpreter.runtime.errors.ArityException;

public class AtomConstructor extends Callable implements TruffleObject {
  public static final AtomConstructor CONS =
      new AtomConstructor(
          "Cons",
          2,
          new ArrayList<>(
              Arrays.asList(new ArgumentDefinition(0, "head"), new ArgumentDefinition(1, "rest"))));
  public static final AtomConstructor NIL = new AtomConstructor("Nil", 0, new ArrayList<>());
  public static final AtomConstructor UNIT = new AtomConstructor("Unit", 0, new ArrayList<>());

  private final String name;
  private final int arity;
  private final Atom cachedInstance;

  public AtomConstructor(String name, List<ArgumentDefinition> args) {
    this(name, args.size(), args);
  }

  public AtomConstructor(String name, int arity, List<ArgumentDefinition> args) {
    super(args);
    this.name = name;
    this.arity = arity;
    if (arity == 0) {
      cachedInstance = new Atom(this);
    } else {
      cachedInstance = null;
    }
  }

  public String getName() {
    return name;
  }

  public int getArity() {
    return arity;
  }

  // TODO [AA] Make defaulted arguments actually work with constructors
  public Atom newInstance(Object... arguments) {
    if (arguments.length != arity) throw new ArityException(arity, arguments.length);
    if (cachedInstance != null) return cachedInstance;
    return new Atom(this, arguments);
  }

  @Override
  public String toString() {
    return super.toString() + "<" + name + "/" + arity + ">";
  }
}
