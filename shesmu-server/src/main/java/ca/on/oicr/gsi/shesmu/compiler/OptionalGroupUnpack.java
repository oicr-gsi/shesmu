package ca.on.oicr.gsi.shesmu.compiler;

import static ca.on.oicr.gsi.shesmu.compiler.BaseOliveBuilder.A_OPTIONAL_TYPE;
import static ca.on.oicr.gsi.shesmu.compiler.ExpressionNodeOptionalOf.renderLayers;
import static ca.on.oicr.gsi.shesmu.compiler.GroupNodeOptionalUnpack.innerName;
import static ca.on.oicr.gsi.shesmu.compiler.TypeUtils.TO_ASM;

import ca.on.oicr.gsi.shesmu.compiler.Regrouper.OnlyIfConsumer;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

public enum OptionalGroupUnpack {
  FLATTEN {
    @Override
    public Imyhat type(Imyhat type) {
      return type;
    }

    @Override
    public OnlyIfConsumer consumer(Imyhat type, Map<Integer, List<UnboxableExpression>> captures) {
      return new OnlyIfConsumer() {
        @Override
        public void build(Renderer renderer, String fieldName, Type owner, Label failurePath) {
          renderLayers(renderer, failurePath, captures);
        }

        @Override
        public boolean countsRequired() {
          return false;
        }

        @Override
        public void failIfBad(
            GeneratorAdapter checker,
            String fieldName,
            Type owner,
            BiConsumer<GeneratorAdapter, Label> checkInner,
            Label failure) {
          checkInner.accept(checker, failure);
        }

        @Override
        public void renderGetter(GeneratorAdapter getter, String fieldName, Type owner) {
          getter.loadThis();
          getter.invokeVirtual(owner, new Method(innerName(fieldName), type(), new Type[] {}));
        }

        @Override
        public Type type() {
          return type.apply(TO_ASM);
        }
      };
    }
  },
  EMPTY_IF_ANY_EMPTY {
    @Override
    public Imyhat type(Imyhat type) {
      return type.asOptional();
    }

    @Override
    public OnlyIfConsumer consumer(Imyhat type, Map<Integer, List<UnboxableExpression>> captures) {
      return new AsEmpty(type, captures, Regrouper::goodCount, false);
    }
  },
  REJECT_IF_ANY_EMPTY {
    @Override
    public Imyhat type(Imyhat type) {
      return type;
    }

    @Override
    public OnlyIfConsumer consumer(Imyhat type, Map<Integer, List<UnboxableExpression>> captures) {
      return new Reject(type, captures, Regrouper::goodCount, false);
    }
  },
  EMPTY_IF_ALL_EMPTY {
    @Override
    public Imyhat type(Imyhat type) {
      return type.asOptional();
    }

    @Override
    public OnlyIfConsumer consumer(Imyhat type, Map<Integer, List<UnboxableExpression>> captures) {
      return new AsEmpty(type, captures, Regrouper::badCount, true);
    }
  },
  REJECT_IF_ALL_EMPTY {
    @Override
    public Imyhat type(Imyhat type) {
      return type;
    }

    @Override
    public OnlyIfConsumer consumer(Imyhat type, Map<Integer, List<UnboxableExpression>> captures) {
      return new Reject(type, captures, Regrouper::badCount, true);
    }
  };

  private static final class AsEmpty implements OnlyIfConsumer {
    private final Map<Integer, List<UnboxableExpression>> captures;
    private final Function<String, String> checkCount;
    private final boolean isZero;
    private final Imyhat type;

    private AsEmpty(
        Imyhat type,
        Map<Integer, List<UnboxableExpression>> captures,
        Function<String, String> checkCount,
        boolean isZero) {
      this.type = type;
      this.captures = captures;
      this.checkCount = checkCount;
      this.isZero = isZero;
    }

    @Override
    public void build(Renderer renderer, String fieldName, Type owner, Label failurePath) {
      renderLayers(renderer, failurePath, captures);
    }

    @Override
    public boolean countsRequired() {
      return true;
    }

