package org.enso.interpreter;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.instrumentation.ProvidedTags;
import com.oracle.truffle.api.instrumentation.StandardTags;
import org.enso.interpreter.nodes.expression.operator.AddOperatorNode;
import org.enso.interpreter.nodes.expression.operator.AddOperatorNodeGen;
import org.enso.interpreter.runtime.Context;

@TruffleLanguage.Registration(
        id = Constants.LANGUAGE_ID,
        name = Constants.LANGUAGE_NAME,
        implementationName = Constants.IMPL_NAME,
        version = Constants.LANGUAGE_VERSION,
        defaultMimeType = Constants.MIME_TYPE,
        characterMimeTypes = Constants.MIME_TYPE,
        contextPolicy = TruffleLanguage.ContextPolicy.SHARED,
        fileTypeDetectors = FileDetector.class
)
// TODO [AA] Additional tags of our own for tooling support.
@ProvidedTags(
        {StandardTags.CallTag.class,
        StandardTags.ExpressionTag.class,
        StandardTags.RootTag.class,
        StandardTags.TryBlockTag.class}
)
public final class Language extends TruffleLanguage<Context> {

    // TODO [AA] Unimplemented
    @Override
    protected Context createContext(Env env) {
        AddOperatorNode foo  = AddOperatorNodeGen.create(null, null);
        return null;
    }

    // TODO [AA] Unimplemented
    @Override
    protected boolean isObjectOfLanguage(Object object) {
        return false;
    }

    @Override
    protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
        return super.isThreadAccessAllowed(thread, singleThreaded);
    }
}
