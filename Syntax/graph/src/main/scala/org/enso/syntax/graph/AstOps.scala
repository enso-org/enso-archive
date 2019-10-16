package org.enso.syntax.graph

import org.enso.data.List1
import org.enso.data.Shifted
import org.enso.data.List1.List1_ops
import org.enso.syntax.graph.API.Module
import org.enso.syntax.text.AST
import org.enso.syntax.text.AST.App
import org.enso.syntax.text.AST.Import
import org.enso.syntax.text.AST.SAST
import org.enso.syntax.text.AST.UnapplyByType
import org.enso.syntax.text.AST.Block.Line
import org.enso.syntax.text.AST.Block.OptLine
import org.enso.syntax.text.ast.opr.Assoc
import org.enso.syntax.text.ast.Repr._

import scala.collection.GenTraversableOnce

/** Operators with special meaning that we support. */
object KnownOperators {
  val Assignment = "="
  val Access     = "."
  val Minus      = "-"
}

/////////////////
//// AST ops ////
/////////////////

object AstOps {
  implicit class AstOps_syntax_graph(ast: AST) {
    ////////////////////////////////////////////////////////
    ///// Parts to be moved to AST operations from here ////
    ////////////////////////////////////////////////////////
    case class MissingIdException(ast: AST) extends Exception {
      override def getMessage: String =
        s"missing id for node with expression `${ast.show()}`"
    }

    /** Gets [[AST.ID]] from this AST, throwing [[MissingIdException]] if ID
      * has not been set. */
    def unsafeID: AST.ID = ast.id.getOrElse(throw MissingIdException(ast))

    def as[T: UnapplyByType]: Option[T] = UnapplyByType[T].unapply(ast)
    def is[T: UnapplyByType]: Boolean   = ast.as[T].isDefined

    //////////////////////////////////////
    //// parts specific to double rep ////
    //////////////////////////////////////

    def toImport: Option[AST.Import] =
      // FIXME [mwu] I doubt we can use here resolved macro
      as[Import].orElse(as[AST.Macro.Match].flatMap(_.resolved.toImport))

    def toAssignment: Option[AST.App.Infix] =
      ast.as[AST.App.Infix].filter(_.opr.isAssignment)

    /** If this AST is a [[AST.Var]], retrieves its name. */
    def toVarName: Option[String] = ast.as[AST.Var].map(_.name)

    /** Checks if this AST imports a given module. */
    def doesImport(module: Module.Name): Boolean = {
      ast.toImport.exists(_.path == module)
    }
  }

  implicit class Prefix_ops(ast: App.Prefix) {

    /** Linearizes nested sequence of [[AST.App.Prefix]].
      *
      * For example structures for code like `foo a b c` will be flattened to
      * sequence like Seq(foo, a, b, c).
      */
    def flattenPrefix(pos: TextPosition): Seq[Positioned[AST]] = {
      val init = ast.fn match {
        case AST.App.Prefix.any(lhsApp) => lhsApp.flattenPrefix(pos)
        case nonAppAst                  => Seq(Positioned(nonAppAst, pos))
      }
      val rhsPos = pos + ast.fn.span + ast.off
      val last   = Positioned(ast.arg, rhsPos)
      init :+ last
    }
  }

  ////////////////////////////////////////////////////////
  ///// Parts to be moved to AST operations from here ////
  ////////////////////////////////////////////////////////

  implicit class Opr_ops(opr: AST.Opr) {
    def isAssignment: Boolean = opr.name == KnownOperators.Assignment
    def isAccess: Boolean     = opr.name == KnownOperators.Access
    def isMinus: Boolean      = opr.name == KnownOperators.Minus
  }

  /////////////////////////
  //// Module name ops ////
  /////////////////////////

  // FIXME: [mwu] is this safe if module name is List1 ?
  implicit class ModuleName_ops(moduleName: API.Module.Name) {
    def nameAsString(): String = moduleName.toList.map(_.name).mkString(".")
  }

  ////////////////////
  //// Module ops ////
  ////////////////////

  implicit class Module_ops(module: AST.Module) {
    def imports: List[Import] = module.flatTraverse(_.toImport)
    def lineIndexOf(ast: AST): Option[Int] =
      lineIndexWhere(_ == ast).map(_._2) // FIXME
    def lineIndexWhere(p: AST => Boolean): Option[(OptLine, Int)] =
      module.lines.zipWithIndex.find(_._1.elem.exists(p))

    def lineOffset(lineIx: Int): Int =
      module.lines.toList.take(lineIx).foldLeft(0) {
        case (offset, line) => offset + line.span
      }

    /** flatMaps non-empty lines ASTs. */
    def flatTraverse[B](f: AST => GenTraversableOnce[B]): List[B] =
      module.lines.toList.flatMap(_.elem).flatMap(f(_))

    def insert(index: Int, addedLine: OptLine): AST.Module = {
      val moduleIsEmpty = module.lines match {
        case List1(line, Nil) => line.elem.isEmpty
        case _                => false
      }
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
            case None           => l +: go(ls)
            case Some(newLines) => newLines ++ ls
          }
      }
      AST.Module(List1(go(module.lines.toList)).get)
    }
  }
}

