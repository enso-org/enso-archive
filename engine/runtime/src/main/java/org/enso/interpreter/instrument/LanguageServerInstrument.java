package org.enso.interpreter.instrument;

import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import org.enso.languageserver.Handler;
import org.enso.polyglot.*;
import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.polyglot.io.MessageEndpoint;
import org.graalvm.polyglot.io.MessageTransport;

import java.io.IOException;
import java.net.URI;
import java.util.Collections;

@TruffleInstrument.Registration(
    id = LanguageServerConnection.INSTRUMENT_NAME,
    services = LanguageServerInstrument.class)
public class LanguageServerInstrument extends TruffleInstrument {
  private Handler handler;

  @Override
  protected void onCreate(Env env) {
    env.registerService(this);
    try {
      Handler handler = new Handler();
      MessageEndpoint client =
          env.startServer(URI.create(LanguageServerConnection.URI), handler.endpoint());
      if (client != null) {
        handler.endpoint().setClient(client);
        this.handler = handler;
      }
    } catch (MessageTransport.VetoException | IOException ignored) {
    }
  }

  @Override
  protected void onDispose(Env env) {
    if (handler != null) {
      try {
        handler.endpoint().client().sendClose();
      } catch (IOException ignored) {
      }
    }
    super.onDispose(env);
  }

  @Override
  protected OptionDescriptors getOptionDescriptors() {
    return OptionDescriptors.create(
        Collections.singletonList(
            OptionDescriptor.newBuilder(new OptionKey<>(""), LanguageServerConnection.ENABLE_OPTION)
                .build()));
  }
}
