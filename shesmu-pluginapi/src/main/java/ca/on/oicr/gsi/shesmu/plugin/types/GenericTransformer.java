package ca.on.oicr.gsi.shesmu.plugin.types;

import java.util.stream.Stream;

public interface GenericTransformer<R> extends ImyhatTransformer<R> {
  R generic(String id);

  R genericList(GenericTypeGuarantee<?> inner);

  R genericOptional(GenericTypeGuarantee<?> inner);

  R genericTuple(Stream<GenericTypeGuarantee<?>> elements);
}
