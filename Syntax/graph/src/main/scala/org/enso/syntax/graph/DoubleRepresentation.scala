package org.enso.syntax.graph

import org.enso.data.List1
import org.enso.data.List1._
import org.enso.flexer.Reader
import org.enso.syntax.graph.API.Text
import org.enso.syntax.graph.API._
import org.enso.syntax.text.AST.App.Infix
import org.enso.syntax.text.AST.Block.Line
import org.enso.syntax.text.AST.Block.OptLine
import org.enso.syntax.text.AST.Import
import org.enso.syntax.text.AST.UnapplyByType
import org.enso.syntax.text.AST
import org.enso.syntax.text.Parser
import org.enso.syntax.text.ast.Repr._

import scala.collection.GenTraversableOnce

case class MissingIdException(ast: AST) extends Exception {
  override def getMessage: String =
    s"missing id for node with expression `${ast.show}`"
}

object KnownOperators {
  val Assignment = AST.Opr("=")
  val Minus      = AST.Opr("-")
}

object Extensions {
  implicit class Opr_ops(opr: AST.Opr) {
    def isAssignment: Boolean = opr == KnownOperators.Assignment
  }

  implicit class Ast_ops(ast: AST) {
    def getId: AST.ID =
      ast.id.getOrElse(throw MissingIdException(ast))

    def as[T: UnapplyByType]: Option[T] = UnapplyByType[T].unapply(ast)

    // TODO: name should be more different from as[Import]
    def asImport: Option[AST.Import] =
      as[Import].orElse(as[AST.Macro.Match].flatMap(_.resolved.asImport))

    def asAssignment: Option[AST.App.Infix] =
      ast.as[AST.App.Infix].filter(_.opr.isAssignment)

    def getName: Option[String] = ast.as[AST.Var].map(_.name)

    def imports(module: Module.Name): Boolean = {
      ast.asImport.exists(_.path sameTarget module)
    }

    def asNode: Option[Node.Info] = {
      val (lhs, rhs) = ast.asAssignment match {
        case Some(Infix(l, _, r)) => (Some(l), r)
        case None                 => (None, ast)
      }

      val assignment = lhs.map(AssignmentInfo(_, rhs))

      // definition with inputs is a function
      // and a function is not a node
      if (assignment.exists(_.inputAsts.nonEmpty))
        return None

      val id       = ast.getId
      val spanTree = API.SpanTree() // TODO
      val expr     = Expr(rhs.show, spanTree)

      val inputAsts = rhs.groupTopInputs
      // TODO subports
      val inputs           = inputAsts.map(_.asPort)
      val outputName       = assignment.flatMap(_.name)
      val output           = Port.Info(None, outputName, Seq())
      val flags: Set[Flag] = Set.empty // TODO
      val stats            = None
      val metadata         = null // TODO

      val node = Node.Info(id, expr, inputs, output, flags, stats, metadata)
      Some(node)
    }

    def flattenApps: List1[AST] = ast match {
      // TODO: provisionally deal with macros resolving to Group, like parens,
      //       as if code was directly grouped
      case AST.Macro.Match(_, _, group @ AST.Group(body)) =>
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
      case AST.App.Prefix(lhs, rhs) => lhs.flattenApps :+ rhs
      case nonAppAst                => List1(nonAppAst)
    }

    def groupTopInputs: Seq[AST] = ast match {
      case AST.App.Prefix.any(ast)      => ast.flattenApps.tail
      case AST.App.Section.Sides.any(_) => Seq(AST.Blank(), AST.Blank())
      // TODO special case below for unary minus, until parser handles it
      case AST.App.Section.Right(KnownOperators.Minus, rhs) => Seq(rhs)
      case AST.App.Section.Right(_, rhs)                    => Seq(AST.Blank(), rhs)
      case AST.App.Section.Left(lhs, _)                     => Seq(lhs, AST.Blank())
      case AST.App.Infix(lhs, _, rhs)                       => Seq(lhs, rhs)
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

  implicit class ModuleName_ops(moduleName: API.Module.Name) {
    def nameAsString(): String = moduleName.toList.map(_.name).mkString(".")
    def sameTarget(rhs: API.Module.Name): Boolean =
      moduleName.map(_.shape) == rhs.map(_.shape)
  }

  implicit class Module_ops(module: AST.Module) {
    //def importedModules: List[Module.Name] = module.imports.map(_.path)
    def imports:               List[Import] = module.flatTraverse(_.asImport)
    def lineIndexOf(ast: AST): Option[Int]  = lineIndexWhere(_ == ast)
    def lineIndexWhere(p: AST => Boolean): Option[Int] = {
      module.lines.indexWhere(_.elem.exists(p))
    }
    def lineOffset(lineIx: Int): Int =
      module.lines.toList.take(lineIx).foldLeft(0) {
        case (offset, line) => offset + line.span
      }

    /** flatMaps non-empty lines ASTs. */
    def flatTraverse[B](f: AST => GenTraversableOnce[B]): List[B] =
      module.lines.toList.flatMap(_.elem).flatMap(f(_))

    def insert(index: Int, addedLine: OptLine): AST.Module = {
      val moduleIsEmpty = module.lines.size == 1 && module.lines.head.elem.isEmpty
      val newLines =
        if (moduleIsEmpty) List1(addedLine)
        else module.lines.insert(index, addedLine)
      AST.Module(newLines)
    }
    def insert(index: Int, addedLineAst: AST): AST.Module =
      insert(index, OptLine(addedLineAst))

    def removeAt(index: Int): AST.Module = {
      val newLines = List1(module.lines.removeAt(index)) match {
        case Some(list1) => list1
        // if we removed the last line, restore an empty one
        case None => List1(Line(None))
      }
      AST.Module(newLines)
    }

    /** Calls function f on each line,
      *   - if f returns None, line is kept as it was,
      *   - if f returns Some, line is replaced with f
      *        and all further lines are kept as they were
      */
    def findAndReplace(f: OptLine => Option[List[OptLine]]): AST.Module = {
      def go(lines: List[OptLine]): List[OptLine] = lines match {
        case Nil => Nil
        case l +: ls =>
          f(l) match {
            case None        => l +: go(ls)
            case Some(lines) => lines ++ ls
          }
      }
      AST.Module(List1(go(module.lines.toList)).get)
    }
  }
}

// TODO ugly?
import org.enso.syntax.graph.Extensions._

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
  def prettyPrint(ast: AST.Module): Unit = {
    println("------")
    println(org.enso.syntax.text.Main.pretty(ast.toString))
    println("------")
    println(ast.show)
  }
  def parse(program: String): AST.Module = {
    val parser = new Parser()
    val ast    = parser.run(new Reader(program))

//    var counter = 0
//    ast.map(astNode => {
//      // unmarked node asts should get their markers
//      if (astNode.requiresId) {
//        val markedAst = AST.Marked(AST.Marker(counter), astNode)
//        counter += 1
//        markedAst
//      } else
//        astNode
//    })
    ast
  }
}

final case class DoubleRepresentation(
  state: StateManager,
  notifier: NotificationSink
) extends GraphAPI
    with TextAPI {

  protected def findAndReplace(module: Module.Location, at: Text.Position)(
    fun: (Text.Position, AST.Block.OptLine) => List[AST.Block.OptLine]
  ) = {
    var span = 0
    val content = state.getModule(module).findAndReplace { line =>
      span += line.span
      if (span < at.index) None
      else Some(fun(Text.Position(span - line.span), line))
    }
    state.setModule(module, content)
  }

  def getText(module: Module.Location): String = state.getModule(module).show

  def insertText(module: Module.Location, at: Text.Position, text: String) = {
    findAndReplace(module, at) { (pos, line) =>
      val (prefix, suffix) = line.show.splitAt(pos.index + at.index)
      val result           = Parser().run(new Reader(prefix + text + suffix))
      result.lines.toList
    }
    notifier.notify(API.Notification.Text.Inserted(module, at, text))
    notifier.notify(API.Notification.Graph.Invalidate.Module(module))
  }

  def eraseText(module: Module.Location, span: Text.Span) = {
    findAndReplace(module, span.start) { (pos, line) =>
      val (line1, line2) = line.show.splitAt(pos.index + span.start.index)
      val result         = Parser().run(new Reader(line1 + line2.drop(span.length)))
      result.lines.toList
    }
    notifier.notify(API.Notification.Text.Erased(module, span))
    notifier.notify(API.Notification.Graph.Invalidate.Module(module))
  }

  def copyText(module: Module.Location, span: Text.Span): String = {
    var text = ""
    findAndReplace(module, span.start) { (pos, line) =>
      val start = pos.index + span.start.index
      text = line.show.substring(start, start + span.length)
      List(line)
    }
    text
  }

  def pasteText(module: Module.Location, at: Text.Position, clipboard: String) =
    insertText(module, at, clipboard)

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
    val ast     = state.getModule(module)
    val imports = ast.imports
    val paths   = imports.map(_.path)
    paths
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
    notifier.notify(API.Notification.Graph.Invalidate.Module(context))

    val text   = "import " + API.Module.Name(importee)
    val offset = Text.Position(module.lineOffset(lineToPlaceImport))
    notifier.notify(API.Notification.Text.Inserted(context, offset, text))
  }

  override def removeImport(
    context: Module.Id,
    importToRemove: Module.Name
  ): Unit = {
    val module = state.getModule(context)
    val lineIndex = module.lineIndexWhere(_ imports importToRemove) match {
      case Some(index) => index
      case None        => throw NoSuchImportException(importToRemove)
    }

    val newAst = module.removeAt(lineIndex)
    state.setModule(context, newAst)
    notifier.notify(API.Notification.Graph.Invalidate.Module(context))

    val offset = Text.Position(module.lineOffset(lineIndex))
    val span   = Text.Span(offset, module.lines.toList(lineIndex).span)
    notifier.notify(API.Notification.Text.Erased(context, span))
  }
}
