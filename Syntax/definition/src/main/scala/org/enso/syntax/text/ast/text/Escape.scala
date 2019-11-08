package org.enso.syntax.text.ast.text

import org.enso.flexer.ADT
import org.enso.syntax.text.ast.text.Escape.Slash.toString

sealed trait Escape2 {
  val repr: String
}

object Escape2 {

  final case class Invalid(str: String) extends Escape2 {
    val repr = str
  }

  final case class Number(int: Int) extends Escape2 {
    val repr = int.toString
  }

  // Reference: https://en.wikipedia.org/wiki/String_literal
  sealed trait Unicode2 extends Escape2
  object Unicode2 {

    final case class Invalid(unicode: Unicode2) extends Unicode2 {
      val repr = unicode.repr
    }

    type U16 = _U16
    final case class _U16(digits: String) extends Unicode2 {
      val pfx  = "u"
      val sfx  = ""
      val repr = pfx + digits + sfx
    }

    type U32 = _U32
    final case class _U32(digits: String) extends Unicode2 {
      val pfx  = "U"
      val sfx  = ""
      val repr = pfx + digits + sfx
    }

    type U21 = _U21
    final case class _U21(digits: String) extends Unicode2 {
      val pfx  = "u{"
      val sfx  = "}"
      val repr = pfx + digits + sfx
    }

    object Validator {
      val hexChars =
        (('a' to 'f') ++ ('A' to 'F') ++ ('0' to '9')).toSet
      def isHexChar(char: Char) =
        hexChars.contains(char)
    }

    object U16 {
      def apply(digits: String): Unicode2 =
        if (validate(digits)) _U16(digits)
        else Invalid(_U16(digits))
      def validate(digits: String) = {
        import Validator._
        val validLength = digits.length == 4
        val validChars  = digits.forall(isHexChar)
        validLength && validChars
      }
    }
    object U32 {
      def apply(digits: String): Unicode2 =
        if (validate(digits)) _U32(digits)
        else Invalid(_U32(digits))
      def validate(digits: String) = {
        import Validator._
        val validLength = digits.length == 8
        val validPrefix = digits.startsWith("00")
        val validChars  = digits.forall(isHexChar)
        validLength && validPrefix && validChars
      }
    }
    object U21 {
      def apply(digits: String): Unicode2 =
        if (validate(digits)) _U21(digits)
        else Invalid(_U21(digits))
      def validate(digits: String) = {
        import Validator._
        val validLength = digits.length >= 1 && digits.length <= 6
        val validChars  = digits.forall(isHexChar)
        validLength && validChars
      }
    }
  }
//  case object Quote extends Escape2 {
//    val code: Int     = '\''
//    def name          = toString
//    override val repr = "\'"
//  }
//  case object RawQuote extends Escape2 {
//    val code: Int     = '"'
//    def name          = toString
//    override val repr = "\""
//  }

  // Reference: https://en.wikipedia.org/wiki/String_literal
//  sealed trait Character
//  object Character {
//    case object a extends Escape2 with Character {
//      val code: Int     = '\u0007'
//      def name          = toString
//      override val repr = name
//    }
//    case object b extends Escape2 with Character {
//      val code: Int     = '\u0008'
//      def name          = toString
//      override val repr = name
//    }
//    case object f extends Escape2 with Character {
//      val code: Int     = '\u000C'
//      def name          = toString
//      override val repr = name
//    }
//    case object n extends Escape2 with Character {
//      val code: Int     = '\n'
//      def name          = toString
//      override val repr = name
//    }
//    case object r extends Escape2 with Character {
//      val code: Int     = '\r'
//      def name          = toString
//      override val repr = name
//    }
//    case object t extends Escape2 with Character {
//      val code: Int     = '\u0009'
//      def name          = toString
//      override val repr = name
//    }
//    case object v extends Escape2 with Character {
//      val code: Int     = '\u000B'
//      def name          = toString
//      override val repr = name
//    }
//    case object e extends Escape2 with Character {
//      val code: Int     = '\u001B'
//      def name          = toString
//      override val repr = name
//    }
//    val codes = ADT.constructors[Character]
//  }

