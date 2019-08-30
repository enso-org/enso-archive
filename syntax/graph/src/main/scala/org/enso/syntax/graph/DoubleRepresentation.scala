package org.enso.syntax.graph

import java.awt.Toolkit
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.ClipboardOwner
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.util.UUID

import org.enso.data.List1
import org.enso.data.Shifted
import org.enso.syntax.graph.API._
import org.enso.data.List1._
import org.enso.flexer.Parser.Result
import org.enso.flexer.Reader
import org.enso.syntax.text.AST
import org.enso.syntax.text.AST.Import
import org.enso.syntax.text.AST.SAST
import org.enso.syntax.text.Parser
import org.enso.syntax.text.AST.App.Infix
import org.enso.syntax.text.AST.Block.Line
import org.enso.syntax.text.AST.Macro.Match.Segment
import org.enso.syntax.text.ast.meta
import org.enso.syntax.text.ast.meta.Pattern

import scala.annotation.tailrec
import scala.reflect.ClassTag

object KnownOperators {
  val Assignment = AST.Opr("=")
  val Minus      = AST.Opr("-")
}

object Extensions {
  implicit class Opr_ops(opr: AST.Opr) {
    def isAssignment: Boolean = opr == KnownOperators.Assignment
  }

  implicit class Ast_ops(ast: AST) {
    def as[T: ClassTag]: Option[T] = {
      val tag = implicitly[ClassTag[T]]
      ast match {
        case tag(t) => Some(t)
        case _      => None
      }
    }

    // TODO: name should be more different from as[Import]
    def asImport: Option[AST.Import] =
      as[Import].orElse(as[AST.Macro.Match].flatMap(_.resolved.asImport))

    def asAssignment: Option[AST.App.Infix] =
      ast.as[AST.App.Infix].filter(_.opr.isAssignment)

    def getName: Option[String] = ast.as[AST.Var].map(_.name)

    /** ID is required for all definitions (enterable functions) and nodes. */
    def requiresId: Boolean = {
      ast match {
        case _: AST.Var       => true
        case _: AST.App       => true
        case _: AST.App.Left  => true
        case _: AST.App.Right => true
        case _: AST.App.Infix => true
        case _: AST.App.Sides => true
        case _: AST.Number    => true
        case _                => false
      }
    }

    def imports(module: Module.Name): Boolean = {
      ast.asImport.exists(_.path == module)
    }

    def asNode: Option[Node.Info] = ast.as[AST.Marked].flatMap { marked =>
      val nodeAst = marked.ast
      val (lhs, rhs) = nodeAst.asAssignment match {
        case Some(Infix(l, _, r)) => (Some(l), r)
        case None                 => (None, nodeAst)
      }

      val assignment = lhs.map(AssignmentInfo(_, rhs))

      // definition with inputs is a function
      // and a function is not a node
      if (assignment.exists(_.inputAsts.nonEmpty))
        return None

      val id       = marked.marker.id
      val spanTree = API.SpanTree() // TODO
      val expr     = Expr(rhs.show, spanTree)

      val inputAsts = rhs.groupTopInputs
      // TODO subports
      val inputs           = inputAsts.map(_.asPort)
      val outputName       = assignment.flatMap(_.name)
      val output           = Port.Info(None, outputName, Seq())
      val flags: Set[Flag] = Set.empty // TODO
      val stats            = None
      val metadata         = marked.marker

      val node = Node.Info(id, expr, inputs, output, flags, stats, metadata)
      Some(node)
    }

    def flattenApps: List1[AST] = ast match {
      // TODO: provisionally deal with macros resolving to Group, like parens,
      //       as if code was directly grouped
      case AST.Macro.Match(_, _, _, group @ AST.Group(body)) =>
        body match {
          case Some(groupedAst) => groupedAst.flattenApps
          case _                => List1(group)
        }
//      case AST.Macro.Match(
//          marker,
//          None,
//          Shifted.List1(
//            AST.Macro.Match
//              .Segment(AST.Opr("("), mmm),
//            Shifted(
//              _,
//              AST.Macro.Match
//                .Segment(
//                  AST.Opr(")"),
//                  Pattern.Match.Of(aa, ee)
//                )
//            ) :: Nil
//          ),
//          res
//          ) => {
//        mmm match {
//          case Pattern.Match.Of(Pattern.Build(ppp), el) => {
//            println(ppp.toString + el.toString)
//            null
//          }
//        }
//        null
//      }
      // lhs rhs
      case AST.App(lhs, rhs) => lhs.flattenApps :+ rhs
      case nonAppAst         => List1(nonAppAst)
    }

    def groupTopInputs: Seq[AST] = ast match {
      case _: AST.App       => ast.flattenApps.tail
      case _: AST.App.Sides => Seq(AST.Blank, AST.Blank)
      // TODO special case below for unary minus, until parser handles it
      case AST.App.Right(KnownOperators.Minus, rhs) => Seq(rhs)
      case AST.App.Right(_, rhs)                    => Seq(AST.Blank, rhs)
      case AST.App.Left(lhs, _)                     => Seq(lhs, AST.Blank)
      case AST.App.Infix(lhs, _, rhs)               => Seq(lhs, rhs)
//      case _: AST.Var                 => Seq()
//      case _: AST.Literal             => Seq()
//      case _: AST.Number              => Seq()
      case _ => Seq()
    }

    def asPort: Port.Info = ast match {
      case _ => Port.Empty
    }
  }

