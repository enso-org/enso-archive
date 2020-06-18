package org.enso.searcher.sql

import org.enso.searcher.Suggestion
import slick.jdbc.SQLiteProfile.api._

import scala.concurrent.ExecutionContext

/** The object for accessing the suggestions database. */
final class SuggestionsRepo(implicit ec: ExecutionContext) {

  /** The query returning the arguments joined with the corresponding
    * suggestions. */
  private val joined: Query[
    (Rep[Option[ArgumentsTable]], SuggestionsTable),
    (Option[ArgumentRow], SuggestionRow),
    Seq
  ] =
    arguments
      .joinRight(suggestions)
      .on(_.suggestionId === _.id)

  /** Find suggestions by the return type.
    *
    * @param returnType the return type of a suggestion
    * @return the list of suggestions
    */
  def findBy(returnType: String): DBIO[Seq[Suggestion]] = {
    val q = for {
      (a, s) <- joined
      if s.returnType === returnType
    } yield (a, s)
    q.result.map(joinedToSuggestion)
  }

  /** Select the suggestion by id.
    *
    * @param id the id of a suggestion
    * @return return the suggestion
    */
  def select(id: Long): DBIO[Option[Suggestion]] = {
    val q = for {
      (a, s) <- joined
      if s.id === id
    } yield (a, s)
    q.result.map(coll => joinedToSuggestion(coll).headOption)
  }

  /** Insert the suggestion
    *
    * @param suggestion the suggestion to insert
    * @return the id of an inserted suggestion
    */
  def insert(suggestion: Suggestion): DBIO[Long] = {
    val (suggestionRow, args) = toSuggestionRow(suggestion)
    for {
      id <- suggestions.returning(suggestions.map(_.id)) += suggestionRow
      _  <- arguments ++= args.map(toArgumentRow(id, _))
    } yield id
  }

  private def joinedToSuggestion(
    coll: Seq[(Option[ArgumentRow], SuggestionRow)]
  ): Seq[Suggestion] = {
    coll
      .groupBy(_._2)
      .view
      .mapValues(_.flatMap(_._1))
      .map(Function.tupled(toSuggestion))
      .toSeq
  }

  private def toSuggestionRow(
    suggestion: Suggestion
  ): (SuggestionRow, Seq[Suggestion.Argument]) =
    suggestion match {
      case Suggestion.Atom(name, args, returnType, doc) =>
        val row = SuggestionRow(
          id            = None,
          kind          = SuggestionKind.ATOM,
          name          = name,
          selfType      = None,
          returnType    = returnType,
          documentation = doc,
          scopeStart    = None,
          scopeEnd      = None
        )
        row -> args
      case Suggestion.Method(name, args, selfType, returnType, doc) =>
        val row = SuggestionRow(
          id            = None,
          kind          = SuggestionKind.METHOD,
          name          = name,
          selfType      = Some(selfType),
          returnType    = returnType,
          documentation = doc,
          scopeStart    = None,
          scopeEnd      = None
        )
        row -> args
      case Suggestion.Function(name, args, returnType, scope) =>
        val row = SuggestionRow(
          id            = None,
          kind          = SuggestionKind.FUNCTION,
          name          = name,
          selfType      = None,
          returnType    = returnType,
          documentation = None,
          scopeStart    = Some(scope.start),
          scopeEnd      = Some(scope.end)
        )
        row -> args
      case Suggestion.Local(name, returnType, scope) =>
        val row = SuggestionRow(
          id            = None,
          kind          = SuggestionKind.LOCAL,
          name          = name,
          selfType      = None,
          returnType    = returnType,
          documentation = None,
          scopeStart    = Some(scope.start),
          scopeEnd      = Some(scope.end)
        )
        row -> Seq()
    }

  private def toArgumentRow(
    suggestionId: Long,
    arg: Suggestion.Argument
  ): ArgumentRow =
    ArgumentRow(
      id           = None,
      suggestionId = suggestionId,
      name         = arg.name,
      tpe          = arg.reprType,
      isSuspended  = arg.isSuspended,
      hasDefault   = arg.hasDefault,
      defaultValue = arg.defaultValue
    )

  private def toSuggestion(
    s: SuggestionRow,
    as: Seq[ArgumentRow]
  ): Suggestion =
    s.kind match {
      case SuggestionKind.ATOM =>
        Suggestion.Atom(
          name          = s.name,
          arguments     = as.map(toArgument),
          returnType    = s.returnType,
          documentation = s.documentation
        )
      case SuggestionKind.METHOD =>
        Suggestion.Method(
          name          = s.name,
          arguments     = as.map(toArgument),
          selfType      = s.selfType.get,
          returnType    = s.returnType,
          documentation = s.documentation
        )
      case SuggestionKind.FUNCTION =>
        Suggestion.Function(
          name       = s.name,
          arguments  = as.map(toArgument),
          returnType = s.returnType,
          scope      = Suggestion.Scope(s.scopeStart.get, s.scopeEnd.get)
        )
      case SuggestionKind.LOCAL =>
        Suggestion.Local(
          name       = s.name,
          returnType = s.returnType,
          scope      = Suggestion.Scope(s.scopeStart.get, s.scopeEnd.get)
        )

      case k =>
        throw new NoSuchElementException(s"Unknown suggestion kind: $k")
    }

  private def toArgument(a: ArgumentRow): Suggestion.Argument =
    Suggestion.Argument(
      name         = a.name,
      reprType     = a.tpe,
      isSuspended  = a.isSuspended,
      hasDefault   = a.hasDefault,
      defaultValue = a.defaultValue
    )
}
