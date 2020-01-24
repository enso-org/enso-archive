package org.enso.graph.definition

import scala.annotation.{compileTimeOnly, StaticAnnotation}
import scala.reflect.macros.whitebox

object Macro {

  // TODO [AA] Fix up the simple case first
  // TODO [AA] Fix up the variant case next
  // TODO [AA] Add support for opaque types
  // TODO [AA] Check the docs

  /** This macro generates a field definition for the graph, and can generate
    * these definitions for both single and variant fields for a
    * [[org.enso.graph.Graph.Component]].
    *
    * For a single field, you provide it a definition as follows:
    *
    * {{{
    *   @field case class MyName[G <: Graph, TParams..](args...)
    * }}}
    *
    * It will then generate the required boilerplate for this field definition,
    * including field setters and getters for each of the constructor arguments
    * in the template definition. It must include a type parameter `G` with its
    * bound set to the imported name of the primitive graph in its definition.
    *
    * As an example, consider the following:
    *
    * {{{@field case class ParentLink[G <: Graph](parent: Edge[G])}}}
    *
    * This application of the macro will generate the following code:
    *
    * {{{
    *   sealed case class ParentLink() extends Graph.Component.Field
    *   object ParentLink {
    *     implicit def sized = new Graph.Sized[ParentLink] { type Out = _1 }
    *
    *     implicit class ParentLinkInstance[G <: Graph, C <: Graph.Component](
    *       node: Graph.Component.Ref[G, C]
    *     ) {
    *       def parent(
    *         implicit graph: Graph.GraphData[G],
    *         ev: Graph.HasComponentField[G, C, ParentLink]
    *       ): Edge[G] = {
    *         Graph.Component.Ref(graph.unsafeReadField[C, ParentLink](
    *           node.ix,
    *           0
    *         ))
    *       }
    *
    *       def parent_=(value: Edge[G])(
    *         implicit graph: Graph.GraphData[G],
    *         ev: Graph.HasComponentField[G, C, ParentLink]
    *       ): Unit = {
    *         graph.unsafeWriteField[C, ParentLink](node.ix, 0, value.ix)
    *       }
    *     }
    *
    *     implicit def ParentLink_transInstance[
    *       F <: Graph.Component.Field,
    *       R,
    *       G <: Graph,
    *       C <: Graph.Component
    *     ](
    *       t: Graph.Component.Refined[F, R, Graph.Component.Ref[G, C]]
    *     ): ParentLinkInstance[G, C] =
    *       t.wrapped
    *   }
    * }}}
    *
    * You will need to ensure that `T._` is imported into scope as we currently
    * have no way of making that work better.
    *
    * For a variant field (tagged union), the same macro can be applied to an
    * object definition. This object definition must be provided as follows:
    *
    * {{{
    *   @field object VariantType {
    *     type G = Graph
    *     case class V1()
    *     case class V2(field1: T)
    *     case class V3(field1: T, field2: Q)
    *     // ...
    *   }
    * }}}
    *
    * For this type of definition, the name of the object (here `VariantType`)
    * defines the type of the variant, and the case classes in the body define
    * the variant cases. Each case is supplied with subfield accessors, as well
    * ]as custom unapply methods, where necessary. The fields are nested in the
    * scope of the variant, so above you would have `VariantType.V1`, for
    * access. Furthermore, the body must define a `type G` that binds the name
    * of the primtive graph implementation in scope.
    *
    * The following is a simple definition of a variant component:
    *
    * {{{
    *   @field object Shape {
    *      type G = Graph
    *     case class Null()
    *     case class App(fn: Edge[G], argTest: Edge[G])
    *   }
    * }}}
    *
    * This generates the following field definition:
    *
    * {{{
    *   sealed trait Shape extends Graph.Component.Field
    *   object Shape {
    *     implicit def sized = new Sized[Shape] { type Out = _3 }
    *
    *     sealed case class Null() extends Shape
    *     object Null {
    *       val any            = Graph.Component.VariantMatcher[Shape, Null](0)
    *       implicit def sized = new Sized[Null] { type Out = _0 }
    *     }
    *
    *     sealed case class App() extends Shape
    *     object App {
    *       implicit def sized = new Sized[App] { type Out = _2 }
    *
    *       val any = Graph.Component.VariantMatcher[Shape, App](1)
    *
    *       def unapply[G <: Graph, C <: Graph.Component]
    *          (arg: Graph.Component.Ref[G, C])(
    *         implicit
    *         graph: Graph.GraphData[G],
    *         ev: Graph.HasComponentField[G, C, Shape]
    *       ): Option[(Edge[G], Edge[G])] =
    *         any.unapply(arg).map(t => (t.fn, t.arg))
    *
    *       implicit class AppInstance[G <: Graph, C <: Graph.Component](
    *         node: Graph.Component.Refined[
    *            Shape,
    *            App,
    *            Graph.Component.Ref[G, C]
    *          ]
    *       ) {
    *
    *         def fn(
    *           implicit graph: Graph.GraphData[G],
    *           ev: Graph.HasComponentField[G, C, Shape]
    *         ): Edge[G] = {
    *           Graph.Component.Ref(
    *             graph
    *               .unsafeReadField[C, Shape](
    *                 Graph.Component.Refined.unwrap(node).ix,
    *                 1
    *               )
    *           )
    *         }
    *
    *         def fn_=(value: Edge[G])(
    *           implicit graph: Graph.GraphData[G],
    *           ev: Graph.HasComponentField[G, C, Shape]
    *         ): Unit = {
    *           graph.unsafeWriteField[C, Shape](
    *             Graph.Component.Refined.unwrap(node).ix,
    *             1,
    *             value.ix
    *           )
    *         }
    *
    *         def arg(
    *           implicit graph: Graph.GraphData[G],
    *           ev: Graph.HasComponentField[G, C, Shape]
    *         ): Edge[G] = {
    *           Graph.Component.Ref(
    *             graph
    *               .unsafeReadField[C, Shape](
    *                 Graph.Component.Refined.unwrap(node).ix,
    *                 2
    *               )
    *           )
    *         }
    *
    *         def arg_=(value: Edge[G])(
    *           implicit graph: Graph.GraphData[G],
    *           ev: Graph.HasComponentField[G, C, Shape]
    *         ): Unit = {
    *           graph.unsafeWriteField[C, Shape](
    *             Graph.Component.Refined.unwrap(node).ix,
    *             2,
    *             value.ix
    *           )
    *         }
    *       }
    *     }
    *   }
    * }}}
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
          "You must apply the @field annotation to a single entity"
        )
      }

      val baseBlock: Block = Block(
        List(
          q"""import shapeless.nat._"""
        ),
        EmptyTree
      )

      /** Extracts the template from a type definition.
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

      /** Extracts the constructor arguments from the class definition.
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

      /** Appends a statement to a block with no return value.
        *
        * @param block the block to append to
        * @param statement the statement to append to `block`
        * @return `block` with `statement` appended to it
        */
      def appendToBlock(block: Block, statement: Tree*): Block = Block(
        block.stats ++ statement,
        EmptyTree
      )