  implicit class Line_ops(line: Line) {
//    def asImport: Option[Import] = line.elem.flatMap(_.as[Import])
//    def imports(module: Module.Name): Boolean =
//      line.elem.exists(_.imports(module))
    // def asAssignment: Option[Infix]     = line.elem.flatMap(_.asAssignment)
    // def asNode: Option[Node.Info] = line.elem.flatMap(_.asNode)
  }

  implicit class Module_ops(module: AST.Module) {
    //def importedModules: List[Module.Name] = module.imports.map(_.path)
    def imports:               List[Import] = module.flatTraverse(_.asImport)
    def lineIndexOf(ast: AST): Option[Int]  = lineIndexWhere(_ == ast)
    def lineIndexWhere(p: AST => Boolean): Option[Int] = {
      module.lines.indexWhere(_.elem.exists(p))
    }
  }
}

// TODO ugly?
import Extensions._

case class AssignmentInfo(lhs: AST, rhs: AST) {
  val lhsParts: List1[AST]            = lhs.flattenApps
  val name: Option[String]            = lhsParts.head.getName
  val inputAsts: Seq[AST]             = lhsParts.tail
  val inputNames: Seq[Option[String]] = inputAsts.map(_.getName)
}
//object AssignmentInfo {
//  def apply(ast: AST.App.Infix): Option[AssignmentInfo] =
//    if (ast.opr.name == "=") Some(AssignmentInfo(ast.larg, ast.rarg))
//    else None
//  def apply(ast: AST): Option[DefInfo] =
//    ast.toAssignment.flatMap(DefInfo(_))
//}

object ParserUtils {
  def expectAst(result: Parser.Result[AST.Module]): AST.Module = {
    result match {
      case Result(_, Result.Success(ret)) => ret
      case _ =>
        throw new Exception("Parsing failed: " + result.toString)
    }
  }
  def prettyPrint(ast: AST.Module): Unit = {
    println("------")
    println(org.enso.syntax.text.Main.pretty(ast.toString))
    println("------")
    println(ast.show)
  }
  def parse(program: String, markers: Parser.Markers = Seq()): AST.Module = {
    val parser = new Parser()
    val result = parser.run(new Reader(program), markers)
    val ast    = expectAst(result)

    var counter = 0
    ast.map(astNode => {
      // unmarked node asts should get their markers
      if (astNode.requiresId) {
        val markedAst = AST.Marked(AST.Marker(counter), astNode)
        counter += 1
        markedAst
      } else
        astNode
    })
  }
}

