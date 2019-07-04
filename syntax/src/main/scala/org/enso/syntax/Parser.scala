package org.enso.syntax

import org.enso.syntax.Flexer.ParserBase

class Parser extends ParserBase[AST] {

  ////////////////////////////////////

  var result: AST         = _
  var astStack: List[AST] = Nil

  final def pushAST(): Unit = logger.trace {
    astStack +:= result
    result = null
  }

  final def popAST(): Unit = logger.trace {
    result   = astStack.head
    astStack = astStack.tail
  }

  ////// Offset Management //////

  var lastOffset: Int = 0

  final def useLastOffset(): Int = logger.trace {
    val offset = lastOffset
    lastOffset = 0
    offset
  }

  ////// Group Management //////

  var groupOffsetStack: List[Int] = Nil

  final def pushGroupOffset(offset: Int): Unit = logger.trace {
    groupOffsetStack +:= offset
  }

  final def popGroupOffset(): Int = logger.trace {
    val offset = groupOffsetStack.head
    groupOffsetStack = groupOffsetStack.tail
    offset
  }

  final def onGroupBegin(): Unit = logger.trace {
    pushAST()
    pushGroupOffset(useLastOffset())
  }

  final def onGroupEnd(): Unit = logger.trace {
    val offset  = popGroupOffset()
    val grouped = AST.Group(offset, result, useLastOffset())
    popAST()
    app(grouped)
  }

  ////// Indent Management //////

  class BlockState(
    var isValid: Boolean,
    var indent: Int,
    var emptyLines: List[Int],
    var firstLine: AST,
    var lines: List[AST.Line]
  )
  var blockStack: List[BlockState] = Nil
  var emptyLines: List[Int]        = Nil
  var currentBlock: BlockState     = new BlockState(true, 0, Nil, null, Nil)

  final def pushBlock(newIndent: Int): Unit = logger.trace {
    blockStack +:= currentBlock
    currentBlock =
      new BlockState(true, newIndent, emptyLines.reverse, null, Nil)
    emptyLines = Nil
  }

  final def popBlock(): Unit = logger.trace {
    currentBlock = blockStack.head
    blockStack   = blockStack.tail
  }

  final def submitBlock(): Unit = logger.trace {
    submitLine()
    val block = AST.block(
      currentBlock.indent,
      currentBlock.emptyLines.reverse,
      currentBlock.firstLine,
      currentBlock.lines.reverse,
      currentBlock.isValid
    )
    popAST()
    popBlock()
    app(block)
    logger.endGroup()
  }

  final def submitLine(): Unit = logger.trace {
    if (result != null) {
      if (currentBlock.firstLine == null) {
        currentBlock.emptyLines = emptyLines
        currentBlock.firstLine  = result
      } else {
        val optResult = Option(result)
        emptyLines.foreach(currentBlock.lines +:= AST.Line(_, None))
        currentBlock.lines +:= AST.Line(useLastOffset(), optResult)
      }
      emptyLines = Nil
    }
  }

  final def onBlockBegin(newIndent: Int): Unit = logger.trace {
    pushAST()
    pushBlock(newIndent)
    logger.beginGroup()
  }

  final def onBlockNewline(): Unit = logger.trace {
    submitLine()
    result = null
  }

  final def onEmptyLine(): Unit = logger.trace {
    emptyLines +:= useLastOffset()
  }

  final def onBlockEnd(newIndent: Int): Unit = logger.trace {
    while (newIndent < currentBlock.indent) {
      submitBlock()
    }
    if (newIndent > currentBlock.indent) {
      logger.log("Block with invalid indentation")
      onBlockBegin(newIndent)
      currentBlock.isValid = false
    }
  }

  ////// Numbers //////

  var numberPart1: String = ""
  var numberPart2: String = ""
  var numberPart3: String = ""

  final def numberReset(): Unit = {
    numberPart1 = ""
    numberPart2 = ""
    numberPart3 = ""
  }

  final def submitNumber(): Unit = logger.trace {
    app(AST.Number(numberPart1, numberPart2, numberPart3))
  }

  ////// String //////

  def submitEmptyText(): Unit = {
    app(AST.Text(Vector()))
  }

  ////// Utils //////

  final def app(fn: String => AST): Unit =
    app(fn(currentMatch))

  final def app(t: AST): Unit =
    if (result == null) {
      result = t
    } else {
      result = AST.app(result, useLastOffset(), t)
    }

  final def onWhitespace(): Unit =
    onWhitespace(0)

  final def onWhitespace(shift: Int): Unit = logger.trace {
    val diff = currentMatch.length + shift
    lastOffset += diff
    logger.log(s"lastOffset + $diff = $lastOffset")
  }

  ////// Cleaning //////

  final def onEOF(): Unit = logger.trace {
    onBlockEnd(0)
    submitBlock()
    result = AST.Module(result.asInstanceOf[AST.Block])
  }

  final def initialize(): Unit =
    onBlockBegin(0)

}
