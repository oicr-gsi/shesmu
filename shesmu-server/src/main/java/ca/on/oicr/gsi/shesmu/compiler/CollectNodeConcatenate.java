package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.ListNode.Ordering;
import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

public class CollectNodeConcatenate extends CollectNode {

  public enum ConcatentationType {
    LEXICOGRAPHICAL("LexicalConcat"),
    PROVIDED("FixedConcat");
    private final String syntax;

    private ConcatentationType(String syntax) {
      this.syntax = syntax;
    }

    public String syntax() {
      return syntax;
    }
  }

  private static final Type A_CHAR_SEQUENCE_TYPE = Type.getType(CharSequence.class);
  private static final Type A_COLLECTOR_TYPE = Type.getType(Collector.class);
  private static final Type A_COLLECTORS_TYPE = Type.getType(Collectors.class);
  private static final Type A_STREAM_TYPE = Type.getType(Stream.class);
  private static final Method METHOD_COLLECTORS__JOINING =
      new Method("joining", A_COLLECTOR_TYPE, new Type[] {A_CHAR_SEQUENCE_TYPE});

  private static final Method METHOD_STREAM__SORTED =
      new Method("sorted", A_STREAM_TYPE, new Type[] {});

  private final ConcatentationType concatentation;
  private final ExpressionNode delimiter;
  private final ExpressionNode getter;

  private boolean needsSort;

  public CollectNodeConcatenate(
      int line,
      int column,
      ConcatentationType concatentation,
      ExpressionNode getter,
      ExpressionNode delimiter) {
    super(line, column);
    this.concatentation = concatentation;
    this.getter = getter;
    this.delimiter = delimiter;
  }

  @Override
  public void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate) {
    getter.collectFreeVariables(names, predicate);
    delimiter.collectFreeVariables(names, predicate);
  }

  @Override
  public void collectPlugins(Set<Path> pluginFileNames) {
    getter.collectPlugins(pluginFileNames);
    delimiter.collectPlugins(pluginFileNames);
  }

  @Override
  public final boolean orderingCheck(Ordering ordering, Consumer<String> errorHandler) {
    switch (concatentation) {
      case LEXICOGRAPHICAL:
        needsSort = true;
        return true;
      case PROVIDED:
        if (ordering == Ordering.RANDOM) {
          errorHandler.accept(
              String.format(
                  "%d:%d: String concatenation is based on a random order. That is a bad idea.",
                  line(), column()));
          return false;
        }
        return true;
      default:
        return false;
    }
  }

  @Override
  public void render(JavaStreamBuilder builder, LoadableConstructor name) {
    final Set<String> freeVariables = new HashSet<>();
    getter.collectFreeVariables(freeVariables, Flavour::needsCapture);

    final Renderer mapMethod =
        builder.map(
            line(),
            column(),
            name,
            Imyhat.STRING,
            builder
                .renderer()
                .allValues()
                .filter(v -> freeVariables.contains(v.name()))
                .toArray(LoadableValue[]::new));
    mapMethod.methodGen().visitCode();
    getter.render(mapMethod);
    mapMethod.methodGen().returnValue();
    mapMethod.methodGen().visitMaxs(0, 0);
    mapMethod.methodGen().visitEnd();

    builder.collector(
        Imyhat.STRING.apply(TypeUtils.TO_ASM),
        renderer -> {
          if (needsSort) {
            renderer.methodGen().invokeInterface(A_STREAM_TYPE, METHOD_STREAM__SORTED);
          }
          delimiter.render(renderer);
          renderer.methodGen().invokeStatic(A_COLLECTORS_TYPE, METHOD_COLLECTORS__JOINING);
        });
  }

  @Override
  public String render(EcmaStreamBuilder builder, EcmaLoadableConstructor name) {
    builder.map(name, getter.type(), getter::renderEcma);

    return String.format(
        "%s%s.join(%s)",
        builder.finish(), needsSort ? ".sort()" : "", delimiter.renderEcma(builder.renderer()));
  }

  @Override
  public boolean resolve(
      DestructuredArgumentNode name, NameDefinitions defs, Consumer<String> errorHandler) {
    return getter.resolve(defs.bind(name), errorHandler) & delimiter.resolve(defs, errorHandler);
  }

  @Override
  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    return getter.resolveDefinitions(expressionCompilerServices, errorHandler)
        & delimiter.resolveDefinitions(expressionCompilerServices, errorHandler);
  }

  @Override
  public Imyhat type() {
    return Imyhat.STRING;
  }

  @Override
  public boolean typeCheck(Imyhat incoming, Consumer<String> errorHandler) {
    boolean ok = true;
    if (getter.typeCheck(errorHandler)) {
      if (!getter.type().isSame(Imyhat.STRING)) {
        getter.typeError(Imyhat.STRING, getter.type(), errorHandler);
        ok = false;
      }
    } else {
      ok = false;
    }
    if (delimiter.typeCheck(errorHandler)) {
      if (!delimiter.type().isSame(Imyhat.STRING)) {
        delimiter.typeError(Imyhat.STRING, delimiter.type(), errorHandler);
        ok = false;
      }
    } else {
      ok = false;
    }
    return ok;
  }
}