  // Reference: https://en.wikipedia.org/wiki/Control_character
//  sealed trait Control // extends Simple
//  object Control {
//    case object NUL extends Escape2 with Control {
//      val code: Int     = 0x00
//      def name          = toString
//      override val repr = name
//    }
//    case object SOH extends Escape2 with Control {
//      val code: Int     = 0x01
//      def name          = toString
//      override val repr = name
//    }
//    case object STX extends Escape2 with Control {
//      val code: Int     = 0x02
//      def name          = toString
//      override val repr = name
//    }
//    case object ETX extends Escape2 with Control {
//      val code: Int     = 0x03
//      def name          = toString
//      override val repr = name
//    }
//    case object EOT extends Escape2 with Control {
//      val code: Int     = 0x04
//      def name          = toString
//      override val repr = name
//    }
//    case object ENQ extends Escape2 with Control {
//      val code: Int     = 0x05
//      def name          = toString
//      override val repr = name
//    }
//    case object ACK extends Escape2 with Control {
//      val code: Int     = 0x06
//      def name          = toString
//      override val repr = name
//    }
//    case object BEL extends Escape2 with Control {
//      val code: Int     = 0x07
//      def name          = toString
//      override val repr = name
//    }
//    case object BS extends Escape2 with Control {
//      val code: Int     = 0x08
//      def name          = toString
//      override val repr = name
//    }
//    case object TAB extends Escape2 with Control {
//      val code: Int     = 0x09
//      def name          = toString
//      override val repr = name
//    }
//    case object LF extends Escape2 with Control {
//      val code: Int     = 0x0A
//      def name          = toString
//      override val repr = name
//    }
//    case object VT extends Escape2 with Control {
//      val code: Int     = 0x0B
//      def name          = toString
//      override val repr = name
//    }
//    case object FF extends Escape2 with Control {
//      val code: Int     = 0x0C
//      def name          = toString
//      override val repr = name
//    }
//    case object CR extends Escape2 with Control {
//      val code: Int     = 0x0D
//      def name          = toString
//      override val repr = name
//    }
//    case object SO extends Escape2 with Control {
//      val code: Int     = 0x0E
//      def name          = toString
//      override val repr = name
//    }
//    case object SI extends Escape2 with Control {
//      val code: Int     = 0x0F
//      def name          = toString
//      override val repr = name
//    }
//    case object DLE extends Escape2 with Control {
//      val code: Int     = 0x10
//      def name          = toString
//      override val repr = name
//    }
//    case object DC1 extends Escape2 with Control {
//      val code: Int     = 0x11
//      def name          = toString
//      override val repr = name
//    }
//    case object DC2 extends Escape2 with Control {
//      val code: Int     = 0x12
//      def name          = toString
//      override val repr = name
//    }
//    case object DC3 extends Escape2 with Control {
//      val code: Int     = 0x13
//      def name          = toString
//      override val repr = name
//    }
//    case object DC4 extends Escape2 with Control {
//      val code: Int     = 0x14
//      def name          = toString
//      override val repr = name
//    }
//    case object NAK extends Escape2 with Control {
//      val code: Int     = 0x15
//      def name          = toString
//      override val repr = name
//    }
//    case object SYN extends Escape2 with Control {
//      val code: Int     = 0x16
//      def name          = toString
//      override val repr = name
//    }
//    case object ETB extends Escape2 with Control {
//      val code: Int     = 0x17
//      def name          = toString
//      override val repr = name
//    }
//    case object CAN extends Escape2 with Control {
//      val code: Int     = 0x18
//      def name          = toString
//      override val repr = name
//    }
//    case object EM extends Escape2 with Control {
//      val code: Int     = 0x19
//      def name          = toString
//      override val repr = name
//    }
//    case object SUB extends Escape2 with Control {
//      val code: Int     = 0x1A
//      def name          = toString
//      override val repr = name
//    }
//    case object ESC extends Escape2 with Control {
//      val code: Int     = 0x1B
//      def name          = toString
//      override val repr = name
//    }
//    case object FS extends Escape2 with Control {
//      val code: Int     = 0x1C
//      def name          = toString
//      override val repr = name
//    }
//    case object GS extends Escape2 with Control {
//      val code: Int     = 0x1D
//      def name          = toString
//      override val repr = name
//    }
//    case object RS extends Escape2 with Control {
//      val code: Int     = 0x1E
//      def name          = toString
//      override val repr = name
//    }
//    case object US extends Escape2 with Control {
//      val code: Int     = 0x1F
//      def name          = toString
//      override val repr = name
//    }
//    case object DEL extends Escape2 with Control {
//      val code: Int     = 0x7F
//      def name          = toString
//      override val repr = name
//    }
//    val codes = ADT.constructors[Control]
//  }
}

