package org.enso.graph

import shapeless.ops.{hlist, nat}
import shapeless.{::, HList, HNil, Nat}

// Don't use AnyType here, as it gets boxed sometimes.
import io.estatico.newtype.macros.newtype

// Type-level literals (_0, _1, ...)
import shapeless.nat._

/* TODO [AA, WD] The following are features that we want for this graph:
 *  - Graphviz output for visualisations.
 *  - Utilities for copying (sub-)graphs.
 *  - Storage should keep a free-list and re-use space in the underlying buffers
 *    as much as possible.
 *  - Basic equality testing (that should be overridden as needed).
 */

// ============================================================================
// === HList generic utilities ================================================
// ============================================================================

// ================
// === HListSum ===
// ================

trait HListSum[L <: HList] {
  type Out <: Nat
}
object HListSum {
  type Aux[L <: HList, X] = HListSum[L] { type Out = X }

  def apply[L <: HList](implicit ev: HListSum[L]): Aux[L, ev.Out] = ev

  implicit val onNil: HListSum.Aux[HNil, _0] =
    new HListSum[HNil] { type Out = _0 }

  implicit def onCons[H <: Nat, T <: HList, TS <: Nat](
    implicit
    rest: HListSum.Aux[T, TS],
    all: nat.Sum[H, TS]
  ): HListSum.Aux[H :: T, all.Out] =
    new HListSum[H :: T] { type Out = all.Out }

  object test {
    implicitly[HListSum.Aux[HNil, _0]]
    implicitly[HListSum.Aux[_1 :: HNil, _1]]
    implicitly[HListSum.Aux[_1 :: _2 :: HNil, _3]]
    implicitly[HListSum.Aux[_1 :: _2 :: _3 :: HNil, _6]]
  }
}

// =======================
// === HListOfNatToVec ===
// =======================

trait HListOfNatToVec[L <: HList] {
  val out: Vector[Int]
}
object HListOfNatToVec {
  implicit def onNil: HListOfNatToVec[HNil] =
    new HListOfNatToVec[HNil] { val out = Vector[Int]() }

  implicit def onCons[Head <: Nat, Tail <: HList](
    implicit
    tail: HListOfNatToVec[Tail],
    head: nat.ToInt[Head]
  ): HListOfNatToVec[Head :: Tail] = new HListOfNatToVec[Head :: Tail] {
    val out = head() +: tail.out
  }
}

// ======================
// === HListTakeUntil ===
// ======================

trait HListTakeUntil[T, List <: HList] {
  type Out <: HList
}

object HListTakeUntil extends HListTakeUntilDefaults {
  type Aux[T, List <: HList, X] = HListTakeUntil[T, List] { type Out = X }

  def apply[T, List <: HList](
    implicit ev: HListTakeUntil[T, List]
  ): Aux[T, List, ev.Out] = ev

  implicit def onNil[T]: HListTakeUntil.Aux[T, HNil, HNil] =
    new HListTakeUntil[T, HNil] { type Out = HNil }

  implicit def onConsFound[Head, Tail <: HList]
    : HListTakeUntil.Aux[Head, Head :: Tail, HNil] =
    new HListTakeUntil[Head, Head :: Tail] { type Out = HNil }
}

trait HListTakeUntilDefaults {
  implicit def onConsNotFound[T, Head, Tail <: HList, Tail2 <: HList](
    implicit
    ev1: HListTakeUntil.Aux[T, Tail, Tail2]
  ): HListTakeUntil.Aux[T, Head :: Tail, Head :: Tail2] =
    new HListTakeUntil[T, Head :: Tail] { type Out = Head :: Tail2 }

  object test {
    case class A()
    case class B()
    case class C()
    implicitly[HListTakeUntil.Aux[A, HNil, HNil]]
    implicitly[HListTakeUntil.Aux[A, A :: B :: C :: HNil, HNil]]
    implicitly[HListTakeUntil.Aux[B, A :: B :: C :: HNil, A :: HNil]]
    implicitly[HListTakeUntil.Aux[C, A :: B :: C :: HNil, A :: B :: HNil]]
  }
}

// ============================================================================
// === Graph-specific utilities ===============================================
// ============================================================================

// =============
// === Sized ===
// =============

/** Abstraction for sized objects. Each sized object is compile-time aware of
  * the number of ints it occupies. */
trait Sized[T] {
  type Out <: Nat
}
object Sized {
  type Aux[T, X] = Sized[T] { type Out = X }
  def apply[T](implicit ev: Sized[T]): Aux[T, ev.Out] = ev