/////////////////////////////
//// Describe definition ////
/////////////////////////////

/** Helper structure describing AST piece that is a function definition. */
case class DefinitionInfo(ast: App.Infix, name: AST.Var, args: List1[AST]) {}

object DefinitionInfo {
  import AstOps._

  def apply(ast: AST): Option[DefinitionInfo] =
    for {
      infix  <- ast.toAssignment
      lhsApp <- infix.larg.as[App.Prefix]
      lhsParts = lhsApp.flattenPrefix(TextPosition.Start)
      (name, args) <- lhsParts match {
        case name :: args =>
          for {
            ident   <- name.elem.as[AST.Var]
            argList <- List1(args.map(_.elem))
          } yield ident -> argList
        case _ => None
      }
    } yield DefinitionInfo(infix, name, args)
}

//////////////////////////////////////////////////////////////////////////////
//// GeneralizedInfix ////////////////////////////////////////////////////////
//////////////////////////////////////////////////////////////////////////////

/** A helper type to abstract away differences between proper infix operator
  * application and ones with missing argument (section apps).
  * Also provides operator chain usage flattening.
  *
  * @note SAST's offset refer to spacing between operand and operator
  */
final case class GeneralizedInfix(
  leftAST: Option[SAST],
  oprAST: AST.Opr,
  rightAST: Option[SAST]
) {
  import GeneralizedInfix._

  def assoc: Assoc = Assoc.of(name)
  def name: String = oprAST.name
  def leftOperand(offset: TextPosition): Operand =
    Positioned(leftAST.map(_.el), offset)
  def operator(offset: TextPosition): Operator =
    Positioned(oprAST, offset + operatorOffset)
  def rightOperand(offset: TextPosition): Operand =
    Positioned(rightAST.map(_.el), offset + rightArgumentOffset)
  def selfOperand(offset: TextPosition): Operand = assoc match {
    case Assoc.Left  => leftOperand(offset)
    case Assoc.Right => rightOperand(offset)
  }
  def nonSelfOperand(offset: TextPosition): Operand = assoc match {
    case Assoc.Left  => rightOperand(offset)
    case Assoc.Right => leftOperand(offset)
  }
  def operatorOffset: Int =
    leftAST.map(arg => arg.el.span + arg.off).getOrElse(0)
  def rightArgumentOffset: Int =
    operatorOffset + oprAST.span + rightAST.map(_.off).getOrElse(0)

  /** Converts nested operator applications into a flat list of all operands. */
  def flattenInfix(pos: TextPosition): Chain = {
    val self      = selfOperand(pos)
    val otherPart = ChainElem(operator(pos), nonSelfOperand(pos))

    val selfSubtreeAsInfix = self.flatMap(GeneralizedInfix(_))
    val selfSubtreeFlattened = selfSubtreeAsInfix match {
      case Some(selfInfix) if selfInfix.name == name =>
        selfInfix.flattenInfix(self.position)
      case _ =>
        Chain(self, Seq())
    }

    selfSubtreeFlattened.copy(parts = selfSubtreeFlattened.parts :+ otherPart)
  }
}
object GeneralizedInfix {

  /** Tries describing AST node as infix expression. */
  def apply(ast: AST): Option[GeneralizedInfix] = ast match {
    case AST.App.Infix.any(ast) =>
      Some(
        GeneralizedInfix(
          Some(Shifted(ast.loff, ast.larg)),
          ast.opr,
          Some(Shifted(ast.roff, ast.rarg))
        )
      )
    case AST.App.Section.Left.any(ast) =>
      Some(GeneralizedInfix(Some(Shifted(ast.off, ast.arg)), ast.opr, None))
    case AST.App.Section.Right.any(ast) =>
      Some(GeneralizedInfix(None, ast.opr, Some(Shifted(ast.off, ast.arg))))
    case AST.App.Section.Sides(opr) =>
      Some(GeneralizedInfix(None, opr, None))
    case _ => None
  }

  type Operand  = Positioned[Option[AST]]
  type Operator = Positioned[AST.Opr]
  final case class Parts(left: Operand, operator: Operator, right: Operand)
  final case class ChainElem(operator: Operator, operand: Operand)
  final case class Chain(self: Operand, parts: Seq[ChainElem])
}
