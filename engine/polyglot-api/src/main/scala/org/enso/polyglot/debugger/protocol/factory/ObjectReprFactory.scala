package org.enso.polyglot.debugger.protocol.factory

import com.google.flatbuffers.FlatBufferBuilder
import org.enso.polyglot.debugger.protocol.ObjectRepr

object ObjectReprFactory {

  /**
    * Creates ObjectRepr inside a [[FlatBufferBuilder]].
    *
    * @param obj an object to serialize
    * @param builder a class that helps build a FlatBuffer representation of
    *                complex objects
    * @return an offset pointing to the FlatBuffer representation of the
    *         created object
    */
  def create(obj: Object)(implicit builder: FlatBufferBuilder): Int = {
    val representation: String = representObject(obj)
    val reprOffset             = builder.createString(representation)

    ObjectRepr.createObjectRepr(builder, reprOffset)
  }

  private def representObject(obj: Object): String = obj.toString
}