//////////////////////////////////////////

sealed trait Escape {
  val repr: String
}

object Escape {

  final case class Invalid(str: String) extends Escape {
    val repr = str
  }

  final case class Number(int: Int) extends Escape {
    val repr = int.toString
  }

  // Reference: https://en.wikipedia.org/wiki/String_literal
  sealed trait Unicode extends Escape
  object Unicode {

    // NOTE [mwu]
    // Name of the class below cannot be Invalid, as we already have
    // Escape.Invalid. And to be able to derive JSON serialization with circe
    // case class names within a trait subtree need to be unique.
    final case class InvalidUnicode(unicode: Unicode) extends Unicode {
      val repr = unicode.repr
    }

    type U16 = _U16
    final case class _U16(digits: String) extends Unicode {
      val pfx  = "u"
      val sfx  = ""
      val repr = pfx + digits + sfx
    }

    type U32 = _U32
    final case class _U32(digits: String) extends Unicode {
      val pfx  = "U"
      val sfx  = ""
      val repr = pfx + digits + sfx
    }

    type U21 = _U21
    final case class _U21(digits: String) extends Unicode {
      val pfx  = "u{"
      val sfx  = "}"
      val repr = pfx + digits + sfx
    }

    object Validator {
      val hexChars =
        (('a' to 'f') ++ ('A' to 'F') ++ ('0' to '9')).toSet
      def isHexChar(char: Char) =
        hexChars.contains(char)
    }

    object U16 {
      def apply(digits: String): Unicode =
        if (validate(digits)) _U16(digits)
        else InvalidUnicode(_U16(digits))
      def validate(digits: String) = {
        import Validator._
        val validLength = digits.length == 4
        val validChars  = digits.forall(isHexChar)
        validLength && validChars
      }
    }
    object U32 {
      def apply(digits: String): Unicode =
        if (validate(digits)) _U32(digits)
        else InvalidUnicode(_U32(digits))
      def validate(digits: String) = {
        import Validator._
        val validLength = digits.length == 8
        val validPrefix = digits.startsWith("00")
        val validChars  = digits.forall(isHexChar)
        validLength && validPrefix && validChars
      }
    }
    object U21 {
      def apply(digits: String): Unicode =
        if (validate(digits)) _U21(digits)
        else InvalidUnicode(_U21(digits))
      def validate(digits: String) = {
        import Validator._
        val validLength = digits.length >= 1 && digits.length <= 6
        val validChars  = digits.forall(isHexChar)
        validLength && validChars
      }
    }
  }

  case object Slash extends Escape {
    val code: Int     = '\\'
    def name          = toString
    override val repr = "\\"
  }
  case object Quote extends Escape {
    val code: Int     = '\''
    def name          = toString
    override val repr = "\'"
  }
  case object RawQuote extends Escape {
    val code: Int     = '"'
    def name          = toString
    override val repr = "\""
  }

  // Reference: https://en.wikipedia.org/wiki/String_literal
  sealed trait Character
  object Character {
    case object a extends Escape with Character {
      val code: Int     = '\u0007'
      def name          = toString
      override val repr = name
    }
    case object b extends Escape with Character {
      val code: Int     = '\u0008'
      def name          = toString
      override val repr = name
    }
    case object f extends Escape with Character {
      val code: Int     = '\u000C'
      def name          = toString
      override val repr = name
    }
    case object n extends Escape with Character {
      val code: Int     = '\n'
      def name          = toString
      override val repr = name
    }
    case object r extends Escape with Character {
      val code: Int     = '\r'
      def name          = toString
      override val repr = name
    }
    case object t extends Escape with Character {
      val code: Int     = '\u0009'
      def name          = toString
      override val repr = name
    }
    case object v extends Escape with Character {
      val code: Int     = '\u000B'
      def name          = toString
      override val repr = name
    }
    case object e extends Escape with Character {
      val code: Int     = '\u001B'
      def name          = toString
      override val repr = name
    }
    val codes = ADT.constructors[Character]
  }

