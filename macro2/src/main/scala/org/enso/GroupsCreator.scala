package org.enso

import java.awt.Desktop
import java.net.URI
import java.net.URLEncoder

import org.feijoas.mango.common.{collect => RRR}
import org.feijoas.mango.common.collect.mutable.RangeMap

import scala.collection.mutable.ArrayBuffer
import scala.collection.immutable
import scala.collection.mutable
import scala.reflect.macros.blackbox.Context

object GroupsCreator {

  def createGroups(c: Context)(): c.Expr[Unit] = {
    import c.universe._
    val x = new Helper[c.type](c).createGroups
    c.warning(c.enclosingPosition, showCode(x))
    c.Expr(x)
  }

  class Helper[C <: Context](val c: C) {
    import c.universe._

    def createGroups: c.Tree = q"{..${groups.map(_.generate())}}"

    var groups = new ArrayBuffer[Group[_]]()

    def defineGroup[T](label: String = "unnamed"): Group[T] = {
      val groupIndex = groups.length
      val group      = new Group[T](groupIndex)
      groups.append(group)
      group
    }

    trait Pattern {
      def |(that: Pattern) = Or(this, that)

      def >>(that: Pattern) = Seq_(this, that)

      def many(): Pattern = Many(this)

      def many1(): Pattern = this >> many
    }

    implicit final def charToExpr(char: Char): Pattern =
      Ran(char, char)

    implicit final def stringToExpr(s: String): Pattern =
      s.tail.foldLeft(char(s.head))(_ >> _)

    class ExtendedChar(_this: Char) {
      final def ||(that: Char): Pattern =
        Or(char(_this), char(that))
    }

    implicit final def extendChar(i: Char): ExtendedChar = new ExtendedChar(i)

    final def char(c: Char): Pattern = range(c, c)

    final def range(start: Char, end: Char): Pattern =
      Ran(start, end)

    final def range(start: Int, end: Int): Pattern = Ran(start, end)

    val any: Pattern  = range(0, Int.MaxValue)
    val pass: Pattern = Pass
    val eof: Pattern  = char('\3')

    private val initialization: Unit = {

      ///////////////////////////////////////////

      val lowerLetter = range('a', 'z')
      val upperLetter = range('A', 'Z')
      val digit       = range('0', '9')
      val alphanum    = digit | lowerLetter | upperLetter

      val decimal     = digit.many1
      val indentChar  = lowerLetter | upperLetter | digit | '_'
      val identBody   = indentChar.many >> '\''.many
      val variable    = lowerLetter >> identBody
      val constructor = upperLetter >> identBody
      val whitespace  = ' '.many1
      val newline     = '\n'

      val kwDef = "def"

      val NORMAL        = defineGroup[Unit]("Normal")
      val PARENSED      = defineGroup[Unit]("Parensed")
      val NEWLINE       = defineGroup[Unit]("Newline")
      val NUMBER_PHASE2 = defineGroup[Unit]("Number Phase 2")
      val NUMBER_PHASE3 = defineGroup[Unit]("Number Phase 3")

      // format: off

      ////// NORMAL //////
      NORMAL rule whitespace run q"onWhitespace()"
      NORMAL rule kwDef run q"""println("def!!!")"""
      NORMAL rule variable run q"app(AST.Var)"
      NORMAL rule newline run q"beginGroup(${NEWLINE.index})"
      NORMAL rule "(" run q"{onGroupBegin(); beginGroup(${PARENSED.index})}"
      NORMAL rule eof run q"onEOF()"

      ////// PARENSED //////
      PARENSED.cloneRulesFrom(NORMAL)
      PARENSED rule ")" run q"{onGroupEnd(); endGroup()}"

      ////////////////////////////////
      // NUMBER (e.g. 16_ff0000.ff) //
      ////////////////////////////////

      NORMAL rule decimal run q"{numberPart2 = currentMatch; beginGroup(${NUMBER_PHASE2.index})}"


      NUMBER_PHASE2 rule ("_" >> alphanum.many1) run
        q"""{
        endGroup()
        numberPart1 = numberPart2
        numberPart2 = currentMatch.substring(1)
        beginGroup(${NUMBER_PHASE3.index})
      }"""

      NUMBER_PHASE2 rule pass run q"{endGroup(); submitNumber()}"

      NUMBER_PHASE3 rule ("." >> alphanum.many1) run
        q"""{
        endGroup()
        numberPart3 = currentMatch.substring(1)
        submitNumber()
      }"""

      NUMBER_PHASE3 rule pass run q"{endGroup(); submitNumber()}"

      ////////////
      // String //
      ////////////

      NORMAL rule "'".many1 run
        q"""{
        val size = currentMatch.length
        if (size == 2) submitEmptyText()
      }"""

      ////// NEWLINE //////
      NEWLINE rule ((whitespace | pass) >> newline) run q"{onWhitespace(-1); onEmptyLine()}"

      NEWLINE rule (whitespace | pass) run
        q"""{
        onWhitespace()
        if (lastOffset == currentBlock.indent) {
          onBlockNewline()
        } else if (lastOffset > currentBlock.indent) {
          onBlockBegin(useLastOffset())
        } else {
          onBlockEnd(useLastOffset())
        }
        endGroup()
      }"""
      // format: on
    }

