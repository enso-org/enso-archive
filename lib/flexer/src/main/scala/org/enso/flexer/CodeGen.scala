package org.enso.flexer

import org.enso.flexer.CodeGen.MAX_ASCII_CODE
import org.enso.flexer.CodeGen.MIN_ASCII_CODE
import org.enso.flexer.State.StateDesc
import org.enso.flexer.Utils._

import scala.collection.immutable.Range
import scala.collection.mutable
import scala.reflect.runtime.universe._

case class CodeGen(dfa: DFA) {

  val stateHasOverlappingRules = mutable.Map(0 -> false)

  case class Branch(range: Range, body: Tree)

  def genBranchBody(
    nextState: Int,
    maybeBody: Option[StateDesc],
    rulesOverlap: Boolean
  ): Tree = {
    (nextState, maybeBody, rulesOverlap) match {
      case (-1, None, _)           => q"-2"
      case (-1, Some(body), false) => q"..${body.code}; -1"
      case (-1, Some(body), true)  => q"rewindToLastRule(); ..${body.code}; -1"

      case _ =>
        val rulesOverlap_ = maybeBody match {
          case Some(body) if !dfa.endStatePriorityMap.contains(nextState) =>
            dfa.endStatePriorityMap += nextState -> body
            stateHasOverlappingRules += nextState -> true
            true
          case _ => false
        }
        if (rulesOverlap || rulesOverlap_)
          q"charsToLastRule += charSize; ${Literal(Constant(nextState))}"
        else
          q"${Literal(Constant(nextState))}"
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
    val overlaps  = stateHasOverlappingRules.getOrElse(stateIx, false)
    val state     = dfa.endStatePriorityMap.get(stateIx)
    var nextState = dfa.links(stateIx)(0)
    var rStart    = Int.MinValue
    val branches = for {
      (range, vocIx) <- dfa.vocabulary.toVector
      newNextState = dfa.links(stateIx)(vocIx)
      rEnd         = range.start - 1
      if newNextState != nextState
    } yield Branch(rStart to rEnd, genBranchBody(nextState, state, overlaps))
      .thenDo {
        nextState = newNextState
        rStart    = range.start
      }
    val allBranches = branches :+
      Branch(rStart to Int.MaxValue, genBranchBody(nextState, state, overlaps))

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
        val body      = genSwitch(ascii) :+ cq"_ => ${genIf(utf).body}"

        q"${Match(q"codePoint", body.toList)}"
      case _ =>
        genIf(utf1 :+ b1).body
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
          state = ${Match(q"state", cases)}
          if(state >= 0) {
            matchBuilder.append(buffer(offset))
            if (buffer(offset).isHighSurrogate)
              matchBuilder.append(buffer(offset+1))
            codePoint = getNextCodePoint()
          }
        }
        state
      }
      ..$bodies
    """
  }

}

object CodeGen {

  val MIN_ASCII_CODE = 0
  val MAX_ASCII_CODE = 255

}
