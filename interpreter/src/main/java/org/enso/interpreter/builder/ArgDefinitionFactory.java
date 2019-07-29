package org.enso.interpreter.builder;

import org.enso.interpreter.AstArgDefinitionVisitor;
import org.enso.interpreter.AstExpression;
import org.enso.interpreter.Language;
import org.enso.interpreter.node.function.argument.ArgumentDefinition;

public class ArgDefinitionFactory implements AstArgDefinitionVisitor<ArgumentDefinition> {

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

  public ArgDefinitionFactory(Language language, String scopeName, GlobalScope globalScope) {
    this(new LocalScope(), language, scopeName, globalScope);
  }

  public ArgDefinitionFactory(Language language, GlobalScope globalScope) {
    this(language, "<root>", globalScope);
  }

  @Override
  public ArgumentDefinition visitBareArg(String name, int position) {
    return new ArgumentDefinition(position, name);
  }

  @Override
  public ArgumentDefinition visitDefaultedArg(String name, AstExpression value, int position) {
    ExpressionFactory exprFactory = new ExpressionFactory(language, scope, scopeName, globalScope);
    return new ArgumentDefinition(position, name, value.visit(exprFactory));
  }
}
