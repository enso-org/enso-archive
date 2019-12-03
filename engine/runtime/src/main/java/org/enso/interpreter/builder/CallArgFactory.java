package org.enso.interpreter.builder;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import org.enso.compiler.core.AstCallArgVisitor;
import org.enso.compiler.core.AstDesuspend;
import org.enso.compiler.core.AstExpression;
import org.enso.interpreter.Language;
import org.enso.interpreter.node.ClosureRootNode;
import org.enso.interpreter.node.ExpressionNode;
import org.enso.interpreter.node.callable.thunk.CreateThunkNode;
import org.enso.interpreter.node.callable.thunk.ForceNode;
import org.enso.interpreter.runtime.callable.argument.CallArgument;
import org.enso.interpreter.runtime.scope.LocalScope;
import org.enso.interpreter.runtime.scope.ModuleScope;

import java.util.Optional;

/**
 * A {@code CallArgFactory} is responsible for converting arguments passed to a function call into
 * runtime nodes used by the interpreter to guide function evaluation.
 */
public class CallArgFactory implements AstCallArgVisitor<CallArgument> {

  private final LocalScope scope;
  private final Language language;
  private final String scopeName;
  private final ModuleScope moduleScope;

  /**
   * Explicitly specifies all constructor parameters.
   *
   * @param scope the language scope in which the arguments are called
   * @param language the name of the language for which the arguments are defined
   * @param scopeName the name of the scope in which the arguments are called
   * @param moduleScope the current language global scope
   */
  public CallArgFactory(
      LocalScope scope, Language language, String scopeName, ModuleScope moduleScope) {
    this.scope = scope;
    this.language = language;
    this.scopeName = scopeName;
    this.moduleScope = moduleScope;
  }

  /**
   * Processes an argument application.
   *
   * <p>Arguments can be applied by name, and named arguments can occur at any point in the
   * parameter list.
   *
   * @param name the name of the argument being applied
   * @param value the value of the argument being applied
   * @param position the position of this argument in the calling arguments list
   * @return a runtime representation of the argument
   */
  @Override
  public CallArgument visitCallArg(Optional<String> name, AstExpression value, int position) {

    String displayName = "callArgument<" + name.orElse(String.valueOf(position)) + ">";

    ExpressionNode result;

    if (value instanceof AstDesuspend) {
      ExpressionFactory factory = new ExpressionFactory(language, scope, scopeName, moduleScope);
      result = ((AstDesuspend) value).target().visit(factory);
    } else {
      LocalScope childScope = new LocalScope(scope);
      ExpressionFactory factory =
          new ExpressionFactory(language, childScope, scopeName, moduleScope);
      ExpressionNode expr = value.visit(factory);
      expr.markTail();
      RootCallTarget callTarget =
          Truffle.getRuntime()
              .createCallTarget(
                  new ClosureRootNode(language, childScope, moduleScope, expr, null, displayName));
      result = CreateThunkNode.build(callTarget);
    }

    return new CallArgument(name.orElse(null), result);
  }
}
