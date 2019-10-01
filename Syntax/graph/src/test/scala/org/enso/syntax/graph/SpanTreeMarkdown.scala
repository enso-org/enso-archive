package org.enso.syntax.graph

import scala.collection.mutable

/** All expected positions and spans are sorted in increasing order. */
case class SpanTreeMarkdown(
  program: String,
  expectedInsertionPoints: Seq[TextPosition],
  expectedSettableParts: Seq[TextSpan],
  expectedErasableParts: Seq[TextSpan]
)

/** This object introduces a simple markdown notation for testing available
  * action. There are 3 types of marks available:
  *  - ⎀ denotes an insertion point
  *  - «elem» denotes that `elem` allows [[Action.Set]] and [[Action.Erase]].
  *  - ‹elem› denotes that `elem` allows [[Action.Set]].
  *
  * For example, `foo ⎀«a»⎀` describes the following test case:
  *  - `foo` does not support any actions
  *  - there are two insertion points: before and after `a`
  *  - `a` can be replaced or erased
  *  - no other actions are possible
  **/
object SpanTreeMarkdown {
  val InsertionMark                 = '⎀'
  val BeginSettableAndErasableBlock = '«'
  val EndSettableAndErasableBlock   = '»'
  val BeginSettableBlock            = '‹'
  val EndSettableBlock              = '›'
  val markdownChars: Set[Char] =
    Set(
      InsertionMark,
      BeginSettableAndErasableBlock,
      EndSettableAndErasableBlock,
      BeginSettableBlock,
      EndSettableBlock
    )

  /** Helper class for discovering block spans. */
  case class BlockManager() {

    /** Opened, unclosed blocks. */
    val startIndices: mutable.ArrayStack[TextPosition] = mutable.ArrayStack()

    /** All observed blocks */
    val spans: mutable.ArrayBuffer[TextSpan] = mutable.ArrayBuffer()

    /** Open block at given position. */
    def open(position: TextPosition): Unit = startIndices.push(position)

    /** Close block at given position. */
    def close(position: TextPosition): Unit = {
      val start = startIndices.pop()
      val block = start <-> position
      spans += block
    }
  }

  def apply(markedProgram: String): SpanTreeMarkdown = {
    val program = markedProgram.filterNot(markdownChars.contains)

    val insertable       = mutable.ArrayBuffer[TextPosition]()
    val settable         = BlockManager()
    val settableErasable = BlockManager()

    var index = TextPosition(0)
    markedProgram.foreach {
      case InsertionMark =>
        insertable += index
      case BeginSettableAndErasableBlock =>
        settableErasable.open(index)
      case EndSettableAndErasableBlock =>
        settableErasable.close(index)
      case BeginSettableBlock =>
        settable.open(index)
      case EndSettableBlock =>
        settable.close(index)
      case _ =>
        // only non-marker characters increase index
        index += 1
    }

    /** prepends element if action is enabled on root */
    def perhapsWith[T <: Ordered[T]](
      rootElem: T,
      elems: Seq[T],
      rootAction: Action
    ): Seq[T] = {
      val addRoot    = Actions.Root.contains(rootAction)
      val finalElems = if (addRoot) rootElem +: elems else elems
      finalElems.sorted
    }

    val rootSpan      = TextSpan(program)
    val settableSpans = settable.spans ++ settableErasable.spans
    SpanTreeMarkdown(
      program,
      perhapsWith(TextPosition.Start, insertable, Action.Insert),
      perhapsWith(rootSpan, settableSpans, Action.Set),
      perhapsWith(rootSpan, settableErasable.spans, Action.Erase)
    )
  }
}
