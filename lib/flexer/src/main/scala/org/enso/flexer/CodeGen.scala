package org.enso.flexer

import org.enso.flexer.State.StateDesc

import scala.annotation.tailrec
import scala.collection.mutable
import scala.reflect.runtime.universe._

case class CodeGen(dfa: DFA) {

  var code    = q""
  val offsets = mutable.Map(0 -> 0)

  case class Branch(rangeEnd: Int, targetState: Int)

  def cleanBranches(revInput: List[Branch]): List[Branch] = {
    @tailrec def go(rIn: List[Branch], out: List[Branch]): List[Branch] =
      rIn match {
        case Nil => out
        case i :: is =>
          out match {
            case Nil => go(is, i :: Nil)
            case o :: _ =>
              if (o.targetState == i.targetState) go(is, out)
              else go(is, i :: out)
          }
      }
    go(revInput, Nil)
  }

  def genBranches(
    branches: List[Branch],
    maybeState: Option[StateDesc],
    offset: Int
  ): Tree = {
    branches.foldLeft(identity[Tree] _) {
      case (ifBlock, branch) =>
        elseBody =>
          val body = (branch.targetState, maybeState, offset) match {
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
          ifBlock(
            q"if (codePoint <= ${branch.rangeEnd}) $body else $elseBody"
          )
    }(q"-2")
  }

  def generateStateMatch(): Tree = {
    val branches = dfa.links.indices.toList.map { stateIx =>
      val state = dfa.endStatePriorityMap.get(stateIx)
      val revBranches: List[Branch] =
        dfa.vocabulary.iter.foldLeft(List[Branch]()) {
          case (list, (range, vocIx)) =>
            val targetState = dfa.links(stateIx)(vocIx)
            Branch(range.end, targetState) :: list
        }

      val branches = cleanBranches(revBranches)

      cq"$stateIx => ${genBranches(branches, state, offsets.getOrElse(stateIx, 0))}"
    }
    Match(q"state", branches)
  }

  def generate(i: Int): Tree = {
    code = q"""
            ..$code;
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
    code
  }
}
