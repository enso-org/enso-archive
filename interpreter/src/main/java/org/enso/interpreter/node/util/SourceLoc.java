package org.enso.interpreter.node.util;

public class SourceLoc {
    public long position = 0;
    public long span = 0;

    public SourceLoc() {}
    public SourceLoc(long position, long span) {
        this.position = position;
        this.span = span;
    }

    public void add(SourceLoc that) {
        this.position += that.position;
        this.span += that.span;
    }

    public static SourceLoc empty() {
        return new SourceLoc();
    }

    public boolean isEmpty() {
        return this.position == 0 && this.span == 0;
    }
}
