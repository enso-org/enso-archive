package org.enso.polyglot.debugger;

/** Container for Runtime Server related constants. */
public class DebugServerInfo {
    public static final String URI = "enso://debug-server";

  // TODO this was like this in the original code, shall I change it to enso-debug-server?
  //  Are there some non-trivial implications of such change?
    public static final String INSTRUMENT_NAME = "enso-repl";
    // public static final String ENABLE_OPTION = INSTRUMENT_NAME + ".enable"; // TODO will it be needed ?
}
