package ca.on.oicr.gsi.shesmu.runtime;

import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Optional;
import java.util.function.IntFunction;

public abstract class CopySemantics {
  public static CallSite bootstrap(
      MethodHandles.Lookup lookup, String name, MethodType type, int... indices) {
    final CopySemantics[] output = new CopySemantics[indices.length];
    for (int i = 0; i < indices.length; i++) {
      final IntFunction<CopySemantics> function;
      switch (name.charAt(i)) {
        case 'A':
          function = CopySemantics::leftOptional;
          break;
        case 'a':
          function = CopySemantics::left;
          break;
        case 'B':
          function = CopySemantics::rightOptional;
          break;
        case 'b':
          function = CopySemantics::right;
          break;
        default:
          throw new IllegalArgumentException("Bad character in name: " + name.charAt(i));
      }
      output[i] = function.apply(indices[i]);
    }
    return new ConstantCallSite(MethodHandles.constant(CopySemantics[].class, output));
  }

  public static CopySemantics left(int i) {
    return new CopySemantics() {
      @Override
      public Optional<?> apply(Tuple left, Tuple right) {
        return left == null ? Optional.empty() : Optional.of(left.get(i));
      }
    };
  }

  public static CopySemantics leftOptional(int i) {
    return new CopySemantics() {
      @Override
      public Optional<?> apply(Tuple left, Tuple right) {
        return left == null ? Optional.empty() : (Optional<?>) left.get(i);
      }
    };
  }

  public static CopySemantics right(int i) {
    return new CopySemantics() {
      @Override
      public Optional<?> apply(Tuple left, Tuple right) {
        return right == null ? Optional.empty() : Optional.of(right.get(i));
      }
    };
  }

  public static CopySemantics rightOptional(int i) {
    return new CopySemantics() {
      @Override
      public Optional<?> apply(Tuple left, Tuple right) {
        return right == null ? Optional.empty() : (Optional<?>) right.get(i);
      }
    };
  }

  public abstract Optional<?> apply(Tuple left, Tuple right);
}
