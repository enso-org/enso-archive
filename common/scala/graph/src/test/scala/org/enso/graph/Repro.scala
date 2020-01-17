package org.enso.graph

import shapeless.{::, HList, HNil, IsDistinctConstraint}
import shapeless.ops.hlist._

import scala.collection.mutable

trait MapsOf[List <: HList] {
  type Out <: HList
  val instance: Out
}
object MapsOf {
  type Aux[List <: HList, X] = MapsOf[List] { type Out = X }

  def apply[List <: HList](
    implicit ev: MapsOf[List]
  ): MapsOf.Aux[List, ev.Out] = ev

  implicit def onNil: MapsOf.Aux[HNil, HNil] =
    new MapsOf[HNil] {
      type Out = HNil
      val instance = HNil
    }

  implicit def onCons[Head, Tail <: HList](
    implicit ev: MapsOf[Tail],
    distinct: IsDistinctConstraint[Head :: Tail]
  ): MapsOf.Aux[Head :: Tail, mutable.Map[Int, Head] :: ev.Out] =
    new MapsOf[Head :: Tail] {
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

trait Graph
object Graph {

  trait OpaqueData
  object OpaqueData {
    trait List[G <: Graph] {
      type Out <: HList
    }
    object List {
      type Aux[G <: Graph, X] = List[G] { type Out = X }
    }
  }

  final class GraphData[G <: Graph]()(implicit val info: GraphInfo[G]) {
    type OpaqueTypes = info.OpaqueDataTypes

//    val opaqueData = MapsOf[OpaqueTypes]()
  }

  trait GraphInfo[G] {
    type OpaqueDataTypes <: HList
  }
  object GraphInfo {
    implicit def instance[
      G <: Graph,
      OpaqueDataList <: HList
    ](
      implicit ev: OpaqueData.List.Aux[G, OpaqueDataList]
    ): GraphInfo[G] = new GraphInfo[G] {
      override type OpaqueDataTypes = OpaqueDataList
    }
  }
}