    final def replaceGroupSymbols(s: String, lst: List[Group[Unit]]): String = {
      var out = s
      for ((grp, ix) <- lst.zipWithIndex) {
        out = out.replaceAll(s"___${ix}___", grp.index.toString)
      }
      out
    }

    case object Pass extends Pattern

    case class Ran(start: Int, end: Int) extends Pattern

    case class Or(left: Pattern, right: Pattern) extends Pattern

    case class Seq_(first: Pattern, second: Pattern) extends Pattern

    case class Many(body: Pattern) extends Pattern

    class Rule[T](val expr: Pattern, var fn: c.Tree) {

      def run(f: c.Tree): Unit = {
        fn = f
      }
    }

    class Group[T](val index: Int) {
      val rules = ArrayBuffer[Rule[T]]()

      def rule(r: Rule[T]): Rule[T] = {
        rules.append(r)
        r
      }

      def rule(expr: Pattern): Rule[T] = rule(new Rule[T](expr, null))

      def cloneRulesFrom(that: Group[T]): Unit = {
        rules.appendAll(that.rules)
      }

      def buildAutomata(): NFA = {
        val nfa       = new NFA
        val start     = nfa.addState()
        val endpoints = rules.map(rule => buildRuleAutomata(nfa, start, rule))
        val end       = nfa.addState()
        nfa.state(end).end = true
        for (endpoint <- endpoints) {
          nfa.link(endpoint, end)
        }
        nfa
      }

      def buildRuleAutomata[T](nfa: NFA, previous: Int, rule: Rule[T]): Int = {
        val end = buildExprAutomata(nfa, previous, rule.expr)
        nfa.state(end).end = true
        nfa
          .state(end)
          .code = q"{currentMatch = matchBuilder.result(); ${rule.fn}}" // + "(currentMatch)}"
        end
      }

      def buildExprAutomata(nfa: NFA, previous: Int, expr: Pattern): Int = {
        val current = nfa.addState()
        nfa.link(previous, current)
        expr match {
          case Pass => current
          case Ran(start, end) => {
            val state = nfa.addState()
            nfa.link(current, state, start, end)
            state
          }
          case Seq_(first, second) => {
            val s1 = buildExprAutomata(nfa, current, first)
            buildExprAutomata(nfa, s1, second)
          }
          case Many(body) => {
            val s1 = nfa.addState()
            val s2 = buildExprAutomata(nfa, s1, body)
            val s3 = nfa.addState()
            nfa.link(current, s1)
            nfa.link(current, s3)
            nfa.link(s2, s3)
            nfa.link(s3, s1)
            s3
          }
          case Or(first, second) => {
            val s1 = buildExprAutomata(nfa, current, first)
            val s2 = buildExprAutomata(nfa, current, second)
            val s3 = nfa.addState()
            nfa.link(s1, s3)
            nfa.link(s2, s3)
            s3
          }
        }
      }

      def generate(): c.Tree = {
        val nfa = buildAutomata()
        nfa.computeIsos()
        val dfa  = nfa.computeDFA()
        var code = CodeGen(dfa).generate(index)
        q"{..$code; groups(${Literal(Constant(index))}) = ${TermName(s"runGroup$index")}}"
      }
    }

    class NFA {
      val logger                             = new Logger()
      val states: mutable.ArrayBuffer[State] = new mutable.ArrayBuffer()
      val isoMap: mutable.Map[Set[Int], Int] = mutable.Map()

