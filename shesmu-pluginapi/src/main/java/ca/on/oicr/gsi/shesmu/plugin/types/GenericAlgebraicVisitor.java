package ca.on.oicr.gsi.shesmu.plugin.types;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.types.ImyhatTransformer.AlgebraicVisitor;
import java.util.stream.Stream;

public interface GenericAlgebraicVisitor<R> extends AlgebraicVisitor<R> {

  <T> R genericObject(
      String name, Stream<Pair<String, GenericTypeGuarantee<? extends T>>> pairStream);

  <T> R genericTuple(String name, Stream<GenericTypeGuarantee<? extends T>> pairStream);
}
