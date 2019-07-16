package org.enso.flexer

import State.StateDesc
import Utils._

import scala.collection.mutable
import scala.reflect.runtime.universe._

case class CodeGen(dfa: DFA) {

  val offsets = mutable.Map(0 -> 0)

  case class Branch(range: Range, code: Tree)

  def genBranch(trgState: Int, st: Option[StateDesc], offset: Int): Tree = {
    (trgState, st, offset) match {
      case (-1, None, _)        => q"-2"
      case (-1, Some(state), 0) => q"{..${state.code}; -1}"
      case (-1, Some(state), o) => q"""{
          offset = offset - ${Literal(Constant(o))}
          currentChar = buffer(offset) 
          ..${state.code}
          -1
        }"""

      case (targetState, Some(state), _) =>
        if (!dfa.endStatePriorityMap.contains(targetState)) {
          dfa.endStatePriorityMap += targetState -> state
          offsets += targetState -> (offset + 1)
        }
        q"${Literal(Constant(targetState))}"

      case (targetState, _, _) =>
        q"${Literal(Constant(targetState))}"
    }
  }

  def generateStateMatch(): Tree = {
    val branches = dfa.links.indices.toList.map { stateIx =>
      val offset   = offsets.getOrElse(stateIx, 0)
      val state    = dfa.endStatePriorityMap.get(stateIx)
      var trgState = dfa.links(stateIx)(0)
      var rStart   = Int.MinValue
      val allBranches = for {
        (range, vocIx) <- dfa.vocabulary.toIterator
        newTrgState = dfa.links(stateIx)(vocIx)
        rEnd        = range.start - 1
        if newTrgState != trgState
      } yield Branch(rStart to rEnd, genBranch(trgState, state, offset))
        .thenDo {
          trgState = newTrgState
          rStart   = range.start
        }

      val code =
        allBranches.foldRight(q"${genBranch(trgState, state, offset)}")(
          (a, b) => q"if (codePoint <= ${a.range.end}) ${a.code} else $b"
        )

      cq"$stateIx => $code"
    }
    Match(q"state", branches)
  }

  def generate(i: Int): Tree = {
    q"""
      def ${TermName(s"runGroup$i")}(): Int = {
        var state: Int = 0
        matchBuilder.setLength(0)
        while(state >= 0) {
          codePoint = currentChar.toInt
          state = ${generateStateMatch()}
          if(state >= 0) {
            matchBuilder.append(currentChar)
            currentChar = getNextChar
          }
        }
        state
      }
    """
  }
}