      val vocabulary = new Vocabulary()

      def addState(): Int = {
        val state = new State()
        states += state
        states.length - 1
      }

      def link(start: Int, end: Int, charStart: Char, charEnd: Char): Unit =
        link(start, end, charStart.toInt, charEnd.toInt)

      def link(start: Int, end: Int, charStart: Int, charEnd: Int): Unit = {
        vocabulary.insert(Range(charStart, charEnd))
        state(start).links2.put(RRR.Range.closed(charStart, charEnd), end)
      }

      def link(start: Int, end: Int, char: Char): Unit =
        link(start, end, char, char)

      def link(start: Int, end: Int): Unit =
        state(start).isoLinks += end

      def visualize(): String = {
        val gray  = "#AAAAAA"
        val lines = mutable.ArrayBuffer[String]()
        lines += "digraph G {"
        lines += "node [shape=circle width=0.8]"
        for ((state, source) <- states.zipWithIndex) {
          if (state.links2.isEmpty) {
            lines += s"""${source} [color="${gray}" fontcolor="${gray}"]"""
          } else {
            lines += s"""${source}"""
          }
          for ((range, target) <- state.links2.asMapOfRanges()) {
            lines += s"""${source} -> ${target} [label="${range}"]"""
          }
          for (target <- state.isoLinks) {
            lines += s"""${source} -> ${target} [style="dashed" color="${gray}"]"""
          }
        }

        lines += "}"
        val code    = lines.mkString("\n")
        var webCode = code
        webCode = URLEncoder.encode(webCode, "UTF-8")
        webCode = webCode.replaceAll("[+]", "%20")
        val address = "https://dreampuf.github.io/GraphvizOnline/#" + webCode
        Desktop.getDesktop().browse(new URI(address))
        code
      }

      def state(ix: Int): State =
        states(ix)

      def computeIsosFor(i: Int): Unit = {
        val s    = state(i)
        var isos = Set[Int](i)
        if (s.isosComputed == NotComputed) {
          var circular = false
          s.isosComputed = InProgress
          s.isoLinks.foreach((tgt) => {
            computeIsosFor(tgt)
            val s2 = state(tgt)
            isos = isos + tgt
            isos = isos ++ s2.isos
            if (s2.isosComputed == InProgress) {
              circular = true
            }
          })
          s.isos = isos
          if (!circular) {
            isoMap.get(isos) match {
              case Some(id) => s.isosId = id
              case None => {
                val id = isoMap.size
                s.isosId = id
                isoMap += (isos -> id)
              }
            }
            s.isosComputed = Computed
          }
        }
      }

      def computeIsos(): Unit =
        for (i <- states.indices) {
          computeIsosFor(i)
        }

      def computeNFAMatrix(): Array[Array[Int]] = {
        logger.group("Computing NFA Matrix") {
          val matrix = Array.ofDim[Int](states.length, vocabulary.size)
          for (stateIx <- states.indices) {
            val s = state(stateIx)
            for ((range, vocIx) <- vocabulary.iter) {
              s.links2.get(range.start) match {
                case Some(tgt) => matrix(stateIx)(vocIx) = tgt
                case None      => matrix(stateIx)(vocIx) = -1
              }
            }
          }
          matrix
        }
      }

