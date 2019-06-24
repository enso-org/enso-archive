package org.enso.interpreter

import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.instrumentation.ProvidedTags
import com.oracle.truffle.api.instrumentation.StandardTags
import org.enso.interpreter.nodes.expression.operator.AddOperatorNodeGen
import org.enso.interpreter.runtime.Context

@TruffleLanguage.Registration(
  id                 = Constants.LANGUAGE_ID,
  name               = Constants.LANGUAGE_NAME,
  implementationName = Constants.IMPL_NAME,
  version            = Constants.LANGUAGE_VERSION,
  defaultMimeType    = Constants.MIME_TYPE,
  characterMimeTypes = Array(Constants.MIME_TYPE),
  contextPolicy      = TruffleLanguage.ContextPolicy.SHARED,
  fileTypeDetectors  = Array(classOf[FileDetector])
)
// TODO [AA] Additional tags of our own for tooling support.
@ProvidedTags(
  Array(
    classOf[StandardTags.CallTag],
    classOf[StandardTags.ExpressionTag],
    classOf[StandardTags.RootTag],
    classOf[StandardTags.TryBlockTag]
  )
)
final class Language extends TruffleLanguage[Context] {

  // TODO [AA] Unimplemented
  override protected def createContext(env: TruffleLanguage.Env): Context = {
    val foo = AddOperatorNodeGen.create(null, null)
    null
  }

  override protected def isObjectOfLanguage(`object`: Any) = false

  override protected def isThreadAccessAllowed(
    thread: Thread,
    singleThreaded: Boolean
  ): Boolean = super.isThreadAccessAllowed(thread, singleThreaded)
}