  implicit def instance[
    List <: HList,
    ListOfSizes <: HList,
    TotalSize <: Nat
  ](
    implicit
    ev1: MapSized.Aux[List, ListOfSizes],
    ev2: HListSum.Aux[ListOfSizes, TotalSize]
  ): Sized.Aux[List, TotalSize] =
    new Sized[List] { type Out = TotalSize }
}

/** Utility for accessing size as Int */
trait KnownSize[T] extends Sized[T] {
  val asInt: Int
}
object KnownSize {
  implicit def instance[T, Size <: Nat](
    implicit
    ev: Sized.Aux[T, Size],
    sizeEv: nat.ToInt[Size]
  ): KnownSize[T] = new KnownSize[T] { val asInt: Int = sizeEv() }
}

// ================
// === MapSized ===
// ================

/**
  * Extracts the sizes of all types in a list of types. All of these types must
  * be `Sized`.
  *
  * @tparam L the list of elements to extract sizes from
  */
trait MapSized[L <: HList] {
  type Out <: HList
}
object MapSized {
  type Aux[L <: HList, X] = MapSized[L] { type Out = X }

  def apply[L <: HList](implicit ev: MapSized[L]): Aux[L, ev.Out] = ev

  implicit val onNil: MapSized.Aux[HNil, HNil] =
    new MapSized[HNil] { type Out = HNil }

  implicit def onCons[H, T <: HList, TS <: HList, HSize <: Nat](
    implicit
    rest: MapSized.Aux[T, TS],
    headSize: Sized.Aux[H, HSize]
  ): MapSized.Aux[H :: T, HSize :: TS] =
    new MapSized[H :: T] { type Out = HSize :: TS }

  object test {
    case class A()
    case class B()
    case class C()
    implicit def sizedA = new Sized[A] { type Out = _1 }
    implicit def sizedB = new Sized[B] { type Out = _3 }
    implicit def sizedC = new Sized[C] { type Out = _5 }
    implicitly[MapSized.Aux[HNil, HNil]]
    implicitly[MapSized.Aux[A :: B :: C :: HNil, _1 :: _3 :: _5 :: HNil]]
  }
}

// =================
// === SizeUntil ===
// =================

/**
  * Computes the size of the types in a HList up to but not including `Elem`.
  *
  * @tparam Elem the type of the element to stop computing at
  * @tparam List the list of types to compute over
  */
trait SizeUntil[Elem, List <: HList] {
  type Out <: Nat
  val asInt: Int
}
object SizeUntil {
  type Aux[Elem, List <: HList, X] = SizeUntil[Elem, List] { type Out = X }

  def apply[Elem, List <: HList](
    implicit ev: SizeUntil[Elem, List]
  ): Aux[Elem, List, ev.Out] = ev

  implicit def instance[
    Elem,
    List <: HList,
    PriorElems <: HList,
    PriorFieldSizes <: HList,
    PriorFieldsSize <: Nat
  ](
    implicit
    ev1: HListTakeUntil.Aux[Elem, List, PriorElems],
    ev2: MapSized.Aux[PriorElems, PriorFieldSizes],
    ev3: HListSum.Aux[PriorFieldSizes, PriorFieldsSize],
    sizeAsInt: nat.ToInt[PriorFieldsSize]
  ): SizeUntil.Aux[Elem, List, PriorFieldsSize] =
    new SizeUntil[Elem, List] {
      type Out = PriorFieldsSize
      val asInt = sizeAsInt()
    }

  object test {
    case class A()
    case class B()
    case class C()
    implicit def sizedA = new Sized[A] { type Out = _1 }
    implicit def sizedB = new Sized[B] { type Out = _3 }
    implicit def sizedC = new Sized[C] { type Out = _5 }
    implicitly[SizeUntil.Aux[A, HNil, _0]]
    implicitly[SizeUntil.Aux[A, A :: B :: C :: HNil, _0]]
    implicitly[SizeUntil.Aux[B, A :: B :: C :: HNil, _1]]
    implicitly[SizeUntil.Aux[C, A :: B :: C :: HNil, _4]]
  }
}

// ============================================================================
// === Graph ==================================================================
// ============================================================================

/** A generic graph implementation.
  *
  * It should not be used directly by your programs, and instead should be used
  * to implement custom graph instances by extending the [[Graph]] trait.
  */
trait Graph
object Graph {

  // ==========================
  // === Smart Constructors ===
  // ==========================

  def apply[G <: Graph: GraphInfo](): GraphData[G] = new GraphData[G]()

  // =================
  // === Component ===
  // =================