      /** Creates a constant type-level identifier for Shapeless'
        * [[shapeless.Nat]] type.
        *
        * @param num the natural number you want to represent as a
        *            [[shapeless.Nat]]
        * @return a [[TypeName]] representing that [[shapeless.Nat]]
        */
      def mkNatConstantTypeName(num: Int): TypeName =
        TypeName("_" + num.toString)

      /** Generates a getter for an element of a non-variant field.
        *
        * @param paramDef the definition of the subfield
        * @param enclosingTypeName the name of the field type
        * @param index the index of this subfield in the field
        * @return a definition for this subfield getter
        */
      def genSubfieldGetter(
        paramDef: ValDef,
        enclosingTypeName: TypeName,
        index: Int,
        graphTypeName: TypeName,
        isSimple: Boolean
      ): Tree = {
        val paramName: TermName     = paramDef.name
        val paramType: Tree         = paramDef.tpt
        val graphTermName: TermName = graphTypeName.toTermName

        val isRawType = paramDef.tpt match {
          case Ident(TypeName("Int")) => true
          case _ => false
        }

        if (isSimple) {
          if (isRawType) {
            q"""
            def $paramName(
              implicit graph: $graphTermName.GraphData[G],
              ev: $graphTermName.HasComponentField[G, C, $enclosingTypeName]
            ): $paramType = {
              graph.unsafeReadField[C, $enclosingTypeName](node.ix, $index)
            }
            """
          } else {
            q"""
            def $paramName(
              implicit graph: $graphTermName.GraphData[G],
              ev: $graphTermName.HasComponentField[G, C, $enclosingTypeName]
            ): $paramType = {
              $graphTermName.Component.Ref(
                graph.unsafeReadField[C, $enclosingTypeName](node.ix, $index)
              )
            }
            """
          }
        } else {
          if (isRawType) {
            q"""
            def $paramName(
              implicit graph: $graphTermName.GraphData[G],
              ev: $graphTermName.HasComponentField[G, C, $enclosingTypeName]
            ): $paramType = {
              $graphTermName.Component.Ref(
                graph.unsafeReadField[C, $enclosingTypeName](
                  $graphTermName.Component.Refined.unwrap(node),
                  $index
                )
              )
            }
            """
          } else {
            q"""
            def $paramName(
              implicit graph: $graphTermName.GraphData[G],
              ev: $graphTermName.HasComponentField[G, C, $enclosingTypeName]
            ): $paramType = {
              $graphTermName.Component.Ref(
                graph.unsafeReadField[C, $enclosingTypeName](
                  $graphTermName.Component.Refined.unwrap(node).ix,
                  $index
                )
              )
            }
            """
          }
        }
      }

