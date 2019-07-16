package org.enso.flexer

import java.awt.Desktop
import java.net.URI
import java.net.URLEncoder

import org.enso.Logger
import org.enso.flexer.State._
import org.feijoas.mango.common.{collect => RRR}

import scala.collection.mutable

case class DFA(
  vocabulary: Vocabulary,
  links: Array[Array[Int]],
  endStatePriorityMap: mutable.Map[Int, StateDesc]
)

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
        lines += s"""$source [color="$gray" fontcolor="$gray"]"""
      } else {
        lines += s"""$source"""
      }
      for ((range, target) <- state.links2.asMapOfRanges()) {
        lines += s"""$source -> $target [label="$range"]"""
      }
      for (target <- state.isoLinks) {
        lines += s"""$source -> $target [style="dashed" color="$gray"]"""
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
        for ((range, vocIx) <- vocabulary) {
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
        dfaMatrix :+= Array.fill(vocabulary.size)(-1)
        logger.log(s"DFA[$id] = $key")
        id
      }

      logger.group(s"Preparing start points") {
        val startIsos = state(0).isos
        addDFAKey(startIsos)
      }

      var i = 0
      while (i < dfaRows) {
        val isos = dfaIsoKeys(i)
        logger.group(s"Computing DFA[$i]") {

          for ((voc, vocIx) <- vocabulary) {
            logger.group(s"Vocabulary '$voc'") {
              var tt = Set[Int]()
              for (iso <- isos) {
                val tgt = nfaMatrix(iso)(vocIx)
                if (tgt != -1)
                  tt = tt ++ state(tgt).isos
              }
              if (tt.nonEmpty) {
                dfaMatrix(i)(vocIx) = dfaIsoMap.get(tt) match {
                  case None => addDFAKey(tt)
                  case Some(id) =>
                    logger.log(s"Existing DFA ID $id")
                    id
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

      val dfaEndStatePriorityMap = mutable.Map[Int, StateDesc]()
      for ((isos, dfaIx) <- dfaIsoKeys.zipWithIndex) {
        val iso = isos.maxBy(nfaEndStatePriorityMap.getOrElse(_, -1))
        nfaEndStatePriorityMap.get(iso) match {
          case None =>
          case Some(p) =>
            dfaEndStatePriorityMap += dfaIx -> StateDesc(p, state(iso).code)
        }
      }

      //      println(">>>", nfaEndStatePriorityMap)
      //      println(">>>", dfaEndStatePriorityMap)

      DFA(vocabulary, dfaMatrix, dfaEndStatePriorityMap)
    }
  }
}
