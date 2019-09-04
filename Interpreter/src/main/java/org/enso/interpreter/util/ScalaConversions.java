package org.enso.interpreter.util;

import scala.Option;
import scala.collection.JavaConverters;
import scala.collection.Seq;

import java.util.List;
import java.util.Optional;

public class ScalaConversions {
  public static <T> Optional<T> asJava(Option<T> option) {
    return Optional.ofNullable(option.getOrElse(() -> null));
  }

  public static <T> List<T> asJava(Seq<T> list) {
    return JavaConverters.seqAsJavaList(list);
  }
}