  /** A graph is a set of tightly connected components, such as nodes, edges,
    * or groups. Users of this library are free to define custom components
    * by extending the component trait. See the examples to learn more. */
  trait Component
  object Component {

    // === Ref ===

    /** A generic reference to a graph component. For example, the `Node` type
      * could be defined as `type Node = Ref[MyGraph, Nodes]`, where `Nodes` is
      * the appropriate component in the graph. */
    @newtype
    final case class Ref[G <: Graph, C <: Component](ix: Int)

    // === Refined ===

    /** Type refinement for component references. It can be used to add
      * additional information to components. For example, a node with an
      * information that its shape is `App`, can be encoded with the following
      * type: `Refined[Shape,App,Node]`*/
    @newtype
    final case class Refined[C <: Component.Field, Spec, T](wrapped: T)
    object Refined {
      implicit def unwrap[C <: Component.Field, S, T](
        t: Refined[C, S, T]
      ): T = { t.wrapped }
    }

    // === List ===

    /** Defines the set of components in a graph by assigning a type to the
      * `Out` parameter when implementing the trait.
      *
      * @tparam G the graph for which the components are defined.
      */
    trait List[G <: Graph] {
      type Out <: HList
    }
    object List {
      type Aux[G <: Graph, X] = List[G] { type Out = X }
    }

    // === Field ===

    /** A component can have one of more [[Field]]s.
      *
      * An example would be a `Node` that consists of fields such as `parent`,
      * the link to its parent nodes, and `generationMeta`, some information
      * about how that node was created.
      */
    trait Field
    object Field {

      /** Defines the set of fields for a given kind of component.
        *
        * @tparam G the graph to which the component type [[C]] belongs
        * @tparam C the component type to which the fields in the list belong
        */
      trait List[G <: Graph, C <: Component] { type Out <: HList }
      object List {
        type Aux[G <: Graph, C <: Component, X] = List[G, C] { type Out = X }
      }
    }

    // === Storage ===

    /** Specialized storage for component data.
      *
      * We intentionally do not use [[scala.collection.mutable.ArrayBuffer]] as
      * it cannot be specialised for primitive types. [[Array]], on the other
      * hand, can be.
      */
    final class Storage(elemSize: Int) {
      var length: Int       = 0
      var array: Array[Int] = new Array[Int](length)

      // TODO: Assert that elem size = elemSize
      def push(elem: Array[Int]): Unit = {
        this.array = this.array ++ elem
        this.length += 1
      }
    }

    // === VariantMatcher ===

    /** An utility for generating matches for components containing sum types.
      *
      * This is very important for cases where we want to be able to express sum
      * types on graph components.
      */
    case class VariantMatcher[T <: Component.Field, V](ix: Int) {
      def unapply[G <: Graph, C <: Component](
        arg: Component.Ref[G, C]
      )(
        implicit graph: GraphData[G],
        ev: HasComponentField[G, C, T]
      ): Option[Component.Refined[T, V, Component.Ref[G, C]]] = {
        val variantIndexByteOffset = 0
        if (graph.unsafeReadField[C, T](arg.ix, variantIndexByteOffset) == ix)
          Some(Component.Refined[T, V, Component.Ref[G, C]](arg))
        else None
      }
    }
  }

  // =================
  // === GraphData ===
  // =================

  /** [[GraphData]] is the underlying storage representation used by the
    * [[Graph]]. It contains the raw data for all components.
    *
    * @param info information about the graph's underlying structure
    * @tparam G the graph type that the data is for
    */
  final class GraphData[G <: Graph]()(implicit val info: GraphInfo[G]) {
    var components: Array[Component.Storage] =
      this.componentSizes.map(size => new Component.Storage(size)).to[Array]

    def unsafeGetFieldData[C <: Component, F <: Component.Field](
      componentIx: Int,
      fieldIx: Int
    )(implicit info: HasComponentField[G, C, F]): (Array[Int], Int) = {
      val arr = components(info.componentIndex).array
      val idx = info.componentSize * componentIx + info.fieldOffset + fieldIx
      (arr, idx)
    }

    def unsafeReadField[C <: Component, F <: Component.Field](
      componentIx: Int,
      fieldIx: Int
    )(implicit ev: HasComponentField[G, C, F]): Int = {
      val (arr, idx) = unsafeGetFieldData(componentIx, fieldIx)
      arr(idx)
    }

    def unsafeWriteField[C <: Component, F <: Component.Field](
      componentIx: Int,
      fieldIx: Int,
      value: Int
    )(implicit ev: HasComponentField[G, C, F]): Unit = {
      val (arr, idx) = unsafeGetFieldData(componentIx, fieldIx)
      arr(idx) = value
    }