  // Reference: https://en.wikipedia.org/wiki/Control_character
  sealed trait Control // extends Simple
  object Control {
    case object NUL extends Escape with Control {
      val code: Int     = 0x00
      def name          = toString
      override val repr = name
    }
    case object SOH extends Escape with Control {
      val code: Int     = 0x01
      def name          = toString
      override val repr = name
    }
    case object STX extends Escape with Control {
      val code: Int     = 0x02
      def name          = toString
      override val repr = name
    }
    case object ETX extends Escape with Control {
      val code: Int     = 0x03
      def name          = toString
      override val repr = name
    }
    case object EOT extends Escape with Control {
      val code: Int     = 0x04
      def name          = toString
      override val repr = name
    }
    case object ENQ extends Escape with Control {
      val code: Int     = 0x05
      def name          = toString
      override val repr = name
    }
    case object ACK extends Escape with Control {
      val code: Int     = 0x06
      def name          = toString
      override val repr = name
    }
    case object BEL extends Escape with Control {
      val code: Int     = 0x07
      def name          = toString
      override val repr = name
    }
    case object BS extends Escape with Control {
      val code: Int     = 0x08
      def name          = toString
      override val repr = name
    }
    case object TAB extends Escape with Control {
      val code: Int     = 0x09
      def name          = toString
      override val repr = name
    }
    case object LF extends Escape with Control {
      val code: Int     = 0x0A
      def name          = toString
      override val repr = name
    }
    case object VT extends Escape with Control {
      val code: Int     = 0x0B
      def name          = toString
      override val repr = name
    }
    case object FF extends Escape with Control {
      val code: Int     = 0x0C
      def name          = toString
      override val repr = name
    }
    case object CR extends Escape with Control {
      val code: Int     = 0x0D
      def name          = toString
      override val repr = name
    }
    case object SO extends Escape with Control {
      val code: Int     = 0x0E
      def name          = toString
      override val repr = name
    }
    case object SI extends Escape with Control {
      val code: Int     = 0x0F
      def name          = toString
      override val repr = name
    }
    case object DLE extends Escape with Control {
      val code: Int     = 0x10
      def name          = toString
      override val repr = name
    }
    case object DC1 extends Escape with Control {
      val code: Int     = 0x11
      def name          = toString
      override val repr = name
    }
    case object DC2 extends Escape with Control {
      val code: Int     = 0x12
      def name          = toString
      override val repr = name
    }
    case object DC3 extends Escape with Control {
      val code: Int     = 0x13
      def name          = toString
      override val repr = name
    }
    case object DC4 extends Escape with Control {
      val code: Int     = 0x14
      def name          = toString
      override val repr = name
    }
    case object NAK extends Escape with Control {
      val code: Int     = 0x15
      def name          = toString
      override val repr = name
    }
    case object SYN extends Escape with Control {
      val code: Int     = 0x16
      def name          = toString
      override val repr = name
    }
    case object ETB extends Escape with Control {
      val code: Int     = 0x17
      def name          = toString
      override val repr = name
    }
    case object CAN extends Escape with Control {
      val code: Int     = 0x18
      def name          = toString
      override val repr = name
    }
    case object EM extends Escape with Control {
      val code: Int     = 0x19
      def name          = toString
      override val repr = name
    }
    case object SUB extends Escape with Control {
      val code: Int     = 0x1A
      def name          = toString
      override val repr = name
    }
    case object ESC extends Escape with Control {
      val code: Int     = 0x1B
      def name          = toString
      override val repr = name
    }
    case object FS extends Escape with Control {
      val code: Int     = 0x1C
      def name          = toString
      override val repr = name
    }
    case object GS extends Escape with Control {
      val code: Int     = 0x1D
      def name          = toString
      override val repr = name
    }
    case object RS extends Escape with Control {
      val code: Int     = 0x1E
      def name          = toString
      override val repr = name
    }
    case object US extends Escape with Control {
      val code: Int     = 0x1F
      def name          = toString
      override val repr = name
    }
    case object DEL extends Escape with Control {
      val code: Int     = 0x7F
      def name          = toString
      override val repr = name
    }
    val codes = ADT.constructors[Control]
  }
}
