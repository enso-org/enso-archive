package org.enso.syntax.graph

import java.util.UUID

import org.enso.data.List1
import org.enso.syntax.text.AST
import org.enso.syntax.text.AST.Cons
import org.enso.syntax.text.AST.Marker

/** Layer over Double Representation and other backend services. Directly under
  * GUI. */
trait SessionManager {
  import API._

  /** Executes given function in a single transaction.
    *
    * Transactions are atomic -- if any of the actions included failed, the
    * project state is restored to the state from the start of the transaction.
    * Transactions are considered to be a single action by Undo-Redo Manager.
    * There might be only one ongoing transaction at the moment. Any nested
    * transaction will be treated as a part of the parent one.
    *
    * Transactions should be quick. Caller is encouraged to not perform any
    * blocking or time-consuming operations in "f".
    *
    * @param name Optional name for the action performed, to be displayed.
    * @param f Function that will be executed.
    * @return function's result
    *
    */
  def inTransaction[T](name: Option[String] = None)(f: => T): T

  /** Generates a string that denotes the selected nodes.
    *
    * It can be used later when pasting. Only nodes from a single graph may
    * participate in a copy operation.
    *
    * @param context Currently active graph.
    * @param nodes Nodes selected in the active graph.
    * @return a string that can be passed to [[paste]] method later. Likely it
    *         is meant to be stored in the clipboard.
    */
  def copy(context: Node.Context, nodes: List[Node.Id])

  /** Pastes nodes in a given graph location.
    *
    * @param graph Graph where nodes are being pasted.
    * @param copyData Pasted data, e.g. obtained earlier with a [[copy]] method
    * @param position Position where pasted nodes should appear (e.g. mouse
    *                 cursor position).
    */
  def paste(
    graph: Graph.Location,
    copyData: String,
    position: SessionManager.Position
  )
}

object SessionManager {

  /** Position in the graph's rendering space */
  final case class Position(x: Double, y: Double)
}

/** Access to the State Manager -- a component that owns a project state.
  *
  * The state manager is a cooperative, distributed component. Multiple State
  * Managers can be deployed and are expected to converge.
  *
  * */
trait StateManager {
  import API.Module

  /** Lists modules in a project. */
  def availableModules(): Seq[Module.Id]

  /** Obtains the AST for a given [[Module.Id]] */
  def getModule(module: Module.Id): AST.Module

  /** Overwrites module's AST with a new one. */
  def setModule(module: Module.Id, ast: AST.Module): Unit

  // TODO: external update notifications
}

object StateManager {
  case class ModuleNotFoundException(module: API.Module.Id) extends Exception {
    override def getMessage: String =
      s"Module $module cannot be found in the project"
  }
}

trait NotificationSink {
  def retrieve(notification: API.Notification): Unit
}

object API {

  /** Backend always run in the context of single project. From its perspective,
    * the project is eternal.
    **/
  object Project {
    type Context  = Nothing
    type Id       = Unit
    type Location = Id

    // TODO: if we wanted to add some representation of project state, it might
    // go here. or not.
  }

  /** Luna module. Modules are paired with `*.luna` files.
    *
    * Module consists of imports, root graph and definitions.
    *
    * */
  object Module {
    type Name = List1[Cons]

    final case class IllegalModuleNameText(text: String) extends Exception {
      override def getMessage: String =
        s"$text is not a valid module representation: it should be a dot-separated expression"
    }

    object Name {

      /** Construct module name from strings like "Foo.Baz" */
      def apply(text: String): Name = Name(text.split('.'))

      def apply(iterable: Iterable[String]): Name = {
        List1.fromIterableOption(iterable) match {
          case Some(nameParts) =>
            // TODO someone should verify that Cons start upper-cased
            nameParts.map(Cons(_))
          case None =>
            throw IllegalModuleNameText(iterable.toString())
        }
      }
    }

    type Context  = Project.Location
    type Id       = Name
    type Location = Id

    /** Module's root-level graph. It has no inputs nor outputs. */
    object Graph {
      val Graph: API.Graph.type = API.Graph
      final case class Id(id: Module.Id)                 extends Graph.Id
      final case class Context(id: Module.Context)       extends Graph.Context
      final case class Location(module: Module.Location) extends Graph.Location

      final case class Info(
        nodes: Seq[API.Node.Info],
        links: Seq[API.Connection]
      ) extends API.Graph.Info
    }
  }

  object AST {

