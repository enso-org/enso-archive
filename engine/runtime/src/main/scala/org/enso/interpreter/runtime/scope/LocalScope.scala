package org.enso.interpreter.runtime.scope

import com.oracle.truffle.api.frame.{FrameDescriptor, FrameSlot, FrameSlotKind}
import org.enso.compiler.pass.analyse.AliasAnalysis
import org.enso.compiler.pass.analyse.AliasAnalysis.Graph
import org.enso.compiler.pass.analyse.AliasAnalysis.Graph.{Scope => AliasScope}

import scala.collection.mutable

// TODO [AA] Doc this
class LocalScope(
  val aliasingGraph: AliasAnalysis.Graph,
  val scope: AliasAnalysis.Graph.Scope,
  val frameSlots: mutable.Map[Graph.Id, FrameSlot] = mutable.Map()
) {
  val frameDescriptor: FrameDescriptor = new FrameDescriptor()

  val stateFrameSlot: FrameSlot = frameDescriptor.findOrAddFrameSlot(
    "<<monadic_state>>",
    FrameSlotKind.Object
  )

  def createChild(childScope: AliasScope): LocalScope = {
    new LocalScope(aliasingGraph, childScope, frameSlots)
  }

  def createVarSlot(id: Graph.Id): FrameSlot = {
    val slot = frameDescriptor.addFrameSlot(aliasingGraph.idToSymbol(id))
    frameSlots(id) = slot
    slot
  }

  def getFramePointer(id: Graph.Id): Option[FramePointer] = {
    aliasingGraph.defLinkFor(id).flatMap { link =>
      val slot = frameSlots.get(link.target)

      slot.map(new FramePointer(link.scopeCount, _))
    }
  }
}
