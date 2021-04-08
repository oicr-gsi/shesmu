package ca.on.oicr.gsi.shesmu.compiler;

import static org.objectweb.asm.Type.BOOLEAN_TYPE;
import static org.objectweb.asm.Type.INT_TYPE;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.plugin.types.ImyhatTransformer;
import java.util.stream.Stream;
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

/** Utility code for generating comparison bytecode */
public enum Comparison {
  EQ(GeneratorAdapter.EQ, false, "==") {
    @Override
    public ImyhatTransformer<String> render(
        EcmaScriptRenderer renderer, String left, String right) {
      return EcmaScriptRenderer.isEqual(left, right);
    }
  },
  GE(GeneratorAdapter.GE, true, ">=") {
    @Override
    public ImyhatTransformer<String> render(
        EcmaScriptRenderer renderer, String left, String right) {
      return new OrderableComparison(symbol(), left, right);
    }
  },
  GT(GeneratorAdapter.GT, true, ">") {
    @Override
    public ImyhatTransformer<String> render(
        EcmaScriptRenderer renderer, String left, String right) {
      return new OrderableComparison(symbol(), left, right);
    }
  },
  LE(GeneratorAdapter.LE, true, "<=") {
    @Override
    public ImyhatTransformer<String> render(
        EcmaScriptRenderer renderer, String left, String right) {
      return new OrderableComparison(symbol(), left, right);
    }
  },
  LT(GeneratorAdapter.LT, true, "<") {
    @Override
    public ImyhatTransformer<String> render(
        EcmaScriptRenderer renderer, String left, String right) {
      return new OrderableComparison(symbol(), left, right);
    }
  },
  NE(GeneratorAdapter.NE, false, "!=") {
    @Override
    public ImyhatTransformer<String> render(
        EcmaScriptRenderer renderer, String left, String right) {
      return new ImyhatTransformer<>() {
        final ImyhatTransformer<String> isEqual = EcmaScriptRenderer.isEqual(left, right);

        @Override
        public String algebraic(Stream<AlgebraicTransformer> contents) {
          return "!" + isEqual.algebraic(contents);
        }

        @Override
        public String bool() {
          return "!" + isEqual.bool();
        }

        @Override
        public String date() {
          return "!" + isEqual.date();
        }

        @Override
        public String floating() {
          return "!" + isEqual.floating();
        }

        @Override
        public String integer() {
          return "!" + isEqual.integer();
        }

        @Override
        public String json() {
          return "!" + isEqual.json();
        }

        @Override
        public String list(Imyhat inner) {
          return "!" + isEqual.list(inner);
        }

        @Override
        public String map(Imyhat key, Imyhat value) {
          return "!" + isEqual.map(key, value);
        }

        @Override
        public String object(Stream<Pair<String, Imyhat>> contents) {
          return "!" + isEqual.object(contents);
        }

        @Override
        public String optional(Imyhat inner) {
          return "!" + isEqual.optional(inner);
        }

        @Override
        public String path() {
          return "!" + isEqual.path();
        }

        @Override
        public String string() {
          return "!" + isEqual.string();
        }

        @Override
        public String tuple(Stream<Imyhat> contents) {
          return "!" + isEqual.tuple(contents);
        }
      };
    }
  };

  private static class OrderableComparison implements ImyhatTransformer<String> {
    private final String left;
    private final String right;
    private final String symbol;

    private OrderableComparison(String symbol, String left, String right) {
      this.symbol = symbol;
      this.left = left;
      this.right = right;
    }

    @Override
    public String algebraic(Stream<AlgebraicTransformer> contents) {
      throw new UnsupportedOperationException();
    }

    @Override
    public String bool() {
      throw new UnsupportedOperationException();
    }

    @Override
    public String date() {
      return String.format("(%s %s %s)", left, symbol, right);
    }

    @Override
    public String floating() {
      return String.format("(%s %s %s)", left, symbol, right);
    }

    @Override
    public String integer() {
      return String.format("(%s %s %s)", left, symbol, right);
    }

    @Override
    public String json() {
      throw new UnsupportedOperationException();
    }

    @Override
    public String list(Imyhat inner) {
      throw new UnsupportedOperationException();
    }

    @Override
    public String map(Imyhat key, Imyhat value) {
      throw new UnsupportedOperationException();
    }

    @Override
    public String object(Stream<Pair<String, Imyhat>> contents) {
      throw new UnsupportedOperationException();
    }

    @Override
    public String optional(Imyhat inner) {
      throw new UnsupportedOperationException();
    }

    @Override
    public String path() {
      throw new UnsupportedOperationException();
    }

    @Override
    public String string() {
      throw new UnsupportedOperationException();
    }

    @Override
    public String tuple(Stream<Imyhat> contents) {
      throw new UnsupportedOperationException();
    }
  }

  private static final Type A_COMPARABLE_TYPE = Type.getType(Comparable.class);
  private static final Type A_OBJECT_TYPE = Type.getType(Object.class);

  private static final Method METHOD_COMPARE_TO =
      new Method("compareTo", INT_TYPE, new Type[] {A_OBJECT_TYPE});
  private static final Method METHOD_EQUALS =
      new Method("equals", BOOLEAN_TYPE, new Type[] {A_OBJECT_TYPE});
  private final int id;
  private final boolean ordered;
  private final String symbol;

  Comparison(int id, boolean ordered, String symbol) {
    this.id = id;
    this.ordered = ordered;
    this.symbol = symbol;
  }

  public void branchBool(Label target, GeneratorAdapter methodGen) {
    methodGen.ifCmp(Type.BOOLEAN_TYPE, id, target);
  }

  public void branchComparable(Label target, GeneratorAdapter methodGen) {
    methodGen.invokeInterface(A_COMPARABLE_TYPE, METHOD_COMPARE_TO);
    methodGen.push(0);
    methodGen.ifCmp(Type.INT_TYPE, id, target);
  }

  public void branchFloat(Label target, GeneratorAdapter methodGen) {
    methodGen.ifCmp(Type.DOUBLE_TYPE, id, target);
  }

  public void branchInt(Label target, GeneratorAdapter methodGen) {
    methodGen.ifCmp(Type.LONG_TYPE, id, target);
  }

  public void branchObject(Label target, GeneratorAdapter methodGen) {
    if (isOrdered()) {
      throw new IllegalArgumentException(String.format("Cannot compare %s on object type", name()));
    }
    methodGen.invokeVirtual(A_OBJECT_TYPE, METHOD_EQUALS);
    methodGen.not();
    methodGen.ifZCmp(id, target);
  }

  /** Whether this comparison only works for totally ordered types (integers and dates). */
  public boolean isOrdered() {
    return ordered;
  }

  public abstract ImyhatTransformer<String> render(
      EcmaScriptRenderer renderer, String left, String right);

  /** The symbol for this type. */
  public String symbol() {
    return symbol;
  }
}
