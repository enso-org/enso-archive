package org.enso.graph

import shapeless.{::, HList, HNil, IsDistinctConstraint}
import shapeless.ops.hlist._

import scala.collection.mutable

trait MapsOfRepro[List <: HList] {
  type Out <: HList
  val instance: Out
}
object MapsOfRepro {
  type Aux[List <: HList, X] = MapsOfRepro[List] { type Out = X }

  def apply[List <: HList](
    implicit ev: MapsOfRepro[List]
  ): MapsOfRepro.Aux[List, ev.Out] = ev

  implicit def onNil: MapsOfRepro.Aux[HNil, HNil] =
    new MapsOfRepro[HNil] {
      type Out = HNil
      val instance = HNil
    }

  implicit def onCons[Head, Tail <: HList](
    implicit ev: MapsOfRepro[Tail],
    distinct: IsDistinctConstraint[Head :: Tail]
  ): MapsOfRepro.Aux[Head :: Tail, mutable.Map[Int, Head] :: ev.Out] =
    new MapsOfRepro[Head :: Tail] {
      type Out = mutable.Map[Int, Head] :: ev.Out
      val instance = mutable.Map[Int, Head]() :: ev.instance
    }

  def getOpaqueData[T, Opaques <: HList](
    list: Opaques
  )(
    implicit ev: Selector[Opaques, mutable.Map[Int, T]]
  ): mutable.Map[Int, T] = {
    list.select[mutable.Map[Int, T]]
  }
}

trait GraphRepro
object GraphRepro {

  trait OpaqueDataRepro
  object OpaqueDataRepro {
    trait List[G <: Graph] {
      type Out <: HList
    }
    object List {
      type Aux[G <: Graph, X] = List[G] { type Out = X }
    }
  }

  final class GraphDataRepro[G <: Graph]()(implicit val info: GraphInfoRepro[G]) {
    type OpaqueTypes = info.OpaqueDataTypes

    //    val opaqueData = MapsOf[OpaqueTypes]()
  }

  trait GraphInfoRepro[G] {
    type OpaqueDataTypes <: HList
  }
  object GraphInfoRepro {
    implicit def instance[
      G <: Graph,
      OpaqueDataList <: HList
    ](
      implicit ev: OpaqueDataRepro.List.Aux[G, OpaqueDataList]
    ): GraphInfoRepro[G] = new GraphInfoRepro[G] {
      override type OpaqueDataTypes = OpaqueDataList
    }
  }
}
