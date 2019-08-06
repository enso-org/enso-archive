package org.enso.interpreter.builder;

import org.enso.interpreter.AstCallArgVisitor;
import org.enso.interpreter.AstExpression;
import org.enso.interpreter.Language;
import org.enso.interpreter.runtime.callable.argument.CallArgument;
import org.enso.interpreter.runtime.scope.GlobalScope;
import org.enso.interpreter.runtime.scope.LocalScope;

public class CallArgFactory implements AstCallArgVisitor<CallArgument> {
  private final LocalScope scope;
  private final Language language;
  private final String scopeName;
  private final GlobalScope globalScope;

  public CallArgFactory(LocalScope scope, Language language, String scopeName,
      GlobalScope globalScope) {
    this.scope = scope;
    this.language = language;
    this.scopeName = scopeName;
    this.globalScope = globalScope;
  }

  @Override
  public CallArgument visitIgnore(String name, int position) {
    return new CallArgument(name);
  }

  @Override
  public CallArgument visitNamedCallArg(String name, AstExpression value, int position) {
    ExpressionFactory factory = new ExpressionFactory(language, scope, scopeName, globalScope);
    return new CallArgument(name, value.visit(factory));
  }

  @Override
  public CallArgument visitUnnamedCallArg(AstExpression value, int position) {
    ExpressionFactory factory = new ExpressionFactory(language, scope, scopeName, globalScope);
    return new CallArgument(value.visit(factory));
  }
}