      /** Generates a setter for an element of a non-variant field.
        *
        * @param paramDef the definition of the subfield
        * @param enclosingTypeName the name of the field type
        * @param index the index of this subfield in the field
        * @return a definition for this subfield setter
        */
      def genSubfieldSetter(
        paramDef: ValDef,
        enclosingTypeName: TypeName,
        index: Int,
        graphTypeName: TypeName,
        isSimple: Boolean
      ): Tree = {
        val accessorName: TermName  = TermName(paramDef.name.toString + "_$eq")
        val paramType: Tree         = paramDef.tpt
        val graphTermName: TermName = graphTypeName.toTermName

        val isRawType = paramType match {
          case Ident(TypeName("Int")) => true
          case _ => false
        }

        if (isSimple) {
          if (isRawType) {
            q"""
            def $accessorName(value: $paramType)(
              implicit graph: $graphTermName.GraphData[G],
              ev: $graphTermName.HasComponentField[G, C, $enclosingTypeName]
            ): Unit = {
              graph.unsafeWriteField[C, $enclosingTypeName](
                node.ix, $index, value
              )
            }
            """
          } else {
            q"""
            def $accessorName(value: $paramType)(
              implicit graph: $graphTermName.GraphData[G],
              ev: $graphTermName.HasComponentField[G, C, $enclosingTypeName]
            ): Unit = {
              graph.unsafeWriteField[C, $enclosingTypeName](
                node.ix, $index, value.ix
              )
            }
            """
          }
        } else {
          if (isRawType) {
            q"""
            def $accessorName(value: $paramType)(
              implicit graph: $graphTermName.GraphData[G],
              ev: $graphTermName.HasComponentField[G, C, $enclosingTypeName]
            ): Unit = {
              graph.unsafeWriteField[C, $enclosingTypeName](
                $graphTermName.Component.Refined.unwrap(node).ix,
                $index,
                value
              )
            }
            """
          } else {
            q"""
            def $accessorName(value: $paramType)(
              implicit graph: $graphTermName.GraphData[G],
              ev: $graphTermName.HasComponentField[G, C, $enclosingTypeName]
            ): Unit = {
              graph.unsafeWriteField[C, $enclosingTypeName](
                $graphTermName.Component.Refined.unwrap(node).ix,
                $index,
                value.ix
              )
            }
            """
          }
        }
      }

      /** Generates setters and getters for all the subfields for a given field.
        *
        * @param subfields a list containing the subfield definitions
        * @param enclosingName the name of the field type
        * @return the definitions of getters and setters for all the subfields
        *         in `subfields`
        */
      def genAccessors(
        subfields: List[ValDef],
        enclosingName: TypeName,
        graphTypeName: TypeName,
        valClassTypeName: TypeName,
        isSimple: Boolean
      ): List[Tree] = {
        var accessorDefs: List[Tree] = List()

        for ((subfield, ix) <- subfields.view.zipWithIndex) {
          accessorDefs = accessorDefs :+ genSubfieldGetter(
              subfield,
              enclosingName,
              ix,
              graphTypeName,
              isSimple
            )
          accessorDefs = accessorDefs :+ genSubfieldSetter(
              subfield,
              enclosingName,
              ix,
              graphTypeName,
              isSimple
            )
        }

        val valClassAccessors = genValClassAccessors(
          subfields,
          enclosingName,
          graphTypeName,
          valClassTypeName,
          isSimple
        )

        accessorDefs ++ valClassAccessors
      }

