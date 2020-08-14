package ca.on.oicr.gsi.shesmu.plugin.types;

import java.util.stream.Stream;

public interface GenericTransformer<R> extends ImyhatTransformer<R> {
  R generic(String id);

  <T> R genericAlgebraic(Stream<GenericAlgebraicGuarantee<? extends T>> inner);

  R genericList(GenericTypeGuarantee<?> inner);

  R genericMap(GenericTypeGuarantee<?> key, GenericTypeGuarantee<?> value);

  R genericOptional(GenericTypeGuarantee<?> inner);

  R genericTuple(Stream<GenericTypeGuarantee<?>> elements);
}
