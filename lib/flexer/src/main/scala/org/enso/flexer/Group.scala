package org.enso.flexer

import scala.collection.mutable.ArrayBuffer
import scala.reflect.runtime.universe._

class Group(val groupIx: Int) {
  var parent: Group = null
  val rules         = ArrayBuffer[Rule]()

  def rule(r: Rule): Rule = {
    rules.append(r)
    r
  }

  def rule(expr: Pattern): Rule = rule(new Rule(expr, null))

  def setParent(p: Group): Unit =
    parent = p

  def cloneRulesFrom(that: Group): Unit = {
    rules.appendAll(that.rules)
  }

  def ruleName(ruleIx: Int): TermName =
    TermName(s"group${groupIx}_rule$ruleIx")

  def allRules: Vector[Rule] = {
    val myRules = rules.to[Vector]
    if (parent != null) myRules ++ parent.allRules else myRules
  }

  def buildAutomata(): NFA = {
    val nfa   = new NFA
    val start = nfa.addState()
    val endpoints = allRules.zipWithIndex.map {
      case (rule, ix) => buildRuleAutomata(nfa, start, ix, rule)
    }
    val end = nfa.addState()
    nfa.state(end).end = true
    for (endpoint <- endpoints) {
      nfa.link(endpoint, end)
    }
    nfa
  }

  def buildRuleAutomata(nfa: NFA, last: Int, ruleIx: Int, rule: Rule): Int = {
    val end          = buildExprAutomata(nfa, last, rule.expr)
    val currentMatch = q"currentMatch = matchBuilder.result()"
    nfa.state(end).end  = true
    nfa.state(end).code = q"{$currentMatch; ${ruleName(ruleIx)}()}"
    end
  }

  def buildExprAutomata(nfa: NFA, last: Int, expr: Pattern): Int = {
    val current = nfa.addState()
    nfa.link(last, current)
    expr match {
      case None_ => nfa.addState()
      case Pass  => current
      case Ran(start, end) =>
        val state = nfa.addState()
        nfa.link(current, state, start, end)
        state
      case Seq_(first, second) =>
        val s1 = buildExprAutomata(nfa, current, first)
        buildExprAutomata(nfa, s1, second)
      case Many(body) =>
        val s1 = nfa.addState()
        val s2 = buildExprAutomata(nfa, s1, body)
        val s3 = nfa.addState()
        nfa.link(current, s1)
        nfa.link(current, s3)
        nfa.link(s2, s3)
        nfa.link(s3, s1)
        s3
      case Or(first, second) =>
        val s1 = buildExprAutomata(nfa, current, first)
        val s2 = buildExprAutomata(nfa, current, second)
        val s3 = nfa.addState()
        nfa.link(s1, s3)
        nfa.link(s2, s3)
        s3
    }
  }

  def generate(): Tree = {
    val nfa = buildAutomata()
    nfa.computeIsos()
    val dfa  = nfa.computeDFA()
    var code = CodeGen(dfa).generate(groupIx)

    code =
      q"{..$code; groups(${Literal(Constant(groupIx))}) = ${TermName(s"runGroup$groupIx")}}"
    allRules.zipWithIndex.foreach {
      case (rule, ruleIx) =>
        code = q"..$code; def ${ruleName(ruleIx)}() = ${rule.fn}"
    }
    code
  }
}
