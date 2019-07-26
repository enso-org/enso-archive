package org.enso.interpreter.builder;

import org.enso.interpreter.AstArgDefinitionVisitor;
import org.enso.interpreter.AstExpression;
import org.enso.interpreter.Language;
import org.enso.interpreter.node.function.argument.ArgumentDefinitionNode;

public class ArgDefinitionFactory implements AstArgDefinitionVisitor<ArgumentDefinitionNode> {

  private final LocalScope scope;
  private final Language language;
  private final String scopeName;
  private final GlobalScope globalScope;

  public ArgDefinitionFactory(
      LocalScope scope, Language language, String scopeName, GlobalScope globalScope) {
    this.scope = scope;
    this.language = language;
    this.scopeName = scopeName;
    this.globalScope = globalScope;
  }

  @Override
  public ArgumentDefinitionNode visitBareArg(String name, int position) {
    return new ArgumentDefinitionNode(position, name);
  }

  @Override
  public ArgumentDefinitionNode visitDefaultedArg(String name, AstExpression value, int position) {
    ExpressionFactory exprFactory = new ExpressionFactory(language, scope, scopeName, globalScope);
    return new ArgumentDefinitionNode(position, name, value.visit(exprFactory));
  }
}
