package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.plugin.types.ImyhatTransformer;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class EcmaScriptRenderer {

  public interface LambdaRender {
    String render(EcmaScriptRenderer renderer, IntFunction<String> arg);
  }

  static final class CompareTransformer implements ImyhatTransformer<String> {
    private final String left;
    private final String right;

    CompareTransformer(String left, String right) {
      this.left = left;
      this.right = right;
    }

    @Override
    public String algebraic(Stream<AlgebraicTransformer> contents) {
      return String.format(
          "(%1$s.type.localeCompare(%2$s.type) || (() => { switch(%1$s.type) {%3$s} return 0;})())",
          left,
          right,
          contents
              .map(
                  u ->
                      String.format(
                          "case \"%s\": return %s;",
                          u.name(),
                          u.visit(
                              new AlgebraicVisitor<String>() {
                                private final CompareTransformer inner =
                                    new CompareTransformer(left + ".contents", right + ".contents");

                                @Override
                                public String empty(String name) {
                                  return "0";
                                }

                                @Override
                                public String object(
                                    String name, Stream<Pair<String, Imyhat>> contents) {
                                  return inner.object(contents);
                                }

                                @Override
                                public String tuple(String name, Stream<Imyhat> contents) {
                                  return inner.tuple(contents);
                                }
                              })))
              .collect(Collectors.joining()));
    }

    @Override
    public String bool() {
      return String.format("(%s ? 0 : 1) - (%s ? 0 : 1)", left, right);
    }

    @Override
    public String date() {
      return String.format("%s - %s", left, right);
    }

    @Override
    public String floating() {
      return String.format("%s - %s", left, right);
    }

    @Override
    public String integer() {
      return String.format("%s - %s", left, right);
    }

    @Override
    public String json() {
      return String.format("JSON.stringify(%s).localeCompare(JSON.stringify(%s))", left, right);
    }

    @Override
    public String list(Imyhat inner) {
      return String.format(
          "$runtime.setCompare(%s, %s, (a, b) => %s)", left, right, inner.apply(COMPARATOR));
    }

    @Override
    public String map(Imyhat key, Imyhat value) {
      return String.format(
          "$runtime.dictCompare(%s, %s, (ak, av, bk, bv) => %s || %s)",
          left,
          right,
          key.apply(new CompareTransformer("ak", "bk")),
          value.apply(new CompareTransformer("av", "bv")));
    }

    @Override
    public String object(Stream<Pair<String, Imyhat>> contents) {
      return contents
          .map(
              field ->
                  field
                      .second()
                      .apply(
                          new CompareTransformer(
                              left + "." + field.first(), right + "." + field.first())))
          .collect(Collectors.joining(" || ", "(", ")"));
    }

    @Override
    public String optional(Imyhat inner) {
      return String.format(
          "(%1$s === null && %2$s == null ? 0 : ((%1$s === null ? 0 : 1) - (%2$s === null ? 0 : 1) || %3$s))",
          left, right, inner.apply(this));
    }

    @Override
    public String path() {
      return String.format("%s.localeCompare(%s)", left, right);
    }

    @Override
    public String string() {
      return String.format("%s.localeCompare(%s)", left, right);
    }

    @Override
    public String tuple(Stream<Imyhat> contents) {
      return contents
          .map(
              new Function<Imyhat, String>() {
                private int index;

                @Override
                public String apply(Imyhat imyhat) {
                  final CompareTransformer inner =
                      new CompareTransformer(left + "[" + index + "]", right + "[" + index + "]");
                  index++;
                  return imyhat.apply(inner);
                }
              })
          .collect(Collectors.joining(" || ", "(", ")"));
    }
  }

  public static final ImyhatTransformer<String> COMPARATOR = new CompareTransformer("a", "b");

  public static ImyhatTransformer<String> isEqual(String left, String right) {
    return new ImyhatTransformer<String>() {
      @Override
      public String algebraic(Stream<AlgebraicTransformer> contents) {
        return String.format(
            "%s.type == %s.type && (%s)",
            left,
            right,
            contents
                .map(
                    t -> {
                      try {
                        return String.format(
                            "%s.type == %s && %s",
                            left,
                            RuntimeSupport.MAPPER.writeValueAsString(t.name()),
                            t.visit(
                                new AlgebraicVisitor<String>() {
                                  @Override
                                  public String empty(String name) {
                                    return "true";
                                  }

                                  @Override
                                  public String object(
                                      String name, Stream<Pair<String, Imyhat>> contents) {
                                    return contents
                                        .map(
                                            field ->
                                                field
                                                    .second()
                                                    .apply(
                                                        isEqual(
                                                            left + ".contents." + field.first(),
                                                            right + ".contents." + field.first())))
                                        .collect(Collectors.joining(" && "));
                                  }

                                  @Override
                                  public String tuple(String name, Stream<Imyhat> contents) {
                                    return contents
                                        .map(
                                            new Function<Imyhat, String>() {
                                              private int index;

                                              @Override
                                              public String apply(Imyhat imyhat) {
                                                final int current = index++;
                                                return imyhat.apply(
                                                    isEqual(
                                                        left + ".contents[" + current + "]",
                                                        right + ".contents[" + current + "]"));
                                              }
                                            })
                                        .collect(Collectors.joining(" && "));
                                  }
                                }));
                      } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                      }
                    })
                .collect(Collectors.joining(" || ", "(", ")")));
      }

      @Override
      public String bool() {
        return String.format("(%s == %s)", left, right);
      }

      @Override
      public String date() {
        return String.format("(%s == %s)", left, right);
      }

      @Override
      public String floating() {
        return String.format("(%s == %s)", left, right);
      }

      @Override
      public String integer() {
        return String.format("(%s == %s)", left, right);
      }

      @Override
      public String json() {
        // This may not be right as it assumes that all keys are in the same order, but we have
        // endeavoured to make that true, so YOLO?
        return String.format("(JSON.stringfiy(%s) == JSON.stringify(%s))", left, right);
      }

      @Override
      public String list(Imyhat inner) {
        return String.format(
            "$runtime.setEqual(%s, %s, (a, b) => %s)", left, right, inner.apply(isEqual("a", "b")));
      }

      @Override
      public String map(Imyhat key, Imyhat value) {
        return String.format(
            "$runtime.dictEqual(%s, %s, (ak, av, bk, bv) => %s && %s)",
            left, right, key.apply(isEqual("ak", "bk")), key.apply(isEqual("av", "bv")));
      }

      @Override
      public String object(Stream<Pair<String, Imyhat>> contents) {
        return contents
            .map(
                field ->
                    field
                        .second()
                        .apply(isEqual(left + "." + field.first(), right + "." + field.first())))
            .collect(Collectors.joining(" && ", "(", ")"));
      }

      @Override
      public String optional(Imyhat inner) {
        return String.format(
            "(%1$s === null && %2$s === null || %1$s !== null && $1$s !== null && %3$s)",
            left, right, inner.apply(this));
      }

      @Override
      public String path() {
        return String.format("(%s == %s)", left, right);
      }

      @Override
      public String string() {
        return String.format("(%s == %s)", left, right);
      }

      @Override
      public String tuple(Stream<Imyhat> contents) {
        return contents
            .map(
                new Function<Imyhat, String>() {
                  private int index;

                  @Override
                  public String apply(Imyhat imyhat) {
                    final int current = index++;
                    return imyhat.apply(
                        isEqual(left + "[" + current + "]", right + "[" + current + "]"));
                  }
                })
            .collect(Collectors.joining(" && ", "(", ")"));
      }
    };
  }

  public static String regexFlagsToString(int flags) {
    final StringBuilder builder = new StringBuilder();
    if ((flags & Pattern.CASE_INSENSITIVE) != 0) {
      builder.append("i");
    }
    if ((flags & Pattern.MULTILINE) != 0) {
      builder.append("m");
    }
    if ((flags & Pattern.DOTALL) != 0) {
      builder.append("s");
    }
    if ((flags & Pattern.UNICODE_CASE) != 0) {
      builder.append("u");
    }

    return builder.toString();
  }

  public static String root(String sourcePath, String hash, Consumer<EcmaScriptRenderer> render) {
    final StringBuilder output = new StringBuilder();
    final EcmaScriptRenderer renderer =
        new EcmaScriptRenderer(sourcePath, hash, new AtomicInteger(), output, new TreeMap<>());
    render.accept(renderer);
    return output.toString();
  }

  protected final StringBuilder builder;
  private final String hash;
  private final AtomicInteger id;
  private final String sourcePath;
  private final Map<String, EcmaLoadableValue> targets;

  private EcmaScriptRenderer(
      String sourcePath,
      String hash,
      AtomicInteger id,
      StringBuilder builder,
      Map<String, EcmaLoadableValue> targets) {
    this.sourcePath = sourcePath;
    this.hash = hash;
    this.id = id;
    this.builder = builder;
    this.targets = targets;
  }

  public EcmaStreamBuilder buildStream(Imyhat initialType, String root) {
    return new EcmaStreamBuilder(this, initialType, root);
  }

  public void conditional(String condition, Consumer<EcmaScriptRenderer> whenTrue) {
    builder.append("if (").append(condition).append(") {");
    whenTrue.accept(new EcmaScriptRenderer(sourcePath, hash, id, builder, new TreeMap<>(targets)));
    builder.append("}");
  }

  public void conditional(
      String condition,
      Consumer<EcmaScriptRenderer> whenTrue,
      Consumer<EcmaScriptRenderer> whenFalse) {
    builder.append("if (").append(condition).append(") {");
    whenTrue.accept(new EcmaScriptRenderer(sourcePath, hash, id, builder, new TreeMap<>(targets)));
    builder.append("} else {");
    whenFalse.accept(new EcmaScriptRenderer(sourcePath, hash, id, builder, new TreeMap<>(targets)));
    builder.append("}");
  }

  public void define(EcmaLoadableValue value) {
    targets.put(value.name(), value);
  }

  public EcmaScriptRenderer duplicate() {
    return new EcmaScriptRenderer(sourcePath, hash, id, builder, new TreeMap<>(targets));
  }

  public String hash() {
    return hash;
  }

  public String lambda(int args, LambdaRender body) {
    final StringBuilder functionBuilder = new StringBuilder();
    final int offset = id.getAndAdd(args);
    functionBuilder.append("function(");
    for (int i = 0; i < args; i++) {
      if (i > 0) {
        functionBuilder.append(", ");
      }
      functionBuilder.append(" _").append(i + offset);
    }
    functionBuilder.append(") {");
    final EcmaScriptRenderer block =
        new EcmaScriptRenderer(sourcePath, hash, id, functionBuilder, new TreeMap<>(targets));
    block.statement(
        String.format(
            "return %s",
            body.render(
                block,
                i -> {
                  if (i < 0 || i >= args) {
                    throw new IllegalArgumentException("Invalid lambda argument");
                  }
                  return "_" + (i + offset);
                })));
    functionBuilder.append("}");
    return functionBuilder.toString();
  }

  public final String load(Target target) {
    return targets.get(target.name()).get();
  }

  public <T> void mapIf(
      Stream<T> cases,
      Function<T, String> testGenerator,
      BiConsumer<EcmaScriptRenderer, T> bodyGenerator,
      Consumer<EcmaScriptRenderer> alternate) {
    cases.forEach(
        c -> {
          builder.append("if (").append(testGenerator.apply(c)).append(") {");
          bodyGenerator.accept(
              new EcmaScriptRenderer(sourcePath, hash, id, builder, new TreeMap<>(targets)), c);
          builder.append("} else ");
        });
    builder.append("{");
    alternate.accept(new EcmaScriptRenderer(sourcePath, hash, id, builder, new TreeMap<>(targets)));
    builder.append("}");
  }

  public final String newConst(String value) {
    final int newId = id.getAndIncrement();
    builder.append(String.format("const _%d = %s;\n", newId, value));
    return "_" + newId;
  }

  public final String newLet(String value) {
    final int newId = id.getAndIncrement();
    builder.append(String.format("let _%d = %s;\n", newId, value));
    return "_" + newId;
  }

  public final String newLet() {
    final int newId = id.getAndIncrement();
    builder.append(String.format("let _%d;\n", newId));
    return "_" + newId;
  }

  public String selfInvoke(Function<EcmaScriptRenderer, String> expression) {
    final StringBuilder functionBuilder = new StringBuilder();
    functionBuilder.append("(function() {");
    final EcmaScriptRenderer block =
        new EcmaScriptRenderer(sourcePath, hash, id, functionBuilder, new TreeMap<>(targets));
    block.statement(String.format("return %s", expression.apply(block)));
    functionBuilder.append("})()");
    return functionBuilder.toString();
  }

  public String sourcePath() {
    return sourcePath;
  }

  public final void statement(String statement) {
    builder.append(statement).append(";\n");
  }
}
