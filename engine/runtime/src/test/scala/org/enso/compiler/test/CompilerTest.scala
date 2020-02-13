package org.enso.compiler.test

import org.scalatest.{Matchers, WordSpecLike}

trait CompilerRunner {}

trait CompilerTest extends WordSpecLike with Matchers with CompilerRunner