    def addComponent[C <: Component]()(
      implicit info: HasComponent[G, C]
    ): Component.Ref[G, C] = {
      val compClsIx = info.componentIndex
      val compIx    = components(compClsIx).length
      val data      = new Array[Int](info.componentSize)
      components(compClsIx).push(data)
      Component.Ref(compIx)
    }
  }
  object GraphData {
    implicit def getInfo[G <: Graph](g: GraphData[G]): GraphInfo[G] = g.info
  }

  // ====================
  // === TypeFamilies ===
  // ====================

  // === GraphInfo ===

  /**
    * Information about the number and sizes of components stored in the graph.
    *
    * @tparam G the graph for which this metadata exists
    */
  trait GraphInfo[G <: Graph] {
    val componentCount: Int
    val componentSizes: Vector[Int]
  }
  object GraphInfo {
    implicit def instance[
      G <: Graph,
      ComponentList <: HList,
      ComponentSizeList >: HList,
      ComponentListLength <: Nat
    ](
      implicit
      ev1: Component.List.Aux[G, ComponentList],
      ev2: hlist.Length.Aux[ComponentList, ComponentListLength],
      componentSizesEv: ComponentListToSizes[G, ComponentList],
      len: nat.ToInt[ComponentListLength]
    ): GraphInfo[G] = new GraphInfo[G] {
      val componentCount = len()
      val componentSizes = componentSizesEv.sizes
    }
  }

  // === HasComponent ===

  /**
    * Encodes that a given graph [[G]] has a component with given type [[C]].
    *
    * @tparam G the graph type
    * @tparam C the component type
    */
  trait HasComponent[G <: Graph, C <: Component] {
    val componentIndex: Int
    val componentSize: Int
  }
  object HasComponent {
    implicit def instance[
      G <: Graph,
      C <: Component,
      ComponentList <: HList,
      PrevComponentList <: HList,
      ComponentIndex <: Nat,
      FieldList <: HList
    ](
      implicit
      ev1: Component.List.Aux[G, ComponentList],
      ev2: Component.Field.List.Aux[G, C, FieldList],
      ev3: HListTakeUntil.Aux[C, ComponentList, PrevComponentList],
      ev4: hlist.Length.Aux[PrevComponentList, ComponentIndex],
      componentIndexEv: nat.ToInt[ComponentIndex],
      componentSizeEv: KnownSize[FieldList]
    ): HasComponent[G, C] = new HasComponent[G, C] {
      val componentIndex = componentIndexEv()
      val componentSize  = componentSizeEv.asInt
    }
  }

  // === HasComponentField ===

  /**
    * Encodes that a graph [[G]] has field [[F]] in component [[C]].
    *
    * @tparam G the graph type
    * @tparam C the component type in [[G]]
    * @tparam F the field type in [[C]]
    */
  trait HasComponentField[G <: Graph, C <: Component, F <: Component.Field] {
    val componentIndex: Int
    val componentSize: Int
    val fieldOffset: Int
  }
  object HasComponentField {
    implicit def instance[
      G <: Graph,
      C <: Component,
      F <: Component.Field,
      FieldList <: HList
    ](
      implicit
      ev1: Component.Field.List.Aux[G, C, FieldList],
      evx: HasComponent[G, C],
      fieldOffsetEv: SizeUntil[F, FieldList]
    ): HasComponentField[G, C, F] = new HasComponentField[G, C, F] {
      val componentIndex = evx.componentIndex
      val componentSize  = evx.componentSize
      val fieldOffset    = fieldOffsetEv.asInt
    }
  }

  // === ComponentListToSizes ===

  /**
    * Obtains the sizes of all the components from the graph's list of
    * components.
    *
    * @tparam G the graph
    * @tparam ComponentList the list of components
    */
  trait ComponentListToSizes[G <: Graph, ComponentList <: HList] {
    val sizes: Vector[Int]
  }
  object ComponentListToSizes {
    implicit def onNil[G <: Graph]: ComponentListToSizes[G, HNil] =
      new ComponentListToSizes[G, HNil] { val sizes = Vector[Int]() }

    implicit def onCons[G <: Graph, C <: Component, Tail <: HList](
      implicit
      tail: ComponentListToSizes[G, Tail],
      info: HasComponent[G, C]
    ): ComponentListToSizes[G, C :: Tail] =
      new ComponentListToSizes[G, C :: Tail] {
        val sizes = info.componentSize +: tail.sizes
      }
  }

}