    /** Uniquely identifies a single node in AST.
      *
      * All entities that can be entered, i.e. that can be accessed through the
      * graph API, like function definitions and lambdas, are required to bear
      * an Id. This should be ensured before DR even gets the project state for
      * the first time. If Ids are not present in the file, they should be added
      * automatically right after the parsing.
      */
    type Id = UUID
  }

  // TODO: this is about graph-having defintions (functions, perhaps vars)
  // what about other kinds of "definitions"? TODO unify vocabulary
  object Definition {
    type Context = Either[Module.Location, Definition.Location]
    type Id      = AST.Id
    final case class Location(context: Context, id: Id)

    final case class Info(name: String, id: Id)

    /** Definition's graph. It has output and may have inputs. */
    object Graph {
      final case class Info(
        nodes: Seq[Node.Info],
        links: Seq[Connection],
        inputs: Seq[API.Graph.Input.Info],
        output: API.Graph.Output.Info
      ) extends API.Graph.Info

      val Graph: API.Graph.type = API.Graph
      final case class Id(id: Definition.Id)             extends Graph.Id
      final case class Context(id: Definition.Context)   extends Graph.Context
      final case class Location(id: Definition.Location) extends Graph.Location
    }
  }

  /** Graph describes a [[Definition]] body or a [[Module]]'s root body.
    *
    * Please see [[Definition.Graph]] and [[Module.Graph]] for types subclassing
    * the traits defined here.
    */
  object Graph {
    sealed trait Id
    sealed trait Context
    sealed trait Location

    sealed trait Info {
      def nodes: Seq[Node.Info]
      def links: Seq[Connection]
    }

    /** Class of ports being graph inputs and outputs. */
    object Port {
      type Id      = API.Port.Id
      type Context = API.Port.GraphSocket
      final case class Location(context: Context, id: Id)
      type Info = API.Port.Info
    }
    object Output {
      type Id      = API.Port.OutputPath
      type Context = Port.Context
      final case class Location(context: Context, id: Id)
      type Info = Port.Info
    }
    object Input {
      type Id      = API.Port.InputPath
      type Context = Port.Context
      final case class Location(context: Context, id: Id)
      type Info = Port.Info
    }
  }

  object Node {
    type Id      = AST.Id
    type Context = Graph.Location
    final case class Location(context: Context, node: Id)

    final case class Metadata(visibleParts: TODO)
    final case class Stats() // progress, debug, profiling - TODO
    final case class Info(
      id: Id,
      expr: Expr,
      inputs: Seq[Port.Info],
      output: Port.Info,
      flags: Set[Flag],
      stats: Option[Stats],
      marker: Marker
    )
  }

  //
  trait TODO

  final case class Type(tp: TODO)

  final case class SpanTree() extends TODO {}

  // TODO [MWU] as discussed, most likely we should pass the whole AST
  final case class Expr(text: String, spanTree: SpanTree)

  sealed trait Port
  object Port {
    sealed trait Id
    sealed case class InputPath(path: List1[Int]) extends Id
    sealed case class OutputPath(path: List[Int]) extends Id

    sealed trait Context
    final case class NodeSocket(node: Node.Location)    extends Context
    final case class GraphSocket(graph: Graph.Location) extends Context
    final case class Location(context: Context, id: Id)

    final case class Info(
      tp: Option[Type],
      name: Option[String],
      children: Seq[Info]
    )

    object Info {
      def apply():                    Info = Info(None, None, Seq())
      def apply(children: Seq[Info]): Info = Info(None, None, children)
    }

    /**  port that we don't really know anything about */
    def Empty = Info(None, None, Seq())
    def Empty(n: Int): Seq[Info] = Seq.fill(n)(Port.Empty)
  }

  /** A port that produces values ("output", "source"). */
  object Output extends Port {
    type Id      = Port.OutputPath
    type Context = Port.Context
    final case class Location(context: Context, id: Id)
  }

  /** A port that consumes values ("input", "sink") */
  object Input extends Port {
    type Id      = Port.InputPath
    type Context = Port.Context
    final case class Location(context: Context, id: Id)
  }

  sealed trait Flag
  object Flag {
    final case object Skip   extends Flag
    final case object Freeze extends Flag
  }

  case class Connection(src: Output.Location, tgt: Input.Location)

  /** Each change to the project state shall be covered by a Notification. */
  sealed trait Notification
  object Notification {

