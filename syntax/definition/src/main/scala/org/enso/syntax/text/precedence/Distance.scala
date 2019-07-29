package org.enso.syntax.text.precedence

import org.enso.syntax.text.AST
import org.enso.data.List1
import org.enso.data.Shifted
import scala.annotation.tailrec

object Distance {

  /** Segment is a list of AST tokens which are not separated with spaces */
  type Segment = List1[AST]

  /** Partition the AST stream to non-spaced segments. */
  def partition(lst: List1[Shifted[AST]]): Shifted[Shifted.List1[Segment]] = {
    @tailrec
    def go(
      input: List[Shifted[AST]],
      lastOff: Int,
      current: List1[AST],
      out: List[Shifted[Segment]]
    ): Shifted[Shifted.List1[Segment]] = input match {
      case Nil => Shifted(lastOff, Shifted.List1(current, out))
      case ast1 :: ast2_ =>
        val (current2, out2) = lastOff match {
          case 0 => (ast1.el :: current, out)
          case i => (List1(ast1.el), Shifted(i, current) :: out)
        }
        go(ast2_, ast1.off, current2, out2)
    }
    go(lst.tail, lst.head.off, List1(lst.head.el), Nil)
  }
}
