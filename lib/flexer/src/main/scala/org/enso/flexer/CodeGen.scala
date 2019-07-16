package org.enso.flexer

import State.StateDesc
import Utils._

import scala.collection.immutable.Range
import scala.collection.mutable
import scala.reflect.runtime.universe._

case class CodeGen(dfa: DFA) {

  val offsets = mutable.Map(0 -> 0)

  case class Branch(range: Range, body: Tree)

  def genBranchBody(trgState: Int, st: Option[StateDesc], offset: Int): Tree = {
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

  def genSwitch(branchs: Seq[Branch]): Seq[CaseDef] = {
    branchs.map {
      case Branch(range, body) =>
        val pattern =
          Alternative(range.map(i => q"${Literal(Constant(i))}").toList)
        cq"$pattern => $body"
    }
  }

  def genIf(branchs: Seq[Branch]): Branch = {
    branchs match {
      case b +: Seq() => b
      case a +: b +: rest =>
        val range = a.range.start to b.range.end
        val body  = q"if (codePoint <= ${a.range.end}) ${a.body} else ${b.body}"
        genIf(Branch(range, body) +: rest)
    }
  }

  def generateCaseBody(stateIx: Int): Tree = {
    val offset   = offsets.getOrElse(stateIx, 0)
    val state    = dfa.endStatePriorityMap.get(stateIx)
    var trgState = dfa.links(stateIx)(0)
    var rStart   = Int.MinValue
    val branches = for {
      (range, vocIx) <- dfa.vocabulary.toVector
      newTrgState = dfa.links(stateIx)(vocIx)
      rEnd        = range.start - 1
      if newTrgState != trgState
    } yield Branch(rStart to rEnd, genBranchBody(trgState, state, offset))
      .thenDo {
        trgState = newTrgState
        rStart   = range.start
      }
    val allBranches = branches :+
      Branch(rStart to Int.MaxValue, genBranchBody(trgState, state, offset))

    val (utf1 :+ b1, remBranches) = allBranches.span(_.range.start < 0)
    val (asci, utf2)              = remBranches.span(_.range.end < 256)

    utf2 match {
      case b2 +: utf2 =>
        val b11 = Branch(b1.range.start to -1, b1.body)
        val b12 = Branch(0 to b1.range.end, b1.body)
        val b21 = Branch(b2.range.start to 255, b2.body)
        val b22 = Branch(256 to b2.range.end, b2.body)

        val ascii = (if (b12.range.end < 0) asci else b12 +: asci) :+ b21
        val utf = (utf1 :+ b11) ++ (if (b22.range.start < 256) utf2
                                    else b22 +: utf2)

        val body = genSwitch(ascii) :+ cq"_ => ${genIf(utf).body}"

        q"${Match(q"codePoint", body.toList)}"
      case _ =>
        q"${genIf(utf1 :+ b1).body}"
    }
  }

  def generate(i: Int): Tree = {
    def states =
      dfa.links.indices.toList
        .map(st => (st, TermName(s"group${i}_state$st")))

    val cases = states.map {
      case (st, fun) => cq"$st => $fun"
    }
    val bodies = states.map {
      case (st, fun) => q"def $fun = {${generateCaseBody(st)}}"
    }
    q"""
      def ${TermName(s"runGroup$i")}(): Int = {
        var state: Int = 0
        matchBuilder.setLength(0)
        while(state >= 0) {
          codePoint = currentChar.toInt
          state = ${Match(q"state", cases)}
          if(state >= 0) {
            matchBuilder.append(currentChar)
            currentChar = getNextChar
          }
        }
        state
      }
      ..$bodies
    """
  }

}