      def genValClassAccessors(
        subfields: List[ValDef],
        enclosingName: TypeName,
        graphTypeName: TypeName,
        valClassTypeName: TypeName,
        isSimple: Boolean
      ): List[Tree] = {
        val graphTermName = graphTypeName.toTermName

        val valClassTermName = valClassTypeName.toTermName
        val valGetterName    = TermName(enclosingName.toString.toLowerCase)
        val valSetterName = TermName(
          enclosingName.toString.toLowerCase + "_$eq"
        )

        val valGetter = if (isSimple) {
          q"""
          def $valGetterName(
            implicit graph: $graphTermName.GraphData[G],
            ev: $graphTermName.HasComponentField[G, C, $enclosingName]
          ): $valClassTypeName[G] = {
            $valClassTermName(..${subfields.map(t => q"this.${t.name}")})
          }
           """
        } else {
          ???
        }

        val valSetter = if (isSimple) {
          q"""
          def $valSetterName(
            value: $valClassTypeName[G]
          )(
            implicit graph: $graphTermName.GraphData[G],
            ev: $graphTermName.HasComponentField[G, C, $enclosingName]
          ): Unit = {
            ..${subfields.map(t => q"this.${t.name} = value.${t.name}")}
          }
           """
        } else {
          ???
        }

        List(valGetter, valSetter)
      }

      /** Generates an instance that is used for assisting inference with
        * refined subfields.
        *
        * @param enclosingName the name of the field
        * @param implicitClassName the name of the instance
        * @return an instance definition that assists with inference
        */
      def genTransInstance(
        enclosingName: TermName,
        implicitClassName: TypeName,
        graphTypeName: TypeName
      ): Tree = {
        val defName       = TermName(enclosingName.toString + "_transInstance")
        val graphTermName = graphTypeName.toTermName

        q"""
          implicit def $defName[
            F <: $graphTermName.Component.Field,
            R,
            G <: $graphTypeName,
            C <: $graphTermName.Component
          ](
            t: $graphTermName.Component.Refined[F, R, $graphTermName.Component.Ref[G, C]]
          ): $implicitClassName[G, C] = t.wrapped
         """
      }

      /** Appends trees to a template definition.
        *
        * @param template the template to append to
        * @param trees the trees to append
        * @return `template` with `trees` appended to the end of its body
        */
      def appendToTemplate(template: Template, trees: List[Tree]): Template = {
        Template(
          template.parents,
          template.self,
          template.body ++ trees
        )
      }

      /** Appends trees to the body of a class definition.
        *
        * @param classDef the definition to append to
        * @param trees the trees to append
        * @return `classDef` with `trees` appended to the end of its body
        */
      def appendToClass(classDef: ClassDef, trees: List[Tree]): ClassDef = {
        ClassDef(
          classDef.mods,
          classDef.name,
          classDef.tparams,
          appendToTemplate(classDef.impl, trees)
        )
      }

      /** Appends trees to the body of a module (object) definition.
        *
        * @param module the definition to append to
        * @param trees the trees to append
        * @return `module` with `trees` appended to the end of its body
        */
      def appendToModule(module: ModuleDef, trees: List[Tree]): ModuleDef = {
        ModuleDef(
          Modifiers(),
          module.name,
          appendToTemplate(module.impl, trees)
        )
      }

      /** Generates the name for the implicit class containing the accessors.
        *
        * @param typeName the name of the field
        * @return the name of the field's implicit accessor class
        */
      def mkImplicitClassName(typeName: TypeName): TypeName =
        TypeName(typeName.toString + "Instance")

      def extractSingleGraphTypeName(
        tParams: List[c.universe.TypeDef]
      ): TypeName = {
        val errorTName = TypeName("ERROR")

        if (tParams.isEmpty) {
          c.error(
            c.enclosingPosition,
            "Your case class must have at least one type parameter"
          )
          errorTName
        } else {
          val firstTParam     = tParams.head
          val firstTParamName = firstTParam.name

          if (firstTParamName != TypeName("G")) {
            c.error(
              c.enclosingPosition,
              "Your first type bound must be named \"G\""
            )
            errorTName
          } else {
            val boundsNames = firstTParam.children
              .collect {
                case tree: TypeBoundsTree => tree
              }
              .map(_.hi)
              .collect {
                case Ident(name) => name.toTypeName
              }

            boundsNames.head
          }

        }
      }

