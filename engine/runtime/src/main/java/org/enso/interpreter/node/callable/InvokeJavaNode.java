package org.enso.interpreter.node.callable;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.interop.*;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import org.enso.interpreter.Language;
import org.enso.interpreter.node.callable.thunk.ThunkExecutorNode;
import org.enso.interpreter.runtime.Context;
import org.enso.interpreter.runtime.callable.UnresolvedSymbol;
import org.enso.interpreter.runtime.error.TypeError;
import org.enso.interpreter.runtime.interop.JavaObject;
import org.enso.interpreter.runtime.state.Stateful;
import org.enso.interpreter.runtime.type.TypesGen;

public class InvokeJavaNode extends Node {
  private final int numArgs;
  private final int thisPosition;

  private @Children ThunkExecutorNode[] executors;
  private @Child InteropLibrary library = InteropLibrary.getFactory().createDispatched(10);

  public InvokeJavaNode(int numArgs, int thisPosition) {
    this.numArgs = numArgs;
    this.thisPosition = thisPosition;
  }

  private void initArgumentExecutors(Object[] arguments) {
    CompilerDirectives.transferToInterpreterAndInvalidate();
    executors = new ThunkExecutorNode[numArgs];
    for (int i = 0; i < numArgs; i++) {
      if (TypesGen.isThunk(arguments[i])) {
        executors[i] = insert(ThunkExecutorNode.build(false));
      }
    }
  }

  @ExplodeLoop
  private Object executeArguments(Object[] arguments, Object state) {
    if (executors == null) {
      initArgumentExecutors(arguments);
    }
    for (int i = 0; i < numArgs; i++) {
      if (executors[i] != null) {
        Stateful result = executors[i].executeThunk(TypesGen.asThunk(arguments[i]), state);
        arguments[i] = result.getValue();
        state = result.getState();
      }
    }
    return state;
  }

  public Stateful execute(
      JavaObject target, UnresolvedSymbol symbol, Object[] argumentsX, Object state) {
    Object object = target.getObj();
    Object resultState = executeArguments(argumentsX, state);
    Object[] arguments = new Object[numArgs - 1];
    int j = 0;
    for (int i = 0; i < numArgs; i++) {
      if (i == thisPosition) continue;
      arguments[j] = argumentsX[i];
      j++;
    }
    try {
      Object mems = library.getMembers(object);
      long len = library.getArraySize(mems);
      System.out.println("MEMBERS:");
      for (int i = 0; i < len; i++) {
        System.out.println(library.readArrayElement(mems, i));
      }

      Object methodMember = library.readMember(object, symbol.getName());
      System.out.println("METHOD MEMBER DETAILS:");
      Context ctx = lookupContextReference(Language.class).get();
//      System.out.println(lookupLanguageReference(Language.class).get().findMetaObject());

      Object result;
      if (symbol.getName().equals("new")) {
        result = new JavaObject(library.instantiate(object, arguments));
      } else {
        result = library.invokeMember(object, symbol.getName(), arguments);
      }
      return new Stateful(resultState, result);

    } catch (UnsupportedMessageException
        | ArityException
        | UnknownIdentifierException
        | UnsupportedTypeException
        | InvalidArrayIndexException e) {
      throw new TypeError("Can't call java method.", this);
    }
  }
}
