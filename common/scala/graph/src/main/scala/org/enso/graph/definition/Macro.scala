package org.enso.graph.definition

import scala.annotation.{compileTimeOnly, StaticAnnotation}
import scala.reflect.macros.whitebox

object Macro {

  @compileTimeOnly("please enable macro paradise to expand macro annotations")
  class field extends StaticAnnotation {
    def macroTransform(annottees: Any*): Any = macro FieldMacro.impl
  }
  object FieldMacro {
    def impl(c: whitebox.Context)(annottees: c.Expr[Any]*): c.Expr[Any] = {
      import c.universe._
      val members = annottees.map(_.tree).toList

      if (members.size != 1) {
        c.error(
          c.enclosingPosition,
          "You must apply the @field annotation to a single case class"
        )
      }

      /**
        * Extracts the template from a type definition.
        *
        * The template contains the body of the type definition, as well as the
        * definition of its constructor.
        *
        * @param classDef the class definition
        * @return the class template, if it exists
        */
      def extractDefTemplate(classDef: ClassDef): Option[Template] = {
        for (elem <- classDef.children) {
          elem match {
            case template: Template => return Some(template)
            case _                  =>
          }
        }
        None
      }

      /**
        * Extracts the constructor arguments from the class definition.
        *
        * @param classDef the class definition
        * @return a list containing the constructor arguments from `classDef`
        */
      def extractConstructorArguments(classDef: ClassDef): List[ValDef] = {
        val mTemplate = extractDefTemplate(classDef)

        mTemplate match {
          case Some(template) => {
            val allVals = template.body.collect {
              case valDef: ValDef => valDef
            }

            allVals.filter(
              t =>
                t.mods.hasFlag(
                  c.universe.Flag.CASEACCESSOR | c.universe.Flag.PARAMACCESSOR
                )
            )
          }
          case _ => List()
        }
      }

      /**
        * Appends a statement to a block with no return value.
        *
        * @param block the block to append to
        * @param statement the statement to append to `block`
        * @return `block` with `statement` appended to it
        */
      def appendToBlock(block: Block, statement: Tree*): Block = Block(
        block.stats ++ statement,
        EmptyTree
      )

      /**
        * Generates a set of definitions that correspond to defining a
        * non-variant field for a graph component.
        *
        * @param classDef the class definition to expand
        * @return a full definition for the field
        */
      def processSingleField(classDef: ClassDef): c.Expr[Any] = {
        // Typecheck?
        val fieldName: TypeName     = classDef.name
        val classArgs: List[ValDef] = extractConstructorArguments(classDef)

        val imports: Block = Block(
          List(
            q"""import org.enso.graph.Graph.Component.Field""",
            q"""import org.enso.graph.Sized""",
            q"""import shapeless.nat._"""
          ),
          EmptyTree
        )

        val baseClass: Tree =
          q"final case class $fieldName() extends Graph.Component.Field"

        val result: Block = appendToBlock(imports, baseClass)

        println("\n ===============DEBUG:")
        println(show(result))

        c.Expr(result)
      }

      def processVariantFields(moduleDef: ModuleDef): c.Expr[Any] = {
        println(show(members.head))
        println(showRaw(members.head))

        ???
      }

      members.head match {
        case classDef: ClassDef => {
          val modifiers: Modifiers = classDef.mods

          if (!modifiers.hasFlag(c.universe.Flag.CASE)) {
            c.error(
              c.enclosingPosition,
              "@field must be applied to a case class or object"
            )
            annottees.head
          } else {
            processSingleField(classDef)
          }
        }
        case moduleDef: ModuleDef => {
          processVariantFields(moduleDef)
        }
        case _ => {
          c.error(
            c.enclosingPosition,
            "The @field macro only operates on case classes."
          )
          annottees.head
        }
      }
    }
  }

  // TODO [AA] Handle variant fields (in @field or in separate macro)
  // TODO [AA] Do @component too
}
