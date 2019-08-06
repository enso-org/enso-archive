package org.enso.data

class VectorMap[K: Ordering, V](values: Seq[(K, V)]) {
  private var index  = 0
  private val ord    = Ordering[K]
  private val vector = values.toVector.sortBy(_._1)

  private def key:   K = vector(index)._1
  private def value: V = vector(index)._2

  def get(k: K): Option[V] = {
    while (index < vector.length && ord.lteq(key, k)) {
      if (ord.equiv(key, k))
        return Some(value)
      index += 1
    }
    index -= 1
    while (index >= 0 && ord.gteq(key, k)) {
      if (ord.equiv(key, k))
        return Some(value)
      index -= 1
    }
    index += 1
    None
  }
}

object VectorMap {

  def apply[K: Ordering, V](): VectorMap[K, V] = new VectorMap(Vector[(K, V)]())
  def apply[K: Ordering, V](values: Seq[(K, V)]): VectorMap[K, V] =
    new VectorMap(values)

}
