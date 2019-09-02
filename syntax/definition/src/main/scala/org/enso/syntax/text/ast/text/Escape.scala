package org.enso.syntax.text.ast.text

import org.enso.data.ADT
import org.enso.syntax.text.AST
import org.enso.syntax.text.AST.Text.Segment
import org.enso.syntax.text.ast.Repr.R

//object Escape {
//  type Escape = Segment.Escape
//  case object Slash           extends Escape { val repr = "\\\\" }
//  case object Quote           extends Escape { val repr = "\\'" }
//  case object RawQuote        extends Escape { val repr = "\\\"" }
//  case class Number(int: Int) extends Escape { val repr = '\\' + int.toString }
//  case class Invalid(str: String) extends Escape with AST.Invalid {
//    val repr               = '\\' + str
//    def map(f: AST => AST) = this
//  }
//
//  // Reference: https://en.wikipedia.org/wiki/String_literal
//  sealed trait Unicode extends Escape
//  object Unicode {
//    abstract class U(val pfx: String, val sfx: String) extends Unicode {
//      val digits: String
//      val repr = R + "\\" + pfx + digits + sfx
//
//    }
//    final case class U16 private (digits: String) extends U("u", "")
//    final case class U32 private (digits: String) extends U("U", "")
//    final case class U21 private (digits: String) extends U("u{", "}")
//    final case class InvalidU16 private (digits: String)
//        extends U("u", "")
//        with AST.Invalid {
//      def map(f: AST => AST) = this
//    }
//    final case class InvalidU32 private (digits: String)
//        extends U("U", "")
//        with AST.Invalid {
//      def map(f: AST => AST) = this
//    }
//    final case class InvalidU21 private (digits: String)
//        extends U("u{", "}")
//        with AST.Invalid {
//      def map(f: AST => AST) = this
//    }
//
//    object Validator {
//      val hexChars = (('a' to 'f') ++ ('A' to 'F') ++ ('0' to '9')).toSet
//      def isHexChar(char: Char) =
//        hexChars.contains(char)
//    }
//
//    object U16 {
//      def apply(digits: String): Unicode =
//        if (validate(digits)) new U16(digits) else InvalidU16(digits)
//      def validate(digits: String) = {
//        import Validator._
//        val validLength = digits.length == 4
//        val validChars  = digits.map(isHexChar).forall(identity)
//        validLength && validChars
//      }
//    }
//    object U32 {
//      def apply(digits: String): Unicode =
//        if (validate(digits)) new U32(digits) else InvalidU32(digits)
//      def validate(digits: String) = {
//        import Validator._
//        val validLength = digits.length == 8
//        val validPrefix = digits.startsWith("00")
//        val validChars  = digits.map(isHexChar).forall(identity)
//        validLength && validPrefix && validChars
//      }
//    }
//    object U21 {
//      def apply(digits: String): Unicode =
//        if (validate(digits)) new U21(digits) else InvalidU21(digits)
//      def validate(digits: String) = {
//        import Validator._
//        val validLength = digits.length >= 1 && digits.length <= 6
//        val validChars  = digits.map(isHexChar).forall(identity)
//        validLength && validChars
//      }
//    }
//  }
//
//  abstract class Simple(val code: Int) extends Escape {
//    val name = toString()
//    val repr = '\\' + name
//  }
//
//  // Reference: https://en.wikipedia.org/wiki/String_literal
//  sealed trait Character extends Escape
//  object Character {
//    case object a extends Simple('\u0007') with Character
//    case object b extends Simple('\u0008') with Character
//    case object f extends Simple('\u000C') with Character
//    case object n extends Simple('\n') with Character
//    case object r extends Simple('\r') with Character
//    case object t extends Simple('\u0009') with Character
//    case object v extends Simple('\u000B') with Character
//    case object e extends Simple('\u001B') with Character
//    val codes = ADT.constructors[Character]
//  }
//
//  // Reference: https://en.wikipedia.org/wiki/Control_character
//  sealed trait Control extends Escape
//  object Control {
//    case object NUL extends Simple(0x00) with Control
//    case object SOH extends Simple(0x01) with Control
//    case object STX extends Simple(0x02) with Control
//    case object ETX extends Simple(0x03) with Control
//    case object EOT extends Simple(0x04) with Control
//    case object ENQ extends Simple(0x05) with Control
//    case object ACK extends Simple(0x06) with Control
//    case object BEL extends Simple(0x07) with Control
//    case object BS  extends Simple(0x08) with Control
//    case object TAB extends Simple(0x09) with Control
//    case object LF  extends Simple(0x0A) with Control
//    case object VT  extends Simple(0x0B) with Control
//    case object FF  extends Simple(0x0C) with Control
//    case object CR  extends Simple(0x0D) with Control
//    case object SO  extends Simple(0x0E) with Control
//    case object SI  extends Simple(0x0F) with Control
//    case object DLE extends Simple(0x10) with Control
//    case object DC1 extends Simple(0x11) with Control
//    case object DC2 extends Simple(0x12) with Control
//    case object DC3 extends Simple(0x13) with Control
//    case object DC4 extends Simple(0x14) with Control
//    case object NAK extends Simple(0x15) with Control
//    case object SYN extends Simple(0x16) with Control
//    case object ETB extends Simple(0x17) with Control
//    case object CAN extends Simple(0x18) with Control
//    case object EM  extends Simple(0x19) with Control
//    case object SUB extends Simple(0x1A) with Control
//    case object ESC extends Simple(0x1B) with Control
//    case object FS  extends Simple(0x1C) with Control
//    case object GS  extends Simple(0x1D) with Control
//    case object RS  extends Simple(0x1E) with Control
//    case object US  extends Simple(0x1F) with Control
//    case object DEL extends Simple(0x7F) with Control
//    val codes = ADT.constructors[Control]
//  }
//}
