package org.enso.searcher.sql

import org.enso.searcher.Suggestion
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import slick.jdbc.SQLiteProfile.api._

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class SuggestionsRepoTest
    extends AnyWordSpec
    with Matchers
    with BeforeAndAfterAll {

  val Timeout: FiniteDuration = 3.seconds

  val db   = Database.forConfig("searcher.db")
  val repo = new SuggestionsRepo()

  override def beforeAll(): Unit = {
    Await.ready(
      db.run((suggestions.schema ++ arguments.schema).createIfNotExists),
      Timeout
    )
  }

  "SuggestionsDBIO" should {

    "select" in {
      val action =
        for {
          id  <- db.run(repo.insert(stub.atom))
          res <- db.run(repo.select(id))
        } yield res

      Await.result(action, Timeout) shouldEqual Some(stub.atom)
    }

    "findBy returnType" in {
      val action =
        for {
          _   <- db.run(repo.insert(stub.local))
          res <- db.run(repo.findBy(stub.local.returnType))
        } yield res

      Await.result(action, Timeout) shouldEqual Seq(stub.local)
    }
  }

  object stub {

    val atom: Suggestion.Atom =
      Suggestion.Atom(
        name = "Foo",
        arguments = Seq(
          Suggestion.Argument("a", "Any", false, false, None)
        ),
        returnType    = "Number",
        documentation = Some("Awesome")
      )

    val function: Suggestion.Method =
      Suggestion.Method(
        name          = "main",
        arguments     = Seq(),
        selfType      = "Main",
        returnType    = "IO",
        documentation = None
      )

    val local: Suggestion.Local =
      Suggestion.Local(
        name       = "bazz",
        returnType = "MyType",
        scope      = Suggestion.Scope(37, 84)
      )
  }
}