    @Override
    public void failIfBad(
        GeneratorAdapter checker,
        String fieldName,
        Type owner,
        BiConsumer<GeneratorAdapter, Label> checkInner,
        Label failure) {
      final var end = checker.newLabel();
      // First check if we failed by optional standards; if we did , continue, as we will never care
      // about the inside
      checker.loadThis();
      checker.getField(owner, checkCount.apply(fieldName), Type.INT_TYPE);
      checker.push(0);
      checker.ifICmp(isZero ? GeneratorAdapter.NE : GeneratorAdapter.EQ, end);

      // If we would emit this value, but it's bad...
      final var innerBad = checker.newLabel();
      checkInner.accept(checker, innerBad);
      checker.goTo(end);

      // scramble the count so we never try to read it
      checker.mark(innerBad);
      checker.loadThis();
      checker.push(0);
      checker.putField(owner, checkCount.apply(fieldName), Type.INT_TYPE);

      checker.mark(end);
    }

    @Override
    public void renderGetter(GeneratorAdapter getter, String fieldName, Type owner) {
      final var failPath = getter.newLabel();
      final var end = getter.newLabel();
      getter.loadThis();
      getter.getField(owner, checkCount.apply(fieldName), Type.INT_TYPE);
      getter.push(0);
      getter.ifICmp(isZero ? GeneratorAdapter.NE : GeneratorAdapter.EQ, failPath);
      getter.loadThis();
      getter.invokeVirtual(
          owner, new Method(innerName(fieldName), type.apply(TO_ASM), new Type[] {}));
      getter.valueOf(type.apply(TO_ASM));
      getter.invokeStatic(A_OPTIONAL_TYPE, METHOD_OPTIONAL__OF);
      getter.goTo(end);
      getter.mark(failPath);
      getter.invokeStatic(A_OPTIONAL_TYPE, METHOD_OPTIONAL__EMPTY);
      getter.mark(end);
    }

    @Override
    public Type type() {
      return A_OPTIONAL_TYPE;
    }
  }

  private static final class Reject implements OnlyIfConsumer {
    private final Map<Integer, List<UnboxableExpression>> captures;
    private final Function<String, String> checkCount;
    private final boolean isZero;
    private final Imyhat type;

    private Reject(
        Imyhat type,
        Map<Integer, List<UnboxableExpression>> captures,
        Function<String, String> checkCount,
        boolean isZero) {
      this.type = type;
      this.captures = captures;
      this.checkCount = checkCount;
      this.isZero = isZero;
    }

    @Override
    public void build(Renderer renderer, String fieldName, Type owner, Label failurePath) {
      renderLayers(renderer, failurePath, captures);
    }

    @Override
    public boolean countsRequired() {
      return true;
    }

    @Override
    public void failIfBad(
        GeneratorAdapter checker,
        String fieldName,
        Type owner,
        BiConsumer<GeneratorAdapter, Label> checkInner,
        Label failure) {
      // First check if we failed by optional standards; if we did, fail
      checker.loadThis();
      checker.getField(owner, checkCount.apply(fieldName), Type.INT_TYPE);
      checker.push(0);
      checker.ifICmp(isZero ? GeneratorAdapter.NE : GeneratorAdapter.EQ, failure);

      // We would emit this value, so do its check...
      checkInner.accept(checker, failure);
    }

    @Override
    public void renderGetter(GeneratorAdapter getter, String fieldName, Type owner) {
      getter.loadThis();
      getter.invokeVirtual(owner, new Method(innerName(fieldName), type(), new Type[] {}));
    }

    @Override
    public Type type() {
      return type.apply(TO_ASM);
    }
  }

  private static final Method METHOD_OPTIONAL__EMPTY =
      new Method("empty", A_OPTIONAL_TYPE, new Type[0]);
  private static final Method METHOD_OPTIONAL__OF =
      new Method("of", A_OPTIONAL_TYPE, new Type[] {Type.getType(Object.class)});

  public abstract OnlyIfConsumer consumer(
      Imyhat type, Map<Integer, List<UnboxableExpression>> captures);

  public abstract Imyhat type(Imyhat type);
}
