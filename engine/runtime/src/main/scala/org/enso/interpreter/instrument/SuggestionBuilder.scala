package org.enso.interpreter.instrument

import org.enso.compiler.core.IR
import org.enso.compiler.pass.resolve.DocumentationComments
import org.enso.searcher.Suggestion

import scala.collection.immutable.VectorBuilder
import scala.collection.mutable

/** Module that extracts [[Suggestion]] entries from the [[IR]]. */
final class SuggestionBuilder {

  import SuggestionBuilder._

  /** Build suggestions from the given `ir`.
    *
    * @param ir the input `IR`
    * @return the list of suggestion entries extracted from the given `IR`
    */
  def build(ir: IR.Module): Vector[Suggestion] = {
    @scala.annotation.tailrec
    def go(
      scope: Scope,
      scopes: mutable.Queue[Scope],
      acc: mutable.Builder[Suggestion, Vector[Suggestion]]
    ): Vector[Suggestion] =
      if (scope.queue.isEmpty) {
        if (scopes.isEmpty) {
          acc.result()
        } else {
          val scope = scopes.dequeue()
          go(scope, scopes, acc)
        }
      } else {
        val ir  = scope.queue.dequeue()
        val doc = ir.getMetadata(DocumentationComments).map(_.documentation)
        ir match {
          case IR.Module.Scope.Definition.Method
                .Explicit(
                IR.Name.MethodReference(typePtr, methodName, _, _, _),
                IR.Function.Lambda(args, body, _, _, _, _),
                _,
                _,
                _
                ) =>
            acc += buildMethod(methodName, typePtr, args, doc)
            scopes += Scope(body.children)
            go(scope, scopes, acc)
          case IR.Expression.Binding(name, expr, _, _, _)
              if name.location.isDefined =>
            acc += buildLocal(name.name)
            scopes += Scope(expr.children)
            go(scope, scopes, acc)
          case IR.Module.Scope.Definition.Atom(name, arguments, _, _, _) =>
            acc += buildAtom(name.name, arguments, doc)
            go(scope, scopes, acc)
          case _ =>
            go(scope, scopes, acc)
        }
      }

    go(
      Scope(ir.children),
      mutable.Queue(),
      new VectorBuilder()
    )
  }

  private def buildMethod(
    name: IR.Name,
    typeRef: Seq[IR.Name],
    args: Seq[IR.DefinitionArgument],
    doc: Option[String]
  ): Suggestion.Method =
    Suggestion.Method(
      name          = name.name,
      arguments     = args.map(buildArgument),
      selfType      = buildSelfType(typeRef),
      returnType    = Any,
      documentation = doc
    )

  private def buildLocal(name: String): Suggestion.Local =
    Suggestion.Local(name, Any)

  private def buildAtom(
    name: String,
    arguments: Seq[IR.DefinitionArgument],
    doc: Option[String]
  ): Suggestion.Atom =
    Suggestion.Atom(
      name          = name,
      arguments     = arguments.map(buildArgument),
      returnType    = name,
      documentation = doc
    )

  private def buildArgument(arg: IR.DefinitionArgument): Suggestion.Argument =
    Suggestion.Argument(
      name         = arg.name.name,
      reprType     = Any,
      isSuspended  = arg.suspended,
      hasDefault   = arg.defaultValue.isDefined,
      defaultValue = arg.defaultValue.flatMap(buildDefaultValue)
    )

  private def buildSelfType(ref: Seq[IR.Name]): String =
    ref.map(_.name).mkString(".")

  private def buildDefaultValue(expr: IR): Option[String] =
    expr match {
      case IR.Literal.Number(value, _, _, _) => Some(value)
      case IR.Literal.Text(text, _, _, _)    => Some(text)
      case _                                 => None
    }
}

object SuggestionBuilder {

  /** A single level of an `IR`. */
  private case class Scope(queue: mutable.Queue[IR])

  private object Scope {

    /** Create new scope from the list of items. */
    def apply(items: Seq[IR]): Scope =
      new Scope(mutable.Queue(items: _*))
  }

  private val Any: String = "Any"

}
