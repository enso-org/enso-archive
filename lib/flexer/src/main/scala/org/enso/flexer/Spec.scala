package org.enso.flexer

import org.enso.flexer.automata.State
import org.enso.flexer.automata.DFA

import scala.collection.immutable.Range
import scala.collection.mutable
import scala.reflect.runtime.universe._

// FIXME: This file needs to be refactored. Contains a lot of ugly vars
//        and does not always provide explanation why something happens

case class Spec(dfa: DFA) {
  import Spec._

  val stateHasOverlappingRules = mutable.Map(0 -> false)

  case class Branch(range: Range, body: Tree)

  def genBranchBody(
    trgState: Int,
    maybeState: Option[State.Desc],
    rulesOverlap: Boolean
  ): Tree = (trgState, maybeState, rulesOverlap) match {
    case (State.missing, None, _) =>
      Literal(Constant(Parser.State.Status.Exit.FAIL))
    case (State.missing, Some(state), false) =>
      q"state.call(${TermName(state.rule)})"
    case (State.missing, Some(state), true) =>
      q"rewindThenCall(${TermName(state.rule)})"

    case (targetState, _, _) =>
      val rulesOverlap_ = maybeState match {
        case Some(state) if !dfa.endStatePriorityMap.contains(targetState) =>
          dfa.endStatePriorityMap += targetState -> state
          stateHasOverlappingRules += targetState -> true
          true
        case _ => false
      }
      if (rulesOverlap || rulesOverlap_)
        q"reader.lastRuleOffset = reader.offset; ${Literal(Constant(targetState))}"
      else
        q"${Literal(Constant(targetState))}"
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
        val body  = q"if (charCode <= ${a.range.end}) ${a.body} else ${b.body}"
        genIf(Branch(range, body) +: rest)
    }
  }

  def generateCaseBody(stateIx: Int): Tree = {
    val overlaps = stateHasOverlappingRules.getOrElse(stateIx, false)
    val state    = dfa.endStatePriorityMap.get(stateIx)
    var trgState = dfa.links(stateIx)(0)
    var rStart   = Int.MinValue
    val branches = dfa.vocabulary.toVector.flatMap {
      case (range, vocIx) =>
        val newTrgState = dfa.links(stateIx)(vocIx)
        if (newTrgState != trgState) {
          val rEnd      = range.start - 1
          val xtrgState = trgState
          val xrStart   = rStart
          trgState = newTrgState
          rStart   = range.start
          Some(
            Branch(xrStart to rEnd, genBranchBody(xtrgState, state, overlaps))
          )
        } else None
    }

    val allBranches = branches :+
      Branch(rStart to Int.MaxValue, genBranchBody(trgState, state, overlaps))

    val (utf1 :+ b1, rest) = allBranches.span(_.range.start < MIN_ASCII_CODE)
    val (asci, utf2)       = rest.span(_.range.end <= MAX_ASCII_CODE)

    utf2 match {
      case b2 +: utf2 =>
        val b1UTF = Branch(b1.range.start to MIN_ASCII_CODE - 1, b1.body)
        val b1ASC = Branch(MIN_ASCII_CODE to b1.range.end, b1.body)
        val b2ASC = Branch(b2.range.start to MAX_ASCII_CODE, b2.body)
        val b2UTF = Branch(MAX_ASCII_CODE + 1 to b2.range.end, b2.body)

        val emptyB1ASC = b1ASC.range.end < MIN_ASCII_CODE
        val emptyB2UTF = b2UTF.range.start <= MAX_ASCII_CODE

        val ascii     = if (emptyB1ASC) asci :+ b2ASC else b1ASC +: asci :+ b2ASC
        val utfMiddle = if (emptyB2UTF) Vector(b1UTF) else Vector(b1UTF, b2UTF)
        val utf       = utf1 ++ utfMiddle ++ utf2
        val body      = genSwitch(ascii) :+ cq"charCode => ${genIf(utf).body}"

        q"${Match(q"reader.charCode", body.toList)}"
      case _ =>
        genIf(utf1 :+ b1).body
    }
  }

  def generate(i: Int): Tree = {
    val stateNames =
      dfa.links.indices.toList
        .map(st => (st, TermName(s"state${i}_${st}")))

    val stateMatch = Match(q"state", stateNames.map {
      case (st, fun) => cq"$st => $fun"
    })
    val stateBodies = stateNames.map {
      case (st, fun) => q"def $fun = {${generateCaseBody(st)}}"
    }
    q"""
      stateDefs($i) = ${TermName(s"nextState$i")}
      def ${TermName(s"nextState$i")}(state: Int): Int = $stateMatch
      ..$stateBodies
    """
  }

}

object Spec {
  val MIN_ASCII_CODE = -1
  val MAX_ASCII_CODE = 255
}