    /** Node-related updates */
    sealed trait Node extends Notification
    object Node {
      import API.Node._
      case class Added(ctx: Context, node: Info) extends Node
      case class Removed(loc: Location)          extends Node
      sealed trait Changed                       extends Node
      object Changed {
        case class Expression(node: Location, newExpr: String) extends Node
        case class Inputs(node: API.Input.Context, inputs: List[Port.Info])
            extends Node
        case class Output(node: Location, output: Port.Info)   extends Node
        case class Metadata(loc: Location, metadata: Metadata) extends Node
      }

      sealed trait Flag extends Node
      object Flag {
        case class Enabled(node: Location, flag: API.Flag)  extends Flag
        case class Disabled(node: Location, flag: API.Flag) extends Flag
      }
    }

    /** Connection-related updates */
    sealed trait Connection extends Notification
    object Connection {
      case class Added(connection: API.Connection)   extends Connection
      case class Removed(connection: API.Connection) extends Connection
    }

    /** Invalidations
      *
      * Invalidation happens when given entity information might have been
      * changed, however more specific extent of change is unknown.
      */
    sealed trait Invalidate extends Notification
    object Invalidate {
      case class Node(node: API.Node.Location)       extends Invalidate
      case class Graph(graph: API.Graph.Location)    extends Invalidate
      case class Module(module: API.Module.Location) extends Invalidate
      case class Project()                           extends Invalidate
    }

    case class TextInserted(
      module: Module.Location,
      position: TextPosition,
      text: String
    )
    case class TextErased(module: Module.Location, span: TextSpan)

    ////////////////////////////////////////////////////////////////////////////
  }

  case class TextPosition(index: Int)
  case class TextSpan(start: TextPosition, length: Int)

  /***** Exceptions */
  final case class ImportAlreadyExistsException(name: Module.Name)
      extends Exception {
    override def getMessage: String = s"Module $name is already imported"
  }
  final case class NoSuchImportException(name: Module.Name) extends Exception {
    override def getMessage: String = s"Module $name is not imported"
  }
}

trait TextAPI {
  import API._

  // view
  def getText(loc: Module.Location): String

  // modify
  def insertText(loc: Module.Location, cursor: TextPosition, text: String): Unit
  def eraseText(loc: Module.Location, span: TextSpan): Unit
  def copyText(loc: Module.Location, span: TextSpan): String
  def pasteText(loc: Module.Location, cursor: TextPosition, text: String): Unit // FIXME We can get both plain text or metadata from graph

  // TODO should we represent here that text notifications are emitted?
}

trait GraphAPI {
  import API._

  /////////////////////////////////////////////////////////////////////////////
  def getGraph(loc: Graph.Location): Graph.Info = loc match {
    case ctx @ Definition.Graph.Location(_) => getGraph(ctx)
    case ctx @ Module.Graph.Location(_)     => getGraph(ctx)
  }
  def getGraph(loc: Definition.Graph.Location): Definition.Graph.Info
  def getGraph(loc: Module.Graph.Location): Module.Graph.Info
  def getDefinitions(loc: Module.Location): Seq[Definition.Info]
  // TODO other entities? (visible through Graph API)
  /** Manage Imports */
  def importedModules(module: Module.Location): Seq[Module.Name]
  def importModule(context: Module.Id, importee: Module.Name): Unit
  def removeImport(context: Module.Id, importToRemove: Module.Name): Unit
  //////////////////////////////////////////////////////////////////////////////
  def addNode(
    context: Node.Context,
    metadata: Node.Metadata,
    expr: String
  ): Node.Id
  def setMetadata(node: Node.Location, newMetadata: Node.Metadata)
  def enableFlag(node: Node.Location, flag: Flag)
  def disableFlag(node: Node.Location, flag: Flag)
  def setExpression(node: Node.Location, expression: String)
  def removeNode(node: Node.Location)
  def extractToFunction(
    context: Node.Context,
    node: Set[Node.Id]
  ): Definition.Id

  //////////////////////////////////////////////////////////////////////////////
  def setPortName(port: Graph.Port.Location, name: String)
  def addPort(port: Graph.Port.Location, name: String, tp: Option[Type]) // TODO how to handle type
  def removePort(port: Graph.Port.Location)
  //////////////////////////////////////////////////////////////////////////////
  // TODO connected node may be unnamed and then name needs to be generated
  //      preferably generated name should use information from interpreter
  //      (like the resolved function definition) so we should either take here
  //      optional name or optional name generator
  def addConnection(graph: Port.Context, from: Output.Id, to: Input.Id)
  def removeConnection(graph: Port.Context, from: Output.Id, to: Input.Id)
}
