package org.enso.interpreter.runtime.data;

import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

@ExportLibrary(InteropLibrary.class)
public class Vector implements TruffleObject {
  private final Object[] items;

  public Vector(Object[] items) {
    this.items = items;
  }

  @ExportMessage
  public boolean hasArrayElements() {
    return true;
  }

  @ExportMessage
  public Object readArrayElement(long index) throws InvalidArrayIndexException {
    if (index >= items.length) {
      throw InvalidArrayIndexException.create(index);
    }
    return items[(int) index];
  }

  @ExportMessage
  public long getArraySize() {
    return items.length;
  }

  @ExportMessage
  public boolean isArrayElementReadable(long index) {
    return index < getArraySize();
  }
}
