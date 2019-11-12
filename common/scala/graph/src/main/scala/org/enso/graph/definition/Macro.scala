package org.enso.graph.definition

import shapeless.Nat

import scala.annotation.{StaticAnnotation, compileTimeOnly}
import scala.reflect.macros.whitebox

object Macro {

  /**
    * This macro generates a field definition for the graph, and can generate
    * these definitions for both single and variant fields for a
    * [[org.enso.graph.Graph.Component]].
    *
    * For a single field, you provide it a definition as follows:
    *
    * {{{
    *   @field case class MyName[TParams..](args...)
    * }}}
    *
    * It will then generate the required boilerplate for this field definition,
    * including field setters and getters for each of the constructor arguments
    * in the template definition.
    *
    * As an examble, consider the following:
    *
    * {{{@field case class ParentLink[G <: Graph](parent: Edge[G])}}}
    *
    * This application of the macro will generate the following code:
    *
    * {{{
    *   final case class ParentLink() extends Graph.Component.Field
    *   object ParentLink {
    *     implicit def sized = new Sized[ParentLink] { type Out = _1 }
    *
    *     object implicits {
    *       implicit class ParentLinkInstance[G <: Graph, C <: Component](
    *         node: Component.Ref[G, C]
    *       ) {
    *         def parent(
    *           implicit graph: GraphData[G],
    *           ev: HasComponentField[G, C, ParentLink]
    *         ): Edge[G] = {
    *           Component.Ref(graph.unsafeReadField[C, ParentLink](node.ix, 0))
    *         }
    *
    *         def parent_=(value: Edge[G])(
    *           implicit graph: GraphData[G],
    *           ev: HasComponentField[G, C, ParentLink]
    *         ): Unit = {
    *           graph.unsafeWriteField[C, ParentLink](node.ix, 0, value.ix)
    *         }
    *       }
    *
    *       implicit def ParentLink_transInstance[
    *         F <: Component.Field,
    *         R,
    *         G <: Graph,
    *         C <: Component
    *       ](
    *         t: Component.Refined[F, R, Component.Ref[G, C]]
    *       ): ParentLinkInstance[G, C] =
    *         t.wrapped
    *     }
    *   }
    * }}}
    *
    * You will need to ensure that `MyName.implicits._` is imported into scope
    * as we currently have no way of making that work better.
    */
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
        * Creates a constant type-level identifier for Shapeless' [[Nat]] type.
        *
        * @param num the natural number you want to represent as a [[Nat]]
        * @return a [[TypeName]] representing that [[Nat]]
        */
      def mkNatConstantTypeName(num: Int): TypeName =
        TypeName("_" + num.toString)

      def genSubfieldGetter(
        paramDef: ValDef,
        enclosingTypeName: TypeName,
        index: Int
      ): Tree = {
        val paramName: TermName = paramDef.name
        val paramType: Tree     = paramDef.tpt

        q"""
          def $paramName(
            implicit graph: GraphData[G],
            ev: HasComponentField[G, C, $enclosingTypeName]
          ): $paramType = {
            Component.Ref(
              graph.unsafeReadField[C, $enclosingTypeName](node.ix, $index)
            )
          }
         """
      }

      def genSubfieldSetter(
        paramDef: ValDef,
        enclosingTypeName: TypeName,
        index: Int
      ): Tree = {
        val accessorName: TermName = TermName(paramDef.name.toString + "_$eq")
        val paramType: Tree        = paramDef.tpt

        q"""
          def $accessorName(value: $paramType)(
            implicit graph: GraphData[G],
            ev: HasComponentField[G, C, $enclosingTypeName]
          ): Unit = {
            graph.unsafeWriteField[C, $enclosingTypeName](
              node.ix, $index, value.ix
            )
          }
         """
      }

      def genSubfieldAccessors(
        subfields: List[ValDef],
        enclosingName: TypeName
      ): List[Tree] = {
        var accessorDefs: List[Tree] = List()

        for ((subfield, ix) <- subfields.view.zipWithIndex) {
          accessorDefs = accessorDefs :+ genSubfieldGetter(
              subfield,
              enclosingName,
              ix
            )
          accessorDefs = accessorDefs :+ genSubfieldSetter(
              subfield,
              enclosingName,
              ix
            )
        }

        accessorDefs
      }

      def genTransInstance(
        enclosingName: TermName,
        implicitClassName: TypeName
      ): Tree = {
        val defName = TermName(enclosingName.toString + "_transInstance")

        q"""
          implicit def $defName[
            F <: Component.Field,
            R,
            G <: Graph,
            C <: Component
          ](
            t: Component.Refined[F, R, Component.Ref[G, C]]
          ): $implicitClassName[G, C] = t.wrapped
         """
      }

      /**
        * Generates a set of definitions that correspond to defining a
        * non-variant field for a graph component.
        *
        * @param classDef the class definition to expand
        * @return a full definition for the field
        */
      def processSingleField(classDef: ClassDef): c.Expr[Any] = {
        val fieldTypeName: TypeName = classDef.name
        val fieldTermName: TermName = fieldTypeName.toTermName
        val subfields: List[ValDef] = extractConstructorArguments(classDef)
        val natSubfields: TypeName  = mkNatConstantTypeName(subfields.length)
        val implicitClassName: TypeName = TypeName(
          fieldTypeName.toString + "Instance"
        )

        val imports: Block = Block(
          List(
            q"""import org.enso.graph.Graph.Component.Field""",
//            q"""import org.enso.graph.Sized""",
            q"""import shapeless.nat._""",
//            q"""import org.enso.graph.Graph""",
            q"""import org.enso.graph.Graph.Component""",
            q"""import org.enso.graph.Graph.GraphData""",
            q"""import org.enso.graph.Graph.HasComponentField"""
          ),
          EmptyTree
        )

        // TODO [AA] Should not actually take type parameters
        val baseClass: Tree =
          q"final case class $fieldTypeName() extends Graph.Component.Field"

        val accessorClassStub: ClassDef = q"""
            implicit class $implicitClassName[G <: Graph, C <: Component](
              node: Component.Ref[G, C]
            )
           """.asInstanceOf[ClassDef]

        val accessorClass: ClassDef = ClassDef(
          accessorClassStub.mods,
          accessorClassStub.name,
          accessorClassStub.tparams,
          Template(
            accessorClassStub.impl.parents,
            accessorClassStub.impl.self,
            accessorClassStub.impl.body ++ genSubfieldAccessors(
              subfields,
              fieldTypeName
            )
          )
        )

        val implicitsModuleStub = q"object implicits".asInstanceOf[ModuleDef]
        val implicitsModule = ModuleDef(
          Modifiers(),
          implicitsModuleStub.name,
          Template(
            implicitsModuleStub.impl.parents,
            implicitsModuleStub.impl.self,
            implicitsModuleStub.impl.body.filter(stat => stat != EmptyTree)
            :+ accessorClass :+ genTransInstance(
              fieldTermName,
              implicitClassName
            )
          )
        )

        val companionModuleStub: ModuleDef =
          q"""
            object $fieldTermName {
              implicit def sized = new Sized[$fieldTypeName] {
                type Out = $natSubfields
              }
            }
            """.asInstanceOf[ModuleDef]

        val companionModule = ModuleDef(
          Modifiers(),
          companionModuleStub.name,
          Template(
            companionModuleStub.impl.parents,
            companionModuleStub.impl.self,
            companionModuleStub.impl.body :+ implicitsModule
          )
        )

        val resultBlock =
          appendToBlock(imports, baseClass, companionModule).stats

        val result = q"..$resultBlock"

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
