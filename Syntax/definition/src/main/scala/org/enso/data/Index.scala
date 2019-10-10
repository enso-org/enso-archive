package org.enso.data

/** Strongly typed index position in a container. */
case class Index(index: Int) extends Ordered[Index] {
  def +(offset: Int): Index   = Index(index + offset)
  def +(offset: Size): Index  = Index(index + offset.value)
  def -(offset: Size): Index  = Index(index - offset.value)
  def +(offset: Index): Index = Index(index + offset.index)

  /** Span between two text positions. Operands order is irrelevant. */
  def <->(that: Index): Span = Span(this, that)

  def compare(rhs: Index): Int = index compare rhs.index
}

object Index {
  val Start = Index(0)
}
