package org.enso.interpreter.builder;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import java.util.List;

import org.enso.interpreter.*;
import org.enso.interpreter.node.EnsoRootNode;
import org.enso.interpreter.node.ExpressionNode;
import org.enso.interpreter.node.callable.function.CreateFunctionNode;
import org.enso.interpreter.runtime.callable.argument.ArgumentDefinition;
import org.enso.interpreter.runtime.callable.atom.AtomConstructor;
import org.enso.interpreter.runtime.callable.function.Function;
import org.enso.interpreter.runtime.error.VariableDoesNotExistException;
import org.enso.interpreter.runtime.scope.GlobalScope;

/**
 * A {@code GlobalScopeExpressionFactory} is responsible for converting the top-level definitions of
 * an Enso program into AST nodes for the interpreter to evaluate.
 */
public class GlobalScopeExpressionFactory implements AstGlobalScopeVisitor<ExpressionNode> {
  private final Language language;

  /**
   * Creates a factory for the given language.
   *
   * @param language the name of the language for which this factory is creating nodes
   */
  public GlobalScopeExpressionFactory(Language language) {
    this.language = language;
  }

  /**
   * Executes the factory on a global expression.
   *
   * @param expr the expression to execute on
   * @return a runtime node representing the top-level expression
   */
  public ExpressionNode run(AstGlobalScope expr) {
    return expr.visit(this);
  }

  /**
   * Processes definitions in the language global scope.
   *
   * @param typeDefs any type definitions defined in the global scope
   * @param bindings any bindings made in the global scope
   * @param executableExpression the executable expression for the program
   * @return a runtime node representing the whole top-level program scope
   */
  @Override
  public ExpressionNode visitGlobalScope(
      List<AstTypeDef> typeDefs, List<AstMethodDef> bindings, AstExpression executableExpression) {
    GlobalScope globalScope = language.getCurrentContext().getGlobalScope();

    //    bindings.forEach(binding -> globalScope.registerName(binding.name()));

    for (AstTypeDef type : typeDefs) {
      ArgDefinitionFactory argFactory = new ArgDefinitionFactory(language, globalScope);
      ArgumentDefinition[] argDefs = new ArgumentDefinition[type.getArguments().size()];

      for (int i = 0; i < type.getArguments().size(); ++i) {
        argDefs[i] = type.getArguments().get(i).visit(argFactory, i);
      }

      globalScope.registerConstructor(new AtomConstructor(type.name(), argDefs));
    }

    for (AstMethodDef method : bindings) {
      AtomConstructor constructor =
          globalScope
              .getConstructor(method.typeName())
              .orElseThrow(() -> new VariableDoesNotExistException(method.typeName()));
      ExpressionFactory expressionFactory =
          new ExpressionFactory(
              language, method.typeName() + "." + method.methodName(), globalScope);
      CreateFunctionNode funNode =
          expressionFactory.processFunctionBody(
              method.fun().getArguments(), method.fun().getStatements(), method.fun().ret());
      funNode.markTail();
      Function function = new Function(funNode.getCallTarget(), null, funNode.getArgs());
      globalScope.registerMethod(constructor, method.methodName(), function);
    }

    //    for (AstAssignment binding : bindings) {
    //      String name = binding.name();
    //      AstExpression body = binding.body();
    //
    //      ExpressionFactory exprFactory = new ExpressionFactory(language, name, globalScope);
    //      ExpressionNode node = exprFactory.run(body);
    //
    //      EnsoRootNode root = new EnsoRootNode(this.language, new FrameDescriptor(), node, null,
    // name);
    //      RootCallTarget target = Truffle.getRuntime().createCallTarget(root);
    //
    //      globalScope.updateCallTarget(name, target);
    //    }

    ExpressionFactory factory = new ExpressionFactory(this.language, globalScope);
    return factory.run(executableExpression);
  }
}
