package org.enso.interpreter.builder;

import org.enso.interpreter.AstCallArgVisitor;
import org.enso.interpreter.AstExpression;
import org.enso.interpreter.Language;
import org.enso.interpreter.node.function.argument.CallArgument;

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
//    return new CallArgument(name, position);
    throw new RuntimeException("Ignoring defaults is not yet implemented.");
  }

  @Override
  public CallArgument visitNamedCallArg(String name, AstExpression value, int position) {
    ExpressionFactory factory = new ExpressionFactory(language, scope, scopeName, globalScope);
    return new CallArgument(name, value.visit(factory), position);
  }

  @Override
  public CallArgument visitUnnamedCallArg(AstExpression value, int position) {
    ExpressionFactory factory = new ExpressionFactory(language, scope, scopeName, globalScope);
    return new CallArgument(value.visit(factory), position);
  }
}
