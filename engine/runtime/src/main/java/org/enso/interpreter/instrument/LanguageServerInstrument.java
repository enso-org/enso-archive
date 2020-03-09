package org.enso.interpreter.instrument;

import com.oracle.truffle.api.instrumentation.TruffleInstrument;
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

  @Override
  protected void onCreate(Env env) {
    env.registerService(this);
    System.out.println("CREATE SERVER");
    try {
      MessageEndpoint server =
          env.startServer(
              URI.create("local://foo"),
              new MessageEndpoint() {
                @Override
                public void sendText(String text) {}

                @Override
                public void sendBinary(ByteBuffer data) {
                  System.out.println("RECV BINARY");
                  ServerApi request = ServerApiSerialization.deserialize(data);
                  if (request instanceof CreateContext) {
                    System.out.println("Got create context: " + ((CreateContext) request).id());
                  } else if (request instanceof DestroyContext) {
                    System.out.println("Got destroy context: " + ((DestroyContext) request).id());
                  } else {
                    System.out.println("Got unknown: " + request);
                  }
                }

                @Override
                public void sendPing(ByteBuffer data) {}

                @Override
                public void sendPong(ByteBuffer data) {}

                @Override
                public void sendClose() throws IOException {}
              });
      server.sendText("foo from runtime");
    } catch (MessageTransport.VetoException | IOException e) {

    }
  }

  @Override
  protected OptionDescriptors getOptionDescriptors() {
    return OptionDescriptors.create(
        Collections.singletonList(
            OptionDescriptor.newBuilder(new OptionKey<>(""), INSTRUMENT_ID + ".enable").build()));
  }
}
