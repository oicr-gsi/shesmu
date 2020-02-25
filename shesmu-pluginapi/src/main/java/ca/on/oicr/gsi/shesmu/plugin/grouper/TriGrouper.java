package ca.on.oicr.gsi.shesmu.plugin.grouper;

import java.util.function.BiConsumer;

public interface TriGrouper<T, S, U, O, I> {
  BiConsumer<O, I> apply(T arg1, S arg2, U arg3);
}
