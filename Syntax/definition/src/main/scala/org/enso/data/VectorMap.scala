package org.enso.data

/** Read only zipper-like map.
 *
 *  Sparse, sorted, vector based map
 *  with O(1) access, when accessing adjacent keys
 *  with O(N) access, when accessing random keys
 *  this is achieved by remembering the index of
 *  last accessed key in local variable `index`
 */
final class VectorMap[K: Ordering, V](values: Seq[(K, V)]) {
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