final case class DoubleRepresentation(
  state: StateManager,
  notifier: NotificationSink
) extends GraphAPI
    with TextAPI {

  protected def findAndReplace(loc: Module.Location, pos: TextPosition)(
    fun: (TextPosition, AST.Block.Line) => List[AST.Block.Line]
  ) = {
    var span = 0
    val module = state.getModule(loc).findAndReplace { line =>
      span += line.span
      if (span < pos.index) None
      else Some(fun(TextPosition(span - line.span), line))
    }
    state.setModule(loc, module)
  }

  def getText(loc: Module.Location): String = state.getModule(loc).show

  def insertText(loc: Module.Location, cursor: TextPosition, text: String) =
    findAndReplace(loc, cursor) { (pos, line) =>
      val (prefix, suffix) = line.show.splitAt(pos.index + cursor.index)
      val result           = Parser().run(new Reader(prefix + text + suffix))
      result.unwrap.lines.toList
    }

  def eraseText(loc: Module.Location, span: TextSpan) =
    findAndReplace(loc, span.start) { (pos, line) =>
      val (line1, line2) = line.show.splitAt(pos.index + span.start.index)
      val result         = Parser().run(new Reader(line1 + line2.drop(span.length)))
      result.unwrap.lines.toList
    }

  def copyText(loc: Module.Location, span: TextSpan): String = {
    var text = ""
    findAndReplace(loc, span.start) { (pos, line) =>
      val start = pos.index + span.start.index
      text = line.show.substring(start, start + span.length)
      List(line)
    }
    text
  }

  def pasteText(loc: Module.Location, cursor: TextPosition, clipboard: String) =
    insertText(loc, cursor, clipboard)

  def getGraph(loc: API.Definition.Graph.Location): Definition.Graph.Info = ???
  def getGraph(loc: Module.Graph.Location): Module.Graph.Info = {
    val ast   = state.getModule(loc.module)
    val nodes = ast.flatTraverse(_.asNode)
    Module.Graph.Info(nodes, Seq())
  }

  def getDefinitions(loc: Module.Location): List[Definition.Info] = ???
  def addNode(
    context: Node.Context,
    metadata: Node.Metadata,
    expr: String
  ): Node.Id = ???
  def setMetadata(node: Node.Location, newMetadata: Node.Metadata) = ???
  def enableFlag(node: Node.Location, flag: Flag)                  = ???
  def disableFlag(node: Node.Location, flag: Flag)                 = ???
  def setExpression(node: Node.Location, expression: String)       = ???
  def removeNode(node: Node.Location)                              = ???
  def extractToFunction(
    context: Node.Context,
    node: Set[Node.Id]
  ): Definition.Id = ???
  def setPortName(port: Graph.Port.Location, name: String)                 = ???
  def addPort(port: Graph.Port.Location, name: String, tp: Option[Type])   = ???
  def removePort(port: Graph.Port.Location)                                = ???
  def addConnection(graph: Port.Context, from: Output.Id, to: Input.Id)    = ???
  def removeConnection(graph: Port.Context, from: Output.Id, to: Input.Id) = ???

  override def importedModules(module: Module.Location): Seq[Module.Name] = {
    val ast = state.getModule(module)
    ast.imports.map(_.path)
  }

  override def importModule(context: Module.Id, importee: Module.Name): Unit = {
    val module         = state.getModule(context)
    val currentImports = module.imports
    if (currentImports.exists(_ imports importee))
      throw ImportAlreadyExistsException(importee)

    // TODO perhaps here zippers could be useful?

    val lastImportPosition = currentImports.lastOption.flatMap(
      lastImport => module.lineIndexWhere(_.imports(lastImport.path))
    )
    val lineToPlaceImport = lastImportPosition match {
      case Some(lastImportLineNumber) => lastImportLineNumber + 1
      case None                       => 0
    }

    val newAst = module.insert(lineToPlaceImport, Import(importee))
    state.setModule(context, newAst)
    notifier.retrieve(API.Notification.Invalidate.Module(context))
  }

  override def removeImport(
    context: Module.Id,
    importToRemove: Module.Name
  ): Unit = {
    val ast = state.getModule(context)
    val lineIndex = ast.lineIndexWhere(_ imports importToRemove) match {
      case Some(index) => index
      case None        => throw NoSuchImportException(importToRemove)
    }

    val newAst = ast.removeAt(lineIndex)
    state.setModule(context, newAst)
    notifier.retrieve(API.Notification.Invalidate.Module(context))
  }
}
