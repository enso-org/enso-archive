package org.enso.graph.definition

import scala.annotation.StaticAnnotation
import scala.annotation.compileTimeOnly
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

      members.head match {
        case param: ClassDef => {
          println(showRaw(members.head))

          annottees.head
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
