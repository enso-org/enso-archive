package org.enso.interpreter.builder;

import org.enso.interpreter.AstCallArgVisitor;
import org.enso.interpreter.AstExpression;
import org.enso.interpreter.Language;
import org.enso.interpreter.node.function.argument.CallArgumentNode;

public class CallArgFactory implements AstCallArgVisitor<CallArgumentNode> {
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
  public CallArgumentNode visitIgnore(String name, int position) {
    return new CallArgumentNode(name);
  }

  @Override
  public CallArgumentNode visitNamedCallArg(String name, AstExpression value, int position) {
    ExpressionFactory factory = new ExpressionFactory(language, scope, scopeName, globalScope);
    return new CallArgumentNode(name, value.visit(factory));
  }

  @Override
  public CallArgumentNode visitUnnamedCallArg(AstExpression value, int position) {
    ExpressionFactory factory = new ExpressionFactory(language, scope, scopeName, globalScope);
    return new CallArgumentNode(value.visit(factory));
  }
}