      /** Generates a set of definitions that correspond to defining a
        * non-variant field for a graph component.
        *
        * @param classDef the class definition to expand
        * @return a full definition for the field
        */
      def processSingleField(classDef: ClassDef): c.Expr[Any] = {
        val fieldTypeName: TypeName     = classDef.name
        val fieldTermName: TermName     = fieldTypeName.toTermName
        val subfields: List[ValDef]     = extractConstructorArguments(classDef)
        val natSubfields: TypeName      = mkNatConstantTypeName(subfields.length)
        val implicitClassName: TypeName = mkImplicitClassName(fieldTypeName)
        val graphTypeName: TypeName = extractSingleGraphTypeName(
          classDef.tparams
        )
        val graphTermName: TermName = graphTypeName.toTermName
        val valClassName: TypeName  = TypeName(fieldTermName.toString + "Val")

        val baseClass: Tree =
          q"sealed case class $fieldTypeName() extends $graphTermName.Component.Field"

        val valueClass: Tree =
          q"sealed case class $valClassName[G <: $graphTypeName](..$subfields)"

        val accessorClassStub: ClassDef = q"""
            implicit class $implicitClassName[G <: $graphTypeName, C <: $graphTermName.Component](
              node: $graphTermName.Component.Ref[G, C]
            )
           """.asInstanceOf[ClassDef]
        val accessorClass: ClassDef = appendToClass(
          accessorClassStub,
          genAccessors(
            subfields,
            fieldTypeName,
            graphTypeName,
            valClassName,
            isSimple = true
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

        val companionModule =
          appendToModule(
            companionModuleStub,
            List(
              accessorClass,
              genTransInstance(fieldTermName, implicitClassName, graphTypeName)
            )
          )

        val resultBlock =
          appendToBlock(baseBlock, baseClass, valueClass, companionModule).stats

        val result = q"..$resultBlock"

        println(showCode(result))

        c.Expr(result)
      }

      /** Extracts the variant case definitions from the module body.
        *
        * @param template the template of the variant defining module
        * @return a list of the variant cases
        */
      def extractVariantDefs(template: Template): List[ClassDef] = {
        template.body.collect { case classDef: ClassDef => classDef }
      }

      /** Generates the definition body for a variant case.
        *
        * @param classDef the definition of the variant case
        * @param parentName the name of the parent variant
        * @param index the case's index in the variant
        * @return the expanded defintion for the variant case described by
        *         `classDef`
        */
      def generateVariantCaseDef(
        classDef: ClassDef,
        parentName: TypeName,
        index: Int,
        graphTypeName: TypeName
      ): (Block, Int) = {
        val typeName          = classDef.name
        val termName          = typeName.toTermName
        val subfields         = extractConstructorArguments(classDef)
        val natSubfields      = mkNatConstantTypeName(subfields.length)
        val implicitClassName = mkImplicitClassName(typeName)
        val subfieldTypes     = subfields.map(f => f.tpt)
        val graphTermName     = graphTypeName.toTermName
        val valClassName      = TypeName(typeName.toString + "Val")

        val variantClass: ClassDef =
          q"""
            sealed case class $typeName() extends $parentName
           """.asInstanceOf[ClassDef]

        val variantModuleStub: ModuleDef =
          q"""
            object $termName {
              val any = $graphTermName.Component.VariantMatcher[
                  $parentName,
                  $typeName
                ]($index)
              implicit def sized = new Sized[$typeName] {
                type Out = $natSubfields
              }
            }
           """.asInstanceOf[ModuleDef]

        val unapplyDef: DefDef =
          q"""
            def unapply[G <: $graphTypeName, C <: $graphTermName.Component] (
              arg: $graphTermName.Component.Ref[G, C])(
            )(
              implicit
              graph: $graphTermName.GraphData[G],
              ev: $graphTermName.HasComponentField[G, C, $parentName]
            ): Option[(..$subfieldTypes)] = {
              any.unapply(arg).map(
                t => (..${subfields.map(f => q"t.${f.name}")})
              )
            }
           """.asInstanceOf[DefDef]

        val accessorClassStub: ClassDef = q"""
            implicit class $implicitClassName[
              G <: $graphTypeName,
              C <: $graphTermName.Component
            ](
              node: $graphTermName.Component.Refined[
                $parentName,
                $typeName,
                $graphTermName.Component.Ref[G, C]
              ]
            )
           """.asInstanceOf[ClassDef]
        val accessorClass: ClassDef = appendToClass(
          accessorClassStub,
          genAccessors(
            subfields,
            parentName,
            graphTypeName,
            valClassName,
            isSimple = false
          )
        )

        val result = if (subfields.nonEmpty) {
          appendToModule(variantModuleStub, List(unapplyDef, accessorClass))
        } else {
          variantModuleStub
        }

        (
          q"..${List(variantClass, result)}".asInstanceOf[Block],
          subfields.length
        )
      }

      /** Extracts the name for the primitive graph type from the variant body.
        *
        * @param template the variant body to extract the name from
        * @return the name by which the primitive graph type is in scope
        */
      def extractVariantGraphTypeName(template: Template): TypeName = {
        val typeDefs  = template.body.collect { case tDef: TypeDef => tDef }
        val gName     = TypeName("G")
        val errorName = TypeName("Error")

        if (typeDefs.length < 1) {
          c.error(
            c.enclosingPosition,
            "You must define a type named `G` in your variant that " +
            "defines the graph type name"
          )
          errorName
        } else {
          val gNames = typeDefs.filter(d => d.name == gName)

          if (gNames.isEmpty) {
            c.error(
              c.enclosingPosition,
              "You must define a type named `G` in your variant that " +
              "defines the graph type name"
            )
            errorName
          } else {
            val firstGName = gNames.head
            val idents = firstGName.children.collect {
              case Ident(name) => name.toTypeName
            }

            if (idents.length != 1) {
              c.error(
                c.enclosingPosition,
                "You must assign the name of the primitive graph to `G`"
              )
              errorName
            } else {
              idents.head
            }
          }
        }
      }

      /** Generates the whole set of definitions necessary for a variant
        * component field from the provided description.
        *
        * @param moduleDef the description of the variant field
        * @return the expanded defintion of the variant described by `moduleDef`
        */
      def processVariantField(moduleDef: ModuleDef): c.Expr[Any] = {
        val variantTermName: TermName = moduleDef.name
        val variantTypeName: TypeName = variantTermName.toTypeName
        val graphTypeName: TypeName =
          extractVariantGraphTypeName(moduleDef.impl)
        val graphTermName: TermName = graphTypeName.toTermName

        val baseTrait: ClassDef =
          q"""
            sealed trait $variantTypeName extends $graphTermName.Component.Field
           """.asInstanceOf[ClassDef]

        val variantDefs = extractVariantDefs(moduleDef.impl)

        if (variantDefs.length < 1) {
          c.error(
            c.enclosingPosition,
            "A variant must contain at least one case"
          )
        }

        val variantResults =
          for ((cls, ix) <- variantDefs.view.zipWithIndex)
            yield generateVariantCaseDef(
              cls,
              variantTypeName,
              ix,
              graphTypeName
            )

        // We want to flatten the block structure for correct codegen
        val variantDefinitions =
          variantResults.map(_._1).map(t => t.stats).flatten

        // Note [Encoding Variant Size]
        val numTotalSize = variantResults
            .map(_._2)
            .foldLeft(0)((x: Int, y: Int) => Math.max(x, y)) + 1

        val baseModuleStub: ModuleDef =
          q"""
            object $variantTermName {
              implicit def sized = new Sized[$variantTypeName] {
                type Out = ${mkNatConstantTypeName(numTotalSize)}
              }
            }
           """.asInstanceOf[ModuleDef]

        val traitCompanionModule =
          appendToModule(baseModuleStub, variantDefinitions.toList)

        val result =
          q"..${appendToBlock(baseBlock, baseTrait, traitCompanionModule).stats}"

        c.Expr(result)
      }

      /* Note [Encoding Variant Size]
       * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~
       * Variant fields are encoded on the graph much like a union is in C. The
       * space allocation for storing the type is always the maximum size of any
       * of the variant types.
       *
       * However, the encoding used by the graphs operates more like a _tagged_
       * union, and hence we need to encode the constructor of the variant that
       * is stored in its allocation.
       *
       * To this end, the size of the variant type is _actually_ the maximum
       * size of all the variant constructors + 1, where that additional
       * field is used to encode the constructor tag.
       */

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
          processVariantField(moduleDef)
        }
        case _ => {
          c.error(
            c.enclosingPosition,
            "The @field macro only operates on case classes"
          )
          annottees.head
        }
      }
    }
  }

  /** This macro generates a component definition for the graph to help avoid
    * the boilerplate necessary to defined a [[org.enso.graph.Graph.Component]].
    *
    * You must apply the macro to a definition as follows:
    *
    * {{{
    *   @component case class Types() { type Type[G <: Graph] }
    * }}}
    *
    * The macro will generate the required boilerplate for the component
    * definition. It will generate a parent class named `Types`, and the a
    * contained type with name taken from the first type definition in the body.
    * That type must take one type parameter that is a subtype of Graph. The
    * name `Graph` must be whatever the primitive graph is named in scope.
    *
    * By way of example, consider the following macro invocation:
    *
    * {{{
    *   @component case class Nodes() { type Node[G <: Graph] }
    * }}}
    *
    * This will generate the following code (along with required imports):
    *
    * {{{
    *   sealed case class Nodes() extends Graph.Component
    *   type Node[G <: Graph] = Graph.Component.Ref[G, Nodes]
    *   implicit class GraphWithNodes[G <: Graph](graph: Graph.GraphData[G]) {
    *     def addNode()(implicit ev: Graph.HasComponent[G, Nodes]): Node[G] = {
    *       graph.addComponent[Nodes]()
    *     }
    *   }
    * }}}
    *
    */
  @compileTimeOnly("please enable macro paradise to expand macro annotations")
  class component extends StaticAnnotation {
    def macroTransform(annottees: Any*): Any = macro ComponentMacro.impl
  }
  object ComponentMacro {
    def impl(c: whitebox.Context)(annottees: c.Expr[Any]*): c.Expr[Any] = {
      import c.universe._
      val members = annottees.map(_.tree).toList

      if (members.size != 1) {
        c.error(
          c.enclosingPosition,
          "You must apply the @component annotation to a single entity"
        )
      }

      val baseBlock: Block = Block(List(), EmptyTree)

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

      /** Extracts the name of the child type in the definition, as well as the
        * name of its bound. These names are used to define the type contained
        * within a given component.
        *
        * If multiple `type T` definitions are found, the first will be used.
        *
        * @param template the body of the class the macro is applied to
        * @return a tuple of the name of the type declared in the body of
        *         `template` and the name representing the graph type
        */
      def extractItemNameAndBound(template: Template): (TypeName, TypeName) = {
        val typeDefs   = template.body.collect { case tDef: TypeDef => tDef }
        val errorTName = TypeName("ERROR")

        if (typeDefs.isEmpty) {
          c.error(
            c.enclosingPosition,
            "You must provide a name for the contained type, none found"
          )
          (errorTName, errorTName)
        } else {
          val tDef = typeDefs.head
          val typeParams = tDef.children.collect {
            case typeDef: TypeDef => typeDef
          }

          if (typeParams.length == 1) {
            val boundNames = typeParams.head.children
              .collect {
                case tree: TypeBoundsTree => tree
              }
              .map(_.hi)
              .collect {
                case Ident(name) => name.toTypeName
              }

            (tDef.name, boundNames.head)
          } else {
            c.error(
              c.enclosingPosition,
              "Your contained type must only have one type parameter"
            )
            (errorTName, errorTName)
          }
        }
      }

      /** Generates a graph component from the provided description.
        *
        * @param classDef a description of the variant component
        * @return the expanded form of the variant described by `classDef`
        */
      def genComponentFromClassDef(classDef: ClassDef): c.Expr[Any] = {
        if (!classDef.mods.hasFlag(Flag.CASE)) {
          c.error(
            c.enclosingPosition,
            "@component must be applied to a case class"
          )
        }

        val componentTypeName = classDef.name
        val (componentItemName, graphTypeName) = extractItemNameAndBound(
          classDef.impl
        )
        val graphTermName = graphTypeName.toTermName

        val caseClass: ClassDef =
          q"""
            sealed case class $componentTypeName() extends
              $graphTermName.Component
           """.asInstanceOf[ClassDef]

        val typeDef: TypeDef =
          q"""
            type $componentItemName[G <: $graphTypeName] =
              $graphTermName.Component.Ref[G, $componentTypeName]
           """.asInstanceOf[TypeDef]

        val implClassName = TypeName("GraphWith" + componentTypeName.toString)
        val addName       = TermName("add" + componentItemName.toString)
        val implicitClass: ClassDef =
          q"""
            implicit class $implClassName[G <: $graphTypeName]
              (graph: $graphTermName.GraphData[G]) {
              def $addName()(
                implicit ev: $graphTermName.HasComponent[G, $componentTypeName]
              ): $componentItemName[G] = {
                graph.addComponent[$componentTypeName]()
              }
            }
           """.asInstanceOf[ClassDef]

        val block  = appendToBlock(baseBlock, caseClass, typeDef, implicitClass)
        val result = q"..${block.stats}"

        c.Expr(result)
      }

      val annotatedItem = members.head

      annotatedItem match {
        case classDef: ClassDef => genComponentFromClassDef(classDef)
        case _ => {
          c.error(
            c.enclosingPosition,
            "You must provide a class definition to the @component macro"
          )
          c.Expr(annotatedItem)
        }
      }
    }
  }

  /** This macro generates an opaque storage definition for the graph.
    *
    * This aids in avoiding the necessary boilerplate to define opaque storage
    * for the graph. Opaque storage is used for containing types that _cannot_
    * be represented as [[org.enso.graph.Sized]], and hence cannot be stored
    * directly in the compact storage of a [[org.enso.graph.Graph]].
    *
    * You must apply the macro to a definition as follows:
    *
    * {{{
    *   @opaque case class Name(opaque: T)
    * }}}
    *
    * The macro will generate the following boilerplate:
    *
    * - A case class with name `NameStorage`.
    * - This class will have a member named `name` (all lower case), with type
    *   `T`.
    *
    * Please note that the type `T` must be fully applied. The macro must also
    * be applied to a case class, and that case class must define a single
    * value member `opaque`.
    *
    * By way of example, consider the following macro invocation:
    *
    * {{{
    *   @opaque case class Backref(opaque: Vector[Int])
    * }}}
    *
    * This will generate the following code (along with required imports):
    *
    * {{{
    * sealed case class BackrefStorage() {
    *   val backref: mutable.Map[Int, Vector[Int]] = mutable.Map()
    * }
    * }}}
    */
  @compileTimeOnly("please enable macro paradise to expand macro annotations")
  class opaque extends StaticAnnotation {
    def macroTransform(annottees: Any*): Any = macro OpaqueMacro.impl
  }
  object OpaqueMacro {
    def impl(c: whitebox.Context)(annottees: c.Expr[Any]*): c.Expr[Any] = {
      import c.universe._
      val members = annottees.map(_.tree).toList

      if (members.length != 1) {
        c.error(
          c.enclosingPosition,
          "You must annotate one definition with the @opaque macro"
        )
        c.Expr(q"..$members")
      }
      val annotatedItem = members.head

      /** This function finds the type of the `opaque` member in the case class
        * to which the macro has been applied.
        *
        * @param template the body of the case class to which the macro has been
        *                 applied
        * @return the type of the member `opaque`
        */
      def findOpaqueType(template: Template): Tree = {
        val valDefs = template.body.collect {
          case v: ValDef if v.name == TermName("opaque") => v
        }

        if (valDefs.length != 1) {
          c.error(
            c.enclosingPosition,
            "You must define a constructor member called `opaque` that " +
            "specifies your opaque type"
          )
        }

        val valDef = valDefs.head
        valDef.tpt
      }

      /** Generates the definition of opaque storage from the provided class.
        *
        * @param classDef the member to which the macro has been applied
        * @return the definition of opaque storage for `classDef`
        */
      def processOpaqueClass(classDef: ClassDef): c.Expr[Any] = {
        val inputName  = classDef.name
        val className  = TypeName(inputName.toString + "Storage")
        val memberName = TermName(inputName.toString.toLowerCase)
        val opaqueType = findOpaqueType(classDef.impl)

        val outputBody =
          q"""
            import scala.collection.mutable
            sealed case class $className() {
              val $memberName: mutable.Map[Int, $opaqueType] = mutable.Map()
            }
           """

        c.Expr(outputBody)
      }

      annotatedItem match {
        case classDef: ClassDef => processOpaqueClass(classDef)
        case _ => {
          c.error(
            c.enclosingPosition,
            "You must provide a class definition to the @opaque macro"
          )
          c.Expr(annotatedItem)
        }
      }
    }
  }
}
