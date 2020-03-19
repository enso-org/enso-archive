package org.enso.interpreter.runtime.scope

import com.oracle.truffle.api.frame.{FrameDescriptor, FrameSlot, FrameSlotKind}
import org.enso.compiler.pass.analyse.AliasAnalysis
import org.enso.compiler.pass.analyse.AliasAnalysis.Graph
import org.enso.compiler.pass.analyse.AliasAnalysis.Graph.{
  Occurrence,
  Scope => AliasScope
}

import scala.collection.mutable
import scala.jdk.CollectionConverters._

/** A representation of an Enso local scope.
  *
  * Enso local scopes may be arbitrarily nested, and are used to maintain a
  * mapping between the interpreter's concept of stack frames and the guest
  * language's concept of stack frames.
  *
  * @param aliasingGraph the graph containing aliasing information for the tree
  *                      of scopes within which this local scope exists
  * @param scope the particular scope in `aliasingGraph` represented by this
  *              [[LocalScope]].
  * @param flattenToParent whether or not the frame should be flattened into its
  *                        parent
  * @param frameSlots a mapping from symbol definition identifiers to slots in
  *                   the Enso frame
  */
class LocalScope(
  val aliasingGraph: AliasAnalysis.Graph,
  val scope: AliasAnalysis.Graph.Scope,
  val flattenToParent: Boolean                     = false,
  val frameSlots: mutable.Map[Graph.Id, FrameSlot] = mutable.Map()
) {
  val frameDescriptor: FrameDescriptor = new FrameDescriptor()

  val stateFrameSlot: FrameSlot = frameDescriptor.findOrAddFrameSlot(
    "<<monadic_state>>",
    FrameSlotKind.Object
  )

  def createChild(): LocalScope = createChild(scope.addChild())

  def createChild(
    childScope: AliasScope,
    flattenToParent: Boolean = false
  ): LocalScope = {
    new LocalScope(aliasingGraph, childScope, flattenToParent, frameSlots)
  }

  def createVarSlot(id: Graph.Id): FrameSlot = {
    val slot = frameDescriptor.addFrameSlot(aliasingGraph.idToSymbol(id))
    frameSlots(id) = slot
    slot
  }

  def getFramePointer(id: Graph.Id): Option[FramePointer] = {
    aliasingGraph.defLinkFor(id).flatMap { link =>
      val slot = frameSlots.get(link.target)
      slot.map(
        new FramePointer(
          if (flattenToParent) link.scopeCount - 1 else link.scopeCount,
          _
        )
      )
    }
  }

  def flattenBindings: java.util.Map[String, FramePointer] =
    flattenBindingsWithLevel(0).asJava

  private def flattenBindingsWithLevel(
    level: Int
  ): Map[Graph.Symbol, FramePointer] = {
    var parentResult: Map[Graph.Symbol, FramePointer] = scope.parent match {
      case Some(parent) =>
        new LocalScope(aliasingGraph, parent, frameSlots = frameSlots)
          .flattenBindingsWithLevel(level + 1)
      case _ => Map()
    }
    scope.occurrences.foreach {
      case x: Occurrence.Def =>
        parentResult += x.symbol -> new FramePointer(
          level,
          frameSlots(x.id)
        )
      case _ =>
    }
    parentResult
  }
}
object LocalScope {

  /** Constructs a local scope for an
    * [[org.enso.interpreter.node.EnsoRootNode]].
    *
    * @return a defaulted local scope
    */
  def root: LocalScope = {
    val graph = new AliasAnalysis.Graph
    new LocalScope(graph, graph.rootScope)
  }
}
