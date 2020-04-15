// automatically generated by the FlatBuffers compiler, do not modify

package org.enso.languageserver.protocol;

import java.nio.*;
import java.lang.*;
import java.util.*;
import com.google.flatbuffers.*;

@SuppressWarnings("unused")
public final class OutboundMessage extends Table {
  public static void ValidateVersion() { Constants.FLATBUFFERS_1_12_0(); }
  public static OutboundMessage getRootAsOutboundMessage(ByteBuffer _bb) { return getRootAsOutboundMessage(_bb, new OutboundMessage()); }
  public static OutboundMessage getRootAsOutboundMessage(ByteBuffer _bb, OutboundMessage obj) { _bb.order(ByteOrder.LITTLE_ENDIAN); return (obj.__assign(_bb.getInt(_bb.position()) + _bb.position(), _bb)); }
  public void __init(int _i, ByteBuffer _bb) { __reset(_i, _bb); }
  public OutboundMessage __assign(int _i, ByteBuffer _bb) { __init(_i, _bb); return this; }

  public byte payloadType() { int o = __offset(4); return o != 0 ? bb.get(o + bb_pos) : 0; }
  public Table payload(Table obj) { int o = __offset(6); return o != 0 ? __union(obj, o + bb_pos) : null; }

  public static int createOutboundMessage(FlatBufferBuilder builder,
      byte payload_type,
      int payloadOffset) {
    builder.startTable(2);
    OutboundMessage.addPayload(builder, payloadOffset);
    OutboundMessage.addPayloadType(builder, payload_type);
    return OutboundMessage.endOutboundMessage(builder);
  }

  public static void startOutboundMessage(FlatBufferBuilder builder) { builder.startTable(2); }
  public static void addPayloadType(FlatBufferBuilder builder, byte payloadType) { builder.addByte(0, payloadType, 0); }
  public static void addPayload(FlatBufferBuilder builder, int payloadOffset) { builder.addOffset(1, payloadOffset, 0); }
  public static int endOutboundMessage(FlatBufferBuilder builder) {
    int o = builder.endTable();
    return o;
  }

  public static final class Vector extends BaseVector {
    public Vector __assign(int _vector, int _element_size, ByteBuffer _bb) { __reset(_vector, _element_size, _bb); return this; }

    public OutboundMessage get(int j) { return get(new OutboundMessage(), j); }
    public OutboundMessage get(OutboundMessage obj, int j) {  return obj.__assign(__indirect(__element(j), bb), bb); }
  }
}

