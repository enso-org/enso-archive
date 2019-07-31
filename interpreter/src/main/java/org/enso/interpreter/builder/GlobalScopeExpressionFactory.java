package org.enso.interpreter.builder;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import java.util.ArrayList;
import java.util.List;
import org.enso.interpreter.AstAssignment;
import org.enso.interpreter.AstExpression;
import org.enso.interpreter.AstGlobalScope;
import org.enso.interpreter.AstGlobalScopeVisitor;
import org.enso.interpreter.AstTypeDef;
import org.enso.interpreter.Language;
import org.enso.interpreter.node.EnsoRootNode;
import org.enso.interpreter.node.ExpressionNode;
import org.enso.interpreter.node.function.argument.ArgumentDefinition;
import org.enso.interpreter.runtime.AtomConstructor;

public class GlobalScopeExpressionFactory implements AstGlobalScopeVisitor<ExpressionNode> {

  private final Language language;

  public GlobalScopeExpressionFactory(Language language) {
    this.language = language;
  }

  public ExpressionNode run(AstGlobalScope expr) {
    return expr.visit(this);
  }

  @Override
  public ExpressionNode visitGlobalScope(
      List<AstTypeDef> typeDefs, List<AstAssignment> bindings, AstExpression expression) {
    GlobalScope globalScope = new GlobalScope();

    bindings.forEach(binding -> globalScope.registerName(binding.name()));

    for (AstTypeDef type : typeDefs) {
      ArgDefinitionFactory argFactory = new ArgDefinitionFactory(language, globalScope);
      ArgumentDefinition[] argDefs = new ArgumentDefinition[type.getArguments().size()];

      for (int i = 0; i < type.getArguments().size(); ++i) {
        argDefs[i] = type.getArguments().get(i).visit(argFactory, i);
      }

      globalScope.registerConstructor(new AtomConstructor(type.name(), argDefs));
    }

    for (AstAssignment binding : bindings) {
      String name = binding.name();
      AstExpression body = binding.body();

      ExpressionFactory exprFactory = new ExpressionFactory(language, name, globalScope);
      ExpressionNode node = exprFactory.run(body);

      EnsoRootNode root = new EnsoRootNode(this.language, new FrameDescriptor(), node, null, name);
      RootCallTarget target = Truffle.getRuntime().createCallTarget(root);

      globalScope.updateCallTarget(name, target);
    }

    ExpressionFactory factory = new ExpressionFactory(this.language, globalScope);
    return factory.run(expression);
  }
}