      def computeDFA(): DFA = {
        logger.group("Computing DFA Matrix") {
          val nfaMatrix  = computeNFAMatrix()
          var dfaRows    = 0
          var dfaMatrix  = Array[Array[Int]]()
          val dfaIsoMap  = mutable.Map[Set[Int], Int]()
          val dfaIsoKeys = mutable.ArrayBuffer[Set[Int]]()

          def addDFAKey(key: Set[Int]): Int = {
            val id = dfaIsoMap.size
            dfaIsoMap += (key -> id)
            dfaIsoKeys += key
            dfaRows += 1
            dfaMatrix :+= Array.fill(vocabulary.size) {
              -1
            }
            logger.log(s"DFA[${id}] = ${key}")
            id
          }

          logger.group(s"Preparing start points") {
            val startIsos = state(0).isos
            addDFAKey(startIsos)
          }

          var i = 0
          while (i < dfaRows) {
            val isos = dfaIsoKeys(i)
            logger.group(s"Computing DFA[${i}]") {

              for ((voc, vocIx) <- vocabulary.iter) {
                logger.group(s"Vocabulary '${voc}'") {
                  var tt = Set[Int]()
                  isos.foreach(iso => {
                    val tgt = nfaMatrix(iso)(vocIx)
                    if (tgt != -1) {
                      tt = tt ++ state(tgt).isos
                    }
                  })
                  if (!tt.isEmpty) {
                    dfaMatrix(i)(vocIx) = dfaIsoMap.get(tt) match {
                      case None => addDFAKey(tt)
                      case Some(id) => {
                        logger.log(s"Existing DFA ID ${id}")
                        id
                      }
                    }
                  }
                }
              }
            }
            i += 1
          }

          val nfaEndStatePriorityMap = mutable.Map[Int, Int]()
          for (i <- nfaMatrix.indices) {
            if (state(i).end) {
              nfaEndStatePriorityMap += (i -> (nfaMatrix.length - i))
            }
          }

          val dfaEndStatePriorityMap = mutable.Map[Int, (Int, c.Tree)]()
          for ((isos, dfaIx) <- dfaIsoKeys.zipWithIndex) {
            var priority = -1
            var code     = q""
            isos.foreach(iso => {
              nfaEndStatePriorityMap.get(iso) match {
                case None => {}
                case Some(p) => {
                  if (p > priority) {
                    priority = p
                    code     = state(iso).code
                  }
                }
              }
            })
            if (priority >= 0) {
              dfaEndStatePriorityMap += dfaIx -> (priority, code)
            }
          }

          DFA(vocabulary, dfaMatrix, dfaEndStatePriorityMap)
        }
      }
    }

    case class DFA(
      vocabulary: Vocabulary,
      links: Array[Array[Int]],
      endStatePriorityMap: mutable.Map[Int, (Int, c.Tree)]
    )

    case class CodeGen(dfa: DFA) {
      var code = q""

      def generateStateMatch(): c.Tree = {
        val branches = dfa.links.indices.toList.map { state =>
          val caseBody = dfa.vocabulary.iter.foldLeft(identity[c.Tree] _) {
            case (ifBlock, (range, ix)) =>
              elseBody =>
                val body = (dfa.links(state)(ix), dfa.endStatePriorityMap.get(state)) match {
                  case (-1, None)             => q"{state = -2}"
                  case (-1, Some((_, block))) => q"{..$block; state = -1}"
                  case (st, _)                => q"{state = $st}"
                }
                ifBlock(q"if (codePoint <= ${range.end}) $body else $elseBody")
          }(q"{}")

          cq"$state => $caseBody"
        }

        Match(q"state", branches)
      }

      def generate(i: Int): c.Tree = {
        code = q"""
            ..$code;
            def ${TermName(s"runGroup$i")}(): Int = {
              var state: Int = 0
              matchBuilder.setLength(0)
              while(state >= 0) {
                codePoint = currentChar.toInt
                ${generateStateMatch()}
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

    trait IsoComputeState

    case object NotComputed extends IsoComputeState

    case object InProgress extends IsoComputeState

    case object Computed extends IsoComputeState

    class State {
      //  val links                         = mutable.SortedMap[Int, Int]()
      val isoLinks                      = new mutable.ArrayBuffer[Int]()
      var isos                          = Set[Int]()
      var isosId: Int                   = 0
      var isosComputed: IsoComputeState = NotComputed

      var start  = false
      var end    = false
      var code   = q""
      val links2 = RangeMap[Int, Int, Ordering.Int.type]()
    }

    ////////////////
    // Vocabulary //
    ////////////////

    case class Range(start: Int, end: Int)

    class Vocabulary {
      var divisions = immutable.SortedSet[Int](0, Int.MaxValue)

      def insert(range: Range): Unit = {
        divisions = divisions + range.start
        divisions = divisions + (range.end + 1)
      }

      def size(): Int = divisions.size - 1

      override def toString: String =
        "Vocabulary(" + divisions.toList.map(_.toString).mkString(",") + ")"

      def iter[U]: Iterator[(Range, Int)] = {
        var lastDiv = 0
        for ((i, ix) <- divisions.iterator.drop(1).zipWithIndex) yield {
          val r = (Range(lastDiv, i - 1), ix)
          lastDiv = i
          r
        }
      }
    }
  }
}
