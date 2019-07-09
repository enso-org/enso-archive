package org.enso.interpreter.util;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import java.util.List;
import org.enso.interpreter.AstAssignment;
import org.enso.interpreter.AstExpression;
import org.enso.interpreter.AstGlobalScope;
import org.enso.interpreter.AstGlobalScopeVisitor;
import org.enso.interpreter.Language;
import org.enso.interpreter.node.EnsoRootNode;
import org.enso.interpreter.node.ExpressionNode;
import org.enso.interpreter.runtime.Context;

public class GlobalScopeExpressionFactory implements AstGlobalScopeVisitor<ExpressionNode> {

  public final Language language;

  public GlobalScopeExpressionFactory(Language language) {
    this.language = language;
  }

  public ExpressionNode run(AstGlobalScope expr) {
    return expr.visit(this);
  }

  @Override
  public ExpressionNode visitGlobalScope(List<AstAssignment> bindings, AstExpression expression) {
    Context ctx = language.getCurrentContext();

    bindings.stream().forEach(binding -> ctx.registerName(binding.name()));

    for (AstAssignment binding : bindings) {
      String name = binding.name();
      AstExpression body = binding.body();
      ExpressionFactory exprFactory = new ExpressionFactory(language, name);

      ExpressionNode node = exprFactory.run(body);

      EnsoRootNode root = new EnsoRootNode(this.language, new FrameDescriptor(), node, null, name);

      RootCallTarget target = Truffle.getRuntime().createCallTarget(root);

      ctx.updateCallTarget(name, target);
    }

    ExpressionFactory factory = new ExpressionFactory(this.language);

    return factory.run(expression);
  }
}
