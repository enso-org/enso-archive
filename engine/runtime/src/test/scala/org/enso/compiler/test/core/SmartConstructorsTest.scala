package org.enso.compiler.test.core

import cats.data.NonEmptyList
import org.enso.compiler.core.Core
import org.enso.compiler.core.Core.Node.Utility
import org.enso.core.CoreGraph.DefinitionGen.Node.{Shape => NodeShape}
import org.enso.core.CoreGraph.{DefinitionGen => CoreDef}
import org.enso.graph.{Graph => PrimGraph}
import org.enso.syntax.text.{Location => AstLocation}
import org.scalatest.{Assertion, BeforeAndAfterEach, Matchers, WordSpec}

class SmartConstructorsTest
    extends WordSpec
    with Matchers
    with BeforeAndAfterEach {

  // === Test Setup ===========================================================

  import Core._
  import CoreDef.Link.Shape._
  import CoreDef.Node.Location._
  import CoreDef.Node.ParentLinks._
  import CoreDef.Node.Shape._
  import PrimGraph.Component.Refined._

  // === Useful Constants =====================================================
  val constantLocationStart = 201
  val constantLocationEnd   = 1337
  val dummyLocation: CoreDef.Node.LocationVal[Graph] =
    CoreDef.Node.LocationVal(constantLocationStart, constantLocationEnd)

  // === Utilities ============================================================

  /** Expresses that a node should fail to construct, and provide the erroneous
    * core structures in [[errList]].
    *
    * @param maybeErr the value coming out of the smart constructor being tested
    * @param errList a meta list (on the core graph) containing the erroneous
    *                core
    * @param core an implicit core instance
    * @tparam T the particular node shape being tested
    * @return a success if [[maybeErr]] is a failure and the contained error
    *         matches [[errList]], otherwise a failure
    */
  def shouldFailWith[T <: CoreDef.Node.Shape](
    maybeErr: Core.ConsErrOr[T],
    errList: RefinedNode[MetaList]
  )(implicit core: Core): Assertion = {
    maybeErr match {
      case Left(err) =>
        err.erroneousCore.target match {
          case NodeShape.MetaList.any(xs) =>
            if (Utility.listsAreEqual(xs, errList)) {
              succeed
            } else {
              fail
            }
          case _ => fail
        }
      case Right(_) => fail
    }
  }

  // === Tests for Node Smart Constructors (Meta Shapes) ======================

  "Empty nodes" should {
    implicit val core: Core = new Core()
    val emptyNode           = Node.New.Empty()

    "have valid fields" in {
      emptyNode.location shouldEqual Node.Constants.invalidLocation
      emptyNode.parents shouldEqual Vector()
    }
  }

  "Meta list nodes" should {
    implicit val core: Core = new Core()
    val emptyNode           = Node.New.Empty()
    val nilNode             = Node.New.MetaNil()
    val listNode            = Node.New.MetaList(emptyNode, nilNode).right.get

    "have valid fields" in {
      listNode.location shouldEqual Node.Constants.invalidLocation
      listNode.parents shouldEqual Vector()
    }

    "have properly connected links" in {
      listNode.head.source shouldEqual listNode
      listNode.head.target shouldEqual emptyNode
      listNode.tail.source shouldEqual listNode
      listNode.tail.target shouldEqual nilNode
    }

    "have children parented to the node" in {
      emptyNode.parents should contain(listNode.head.ix)
      nilNode.parents should contain(listNode.tail.ix)
    }

    "fail to construct if tail isn't a valid meta list" in {
      val failNode = Node.New.MetaList(emptyNode, emptyNode)

      shouldFailWith(failNode, Utility.coreListFrom(emptyNode))
    }
  }

  "Meta nil nodes" should {
    implicit val core: Core = new Core()
    val nilNode             = Node.New.MetaNil()

    "have valid fields" in {
      nilNode.location shouldEqual Node.Constants.invalidLocation
      nilNode.parents shouldEqual Vector()
    }
  }

  "Meta true nodes" should {
    implicit val core: Core = new Core()
    val trueNode            = Node.New.MetaTrue()

    "have valid fields" in {
      trueNode.location shouldEqual Node.Constants.invalidLocation
      trueNode.parents shouldEqual Vector()
    }
  }

  "Meta false nodes" should {
    implicit val core: Core = new Core()
    val falseNode           = Node.New.MetaFalse()

    "have valid fields" in {
      falseNode.location shouldEqual Node.Constants.invalidLocation
      falseNode.parents shouldEqual Vector()
    }
  }

  // === Tests for Node Smart Constructors (Literals) =========================

  "Numeric literal nodes" should {
    implicit val core: Core = new Core()
    val numLit              = "1e-262"
    val number              = Node.New.NumericLiteral(numLit, dummyLocation)

    "have valid fields" in {
      number.number shouldEqual numLit
      number.location shouldEqual dummyLocation
      number.parents shouldEqual Vector()
    }
  }

  "Text literal nodes" should {
    implicit val core: Core = new Core()
    val textLit             = "FooBarBaz"
    val text                = Node.New.TextLiteral(textLit, dummyLocation)

    "have valid fields" in {
      text.text shouldEqual textLit
      text.location shouldEqual dummyLocation
      text.parents shouldEqual Vector()
    }
  }

  "Foreign code literal nodes" should {
    implicit val core: Core = new Core()
    val codeLit             = "lambda x: x + 1"
    val code                = Node.New.ForeignCodeLiteral(codeLit, dummyLocation)

    "have valid fields" in {
      code.code shouldEqual codeLit
      code.location shouldEqual dummyLocation
      code.parents shouldEqual Vector()
    }
  }

  // === Tests for Node Smart Constructors (Names) ============================

  "Name nodes" should {
    implicit val core: Core = new Core()
    val nameLit             = "MyType"
    val name                = Node.New.Name(nameLit, dummyLocation)

    "have valid fields" in {
      name.nameLiteral shouldEqual nameLit
      name.location shouldEqual dummyLocation
      name.parents shouldEqual Vector()
    }
  }

  "This name nodes" should {
    implicit val core: Core = new Core()
    val thisName            = Node.New.ThisName(dummyLocation)

    "have valid fields" in {
      thisName.location shouldEqual dummyLocation
      thisName.parents shouldEqual Vector()
    }
  }

  "Here name nodes" should {
    implicit val core: Core = new Core()
    val hereName            = Node.New.HereName(dummyLocation)

    "have valid fields" in {
      hereName.location shouldEqual dummyLocation
      hereName.parents shouldEqual Vector()
    }
  }

  // === Tests for Node Smart Constructors (Module) ===========================

  "Module nodes" should {
    implicit val core: Core = new Core()
    val importNil           = Node.New.MetaNil()
    val defNil              = Node.New.MetaNil()
    val name                = Node.New.Name("MyModule", dummyLocation)

    val module =
      Node.New.ModuleDef(name, importNil, defNil, dummyLocation).right.get

    "have valid fields" in {
      module.location shouldEqual dummyLocation
      module.parents shouldEqual Vector()
    }

    "have properly connected links" in {
      module.name.source shouldEqual module
      module.name.target shouldEqual name
      module.imports.source shouldEqual module
      module.imports.target shouldEqual importNil
      module.definitions.source shouldEqual module
      module.definitions.target shouldEqual defNil
    }

    "have children parented to the node" in {
      name.parents should contain(module.name.ix)
      importNil.parents should contain(module.imports.ix)
      defNil.parents should contain(module.definitions.ix)
    }

    "fail to construct if imports isn't a meta list" in {
      val failNode = Node.New.ModuleDef(name, name, defNil, dummyLocation)
      shouldFailWith(failNode, Utility.coreListFrom(name))
    }

    "fail if construct definitions isn't a meta list" in {
      val failNode = Node.New.ModuleDef(name, importNil, name, dummyLocation)
      shouldFailWith(failNode, Utility.coreListFrom(name))
    }
  }

  "Import nodes" should {
    implicit val core: Core = new Core()
    val segmentsNil         = Node.New.MetaNil()
    val empty               = Node.New.Empty()
    val imp =
      Node.New.Import(segmentsNil, dummyLocation).right.get

    "have valid fields" in {
      imp.location shouldEqual dummyLocation
      imp.parents shouldEqual Vector()
    }

    "have properly connected links" in {
      imp.segments.source shouldEqual imp
      imp.segments.target shouldEqual segmentsNil
    }

    "have children parented to the node" in {
      segmentsNil.parents should contain(imp.segments.ix)
    }

    "fail to construct if segments isn't a valid meta list" in {
      val errNode = Node.New.Import(empty, dummyLocation)

      shouldFailWith(errNode, Utility.coreListFrom(empty))
    }
  }

  "Top-level binding nodes" should {
    implicit val core: Core = new Core()
    val emptyModule         = Node.New.Empty()
    val bindingSrc          = Node.New.Empty()
    val bindingTgt          = Node.New.Empty()
    val binding =
      Node.New.Binding(bindingSrc, bindingTgt, dummyLocation)
    val topLevelBinding =
      Node.New.TopLevelBinding(emptyModule, binding, dummyLocation).right.get

    "have valid fields" in {
      topLevelBinding.location shouldEqual dummyLocation
      topLevelBinding.parents shouldEqual Vector()
    }

    "have properly connected links" in {
      topLevelBinding.module.source shouldEqual topLevelBinding
      topLevelBinding.module.target shouldEqual emptyModule
      topLevelBinding.binding.source shouldEqual topLevelBinding
      topLevelBinding.binding.target shouldEqual binding
    }

    "have children parented to the node" in {
      emptyModule.parents should contain(topLevelBinding.module.ix)
      binding.parents should contain(topLevelBinding.binding.ix)
    }

    "fail to construct if binding isn't a valid binding" in {
      val errNode =
        Node.New.TopLevelBinding(emptyModule, bindingSrc, dummyLocation)

      shouldFailWith(errNode, Utility.coreListFrom(bindingSrc))
    }
  }

  // === Tests for Node Smart Constructors (Type Definitions) =================

  "Atom definition nodes" should {
    implicit val core: Core = new Core()

    val name    = Node.New.Empty()
    val argName = Node.New.Empty()
    val args    = Utility.coreListFrom(argName)

    val atomDef = Node.New.AtomDef(name, args, dummyLocation).right.get

    "have valid fields" in {
      atomDef.location shouldEqual dummyLocation
      atomDef.parents shouldEqual Vector()
    }

    "have properly connected links" in {
      atomDef.name.source shouldEqual atomDef
      atomDef.name.target shouldEqual name
      atomDef.args.source shouldEqual atomDef
      atomDef.args.target shouldEqual args
    }

    "have children parented to the node" in {
      name.parents should contain(atomDef.name.ix)
      args.parents should contain(atomDef.args.ix)
    }

    "fail to construct if args is not a valid meta list" in {
      val errNode = Node.New.AtomDef(name, argName, dummyLocation)
      shouldFailWith(errNode, Utility.coreListFrom(argName))
    }
  }

  "Type definition nodes" should {
    implicit val core: Core = new Core()

    val name    = Node.New.Empty()
    val tParam  = Node.New.Empty()
    val tParams = Utility.coreListFrom(tParam)

    val bodyExpr = Node.New.Empty()
    val body     = Utility.coreListFrom(bodyExpr)

    val typeDef = Node.New.TypeDef(name, tParams, body, dummyLocation).right.get

    "have valid fields" in {
      typeDef.location shouldEqual dummyLocation
      typeDef.parents shouldEqual Vector()
    }

    "have properly connected links" in {
      typeDef.name.source shouldEqual typeDef
      typeDef.name.target shouldEqual name
      typeDef.typeParams.source shouldEqual typeDef
      typeDef.typeParams.target shouldEqual tParams
      typeDef.body.source shouldEqual typeDef
      typeDef.body.target shouldEqual body
    }

    "have children parented to the node" in {
      name.parents should contain(typeDef.name.ix)
      tParams.parents should contain(typeDef.typeParams.ix)
      body.parents should contain(typeDef.body.ix)
    }

    "fail to construct of the type params are not a valid meta list" in {
      val errNode = Node.New.TypeDef(name, name, body, dummyLocation)
      shouldFailWith(errNode, Utility.coreListFrom(name))
    }
    "fail to construct if the body is not a valid meta list" in {
      val errNode = Node.New.TypeDef(name, tParams, name, dummyLocation)
      shouldFailWith(errNode, Utility.coreListFrom(name))
    }
  }

  // === Tests for Node Smart Constructors (Typing) ===========================

  "Type ascription nodes" should {
    implicit val core: Core = new Core()

    val typed      = Node.New.Empty()
    val sig        = Node.New.Empty()
    val ascription = Node.New.TypeAscription(typed, sig, dummyLocation)

    "have valid fields" in {
      ascription.location shouldEqual dummyLocation
      ascription.parents shouldEqual Vector()
    }

    "have properly connected links" in {
      ascription.typed.source shouldEqual ascription
      ascription.typed.target shouldEqual typed
      ascription.sig.source shouldEqual ascription
      ascription.sig.target shouldEqual sig
    }

    "have children parented to the node" in {
      typed.parents should contain(ascription.typed.ix)
      sig.parents should contain(ascription.sig.ix)
    }
  }

  "Context ascription nodes" should {
    implicit val core: Core = new Core()

    val typed      = Node.New.Empty()
    val context    = Node.New.Empty()
    val ascription = Node.New.ContextAscription(typed, context, dummyLocation)

    "have valid fields" in {
      ascription.location shouldEqual dummyLocation
      ascription.parents shouldEqual Vector()
    }

    "have properly connected links" in {
      ascription.typed.source shouldEqual ascription
      ascription.typed.target shouldEqual typed
      ascription.context.source shouldEqual ascription
      ascription.context.target shouldEqual context
    }

    "have children parented to the node" in {
      typed.parents should contain(ascription.typed.ix)
      context.parents should contain(ascription.context.ix)
    }
  }

  "Typeset memeber nodes" should {
    implicit val core: Core = new Core()

    val label      = Node.New.Empty()
    val memberType = Node.New.Empty()
    val value      = Node.New.Empty()

    val typesetMember =
      Node.New.TypesetMember(label, memberType, value, dummyLocation)

    "have valid fields" in {
      typesetMember.location shouldEqual dummyLocation
      typesetMember.parents shouldEqual Vector()
    }

    "have properly connected links" in {
      typesetMember.label.source shouldEqual typesetMember
      typesetMember.label.target shouldEqual label
      typesetMember.memberType.source shouldEqual typesetMember
      typesetMember.memberType.target shouldEqual memberType
      typesetMember.value.source shouldEqual typesetMember
      typesetMember.value.target shouldEqual value
    }

    "have children parented to the node" in {
      label.parents should contain(typesetMember.label.ix)
      memberType.parents should contain(typesetMember.memberType.ix)
      value.parents should contain(typesetMember.value.ix)
    }
  }

  "Typset subsumption nodes" should {
    implicit val core: Core = new Core()

    val left  = Node.New.Empty()
    val right = Node.New.Empty()

    val op = Node.New.TypesetSubsumption(left, right, dummyLocation)

    "have valid fields" in {
      op.location shouldEqual dummyLocation
      op.parents shouldEqual Vector()
    }

    "have properly connected links" in {
      op.left.source shouldEqual op
      op.left.target shouldEqual left
      op.right.source shouldEqual op
      op.right.target shouldEqual right
    }

    "have children parented to the node" in {
      left.parents should contain(op.left.ix)
      right.parents should contain(op.right.ix)
    }
  }

  "Typset equality nodes" should {
    implicit val core: Core = new Core()

    val left  = Node.New.Empty()
    val right = Node.New.Empty()

    val op = Node.New.TypesetEquality(left, right, dummyLocation)

    "have valid fields" in {
      op.location shouldEqual dummyLocation
      op.parents shouldEqual Vector()
    }

    "have properly connected links" in {
      op.left.source shouldEqual op
      op.left.target shouldEqual left
      op.right.source shouldEqual op
      op.right.target shouldEqual right
    }

    "have children parented to the node" in {
      left.parents should contain(op.left.ix)
      right.parents should contain(op.right.ix)
    }
  }

  "Typset concat nodes" should {
    implicit val core: Core = new Core()

    val left  = Node.New.Empty()
    val right = Node.New.Empty()

    val op = Node.New.TypesetConcat(left, right, dummyLocation)

    "have valid fields" in {
      op.location shouldEqual dummyLocation
      op.parents shouldEqual Vector()
    }

    "have properly connected links" in {
      op.left.source shouldEqual op
      op.left.target shouldEqual left
      op.right.source shouldEqual op
      op.right.target shouldEqual right
    }

    "have children parented to the node" in {
      left.parents should contain(op.left.ix)
      right.parents should contain(op.right.ix)
    }
  }

  "Typset union nodes" should {
    implicit val core: Core = new Core()

    val left  = Node.New.Empty()
    val right = Node.New.Empty()

    val op = Node.New.TypesetUnion(left, right, dummyLocation)

    "have valid fields" in {
      op.location shouldEqual dummyLocation
      op.parents shouldEqual Vector()
    }

    "have properly connected links" in {
      op.left.source shouldEqual op
      op.left.target shouldEqual left
      op.right.source shouldEqual op
      op.right.target shouldEqual right
    }

    "have children parented to the node" in {
      left.parents should contain(op.left.ix)
      right.parents should contain(op.right.ix)
    }
  }

  "Typset intersection nodes" should {
    implicit val core: Core = new Core()

    val left  = Node.New.Empty()
    val right = Node.New.Empty()

    val op = Node.New.TypesetIntersection(left, right, dummyLocation)

    "have valid fields" in {
      op.location shouldEqual dummyLocation
      op.parents shouldEqual Vector()
    }

    "have properly connected links" in {
      op.left.source shouldEqual op
      op.left.target shouldEqual left
      op.right.source shouldEqual op
      op.right.target shouldEqual right
    }

    "have children parented to the node" in {
      left.parents should contain(op.left.ix)
      right.parents should contain(op.right.ix)
    }
  }

  "Typset subtraction nodes" should {
    implicit val core: Core = new Core()

    val left  = Node.New.Empty()
    val right = Node.New.Empty()

    val op = Node.New.TypesetSubtraction(left, right, dummyLocation)

    "have valid fields" in {
      op.location shouldEqual dummyLocation
      op.parents shouldEqual Vector()
    }

    "have properly connected links" in {
      op.left.source shouldEqual op
      op.left.target shouldEqual left
      op.right.source shouldEqual op
      op.right.target shouldEqual right
    }

    "have children parented to the node" in {
      left.parents should contain(op.left.ix)
      right.parents should contain(op.right.ix)
    }
  }

  // === Tests for Node Smart Constructors (Function) =========================

  "Lambda nodes" should {
    implicit val core: Core = new Core()

    val arg  = Node.New.Empty()
    val body = Node.New.Empty()

    val lambda = Node.New.Lambda(arg, body, dummyLocation)

    "have valid fields" in {
      lambda.location shouldEqual dummyLocation
      lambda.parents shouldEqual Vector()
    }

    "have properly connected links" in {
      lambda.arg.source shouldEqual lambda
      lambda.arg.target shouldEqual arg
      lambda.body.source shouldEqual lambda
      lambda.body.target shouldEqual body
    }

    "have children parented to the node" in {
      arg.parents should contain(lambda.arg.ix)
      body.parents should contain(lambda.body.ix)
    }
  }

  "Function definition nodes" should {
    implicit val core: Core = new Core()

    val name = Node.New.Empty()
    val arg  = Node.New.Empty()
    val args = Utility.coreListFrom(arg)
    val body = Node.New.Empty()

    val functionDef =
      Node.New.FunctionDef(name, args, body, dummyLocation).right.get

    "have valid fields" in {
      functionDef.location shouldEqual dummyLocation
      functionDef.parents shouldEqual Vector()
    }

    "have properly connected links" in {
      functionDef.name.source shouldEqual functionDef
      functionDef.name.target shouldEqual name
      functionDef.args.source shouldEqual functionDef
      functionDef.args.target shouldEqual args
      functionDef.body.source shouldEqual functionDef
      functionDef.body.target shouldEqual body
    }

    "have children parented to the node" in {
      name.parents should contain(functionDef.name.ix)
      args.parents should contain(functionDef.args.ix)
      body.parents should contain(functionDef.body.ix)
    }

    "fail to construct if args is not a meta list" in {
      val errNode = Node.New.FunctionDef(name, name, body, dummyLocation)
      shouldFailWith(errNode, Utility.coreListFrom(name))
    }
  }

  "Method definition nodes" should {
    implicit val core: Core = new Core()

    val targetPath = Node.New.Empty()
    val name       = Node.New.Empty()
    val lamArg     = Node.New.Empty()
    val lamBody    = Node.New.Empty()
    val function   = Node.New.Lambda(lamArg, lamBody, dummyLocation)

    val methodDef =
      Node.New.MethodDef(targetPath, name, function, dummyLocation).right.get

    "have valid fields" in {
      methodDef.location shouldEqual dummyLocation
      methodDef.parents shouldEqual Vector()
    }

    "have properly connected links" in {
      methodDef.targetPath.source shouldEqual methodDef
      methodDef.targetPath.target shouldEqual targetPath
      methodDef.name.source shouldEqual methodDef
      methodDef.name.target shouldEqual name
      methodDef.function.source shouldEqual methodDef
      methodDef.function.target shouldEqual function
    }

    "have children parented to the node" in {
      targetPath.parents should contain(methodDef.targetPath.ix)
      name.parents should contain(methodDef.name.ix)
      function.parents should contain(methodDef.function.ix)
    }

    "fail to construct if function is not a valid function representation" in {
      val errNode = Node.New.MethodDef(targetPath, name, name, dummyLocation)
      shouldFailWith(errNode, Utility.coreListFrom(name))
    }
  }

  // === Tests for Node Smart Constructors (Def-Site Args) ====================

  // === Tests for Node Utility Functions =====================================

  "Lists" should {
    implicit val core: Core = new Core()
    val empty1              = Node.New.Empty().wrapped
    val empty2              = Node.New.Empty().wrapped

    val list1 = Utility.coreListFrom(NonEmptyList(empty1, List(empty2)))
    val list2 = Utility.coreListFrom(NonEmptyList(empty1, List(empty2)))
    val list3 = Utility.coreListFrom(NonEmptyList(empty2, List(empty1)))

    "be defined as equal when the child nodes are equal" in {
      Utility.listsAreEqual(list1, list2) shouldEqual true
    }

    "be defined as not equal when the child nodes are not equal" in {
      Utility.listsAreEqual(list1, list3) shouldEqual false
    }
  }

  "Nodes for meta lists" should {
    implicit val core: Core = new Core()
    val emptyNode           = Node.New.Empty()
    val nilNode             = Node.New.MetaNil()
    val consNode            = Node.New.MetaList(emptyNode, nilNode).right.get

    "be correctly identified" in {
      Utility.isListNode(emptyNode) shouldEqual false
      Utility.isListNode(nilNode) shouldEqual true
      Utility.isListNode(consNode) shouldEqual true
    }
  }

  "Core lists" should {
    implicit val core: Core = new Core()
    val emptyNode1          = Node.New.Empty().wrapped
    val emptyNode2          = Node.New.Empty().wrapped
    val emptyNode3          = Node.New.Empty().wrapped

    val listOfOne = Utility.coreListFrom(emptyNode1)
    val listOfMany = Utility
      .coreListFrom(NonEmptyList(emptyNode1, List(emptyNode2, emptyNode3)))

    "be able to be constructed from arbitrary nodes" in {
      listOfOne.head.target shouldEqual emptyNode1

      listOfOne.tail.target match {
        case NodeShape.MetaNil.any(_) =>
        case _                        => fail
      }

      listOfMany.head.target shouldEqual emptyNode1

      listOfMany.tail.target match {
        case NodeShape.MetaList.any(e2) =>
          e2.head.target shouldEqual emptyNode2

          e2.tail.target match {
            case NodeShape.MetaList.any(e3) =>
              e3.head.target shouldEqual emptyNode3
              e3.tail.target match {
                case NodeShape.MetaNil.any(_) => succeed
                case _                        => fail
              }
            case _ => fail
          }
        case _ => fail
      }
    }
  }

  // === Tests for Node Conversions ===========================================

  "AST locations" should {
    val startLoc = 1232
    val endLoc   = 1337

    val astLoc  = AstLocation(startLoc, endLoc)
    val coreLoc = CoreDef.Node.LocationVal(startLoc, endLoc)

    "be converted losslessly to core locations" in {
      Node.Conversions.astLocationToNodeLocation(astLoc) shouldEqual coreLoc
    }
  }

  // === Tests for Link Smart Constructors ====================================

  "Links" should {
    implicit val core: Core = new Core()

    "be able to be made with a source and target" in {
      val n1 = Node.New.Empty()
      val n2 = Node.New.Empty()

      val link = Link.New.Connected(n1, n2)

      link.source shouldEqual n1
      link.target shouldEqual n2
    }

    "be able to be made with only a source" in {
      val sourceNode = Node.New.MetaNil()

      val link = Link.New.Disconnected(sourceNode)

      link.source shouldEqual sourceNode

      link.target match {
        case NodeShape.Empty.any(_) => succeed
        case _                      => fail
      }
    }
  }
}
