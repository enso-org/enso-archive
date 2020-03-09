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
import java.nio.ByteBuffer;
import java.util.Collections;

@TruffleInstrument.Registration(
    id = LanguageServerInstrument.INSTRUMENT_ID,
    services = LanguageServerInstrument.class)
public class LanguageServerInstrument extends TruffleInstrument {
  public static final String INSTRUMENT_ID = LanguageInfo.ID + "-language-server";
  private Handler handler;

  @Override
  protected void onCreate(Env env) {
    try {
      Handler handler = new Handler();
      MessageEndpoint client = env.startServer(URI.create("local://local"), handler.endpoint());
      handler.endpoint().setClient(client);
      this.handler = handler;
      env.registerService(this);
    } catch (MessageTransport.VetoException | IOException e) {
      this.handler = null;
    }
  }

  @Override
  protected OptionDescriptors getOptionDescriptors() {
    return OptionDescriptors.create(
        Collections.singletonList(
            OptionDescriptor.newBuilder(new OptionKey<>(""), INSTRUMENT_ID + ".enable").build()));
  }
}
