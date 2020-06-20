package ca.on.oicr.gsi.shesmu.compiler;

import static ca.on.oicr.gsi.shesmu.compiler.TypeUtils.TO_ASM;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat.ObjectImyhat;
import ca.on.oicr.gsi.shesmu.runtime.TableCollector;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

public class CollectNodeTable extends CollectNode {

  private static final Type A_OBJECT_TYPE = Type.getType(Object.class);
  private static final Type A_PAIR_TYPE = Type.getType(Pair.class);
  private static final Type A_STRING_TYPE = Type.getType(String.class);
  private static final Type A_TABLE_COLLECTOR_TYPE = Type.getType(TableCollector.class);
  private static final Method CTOR__PAIR =
      new Method("<init>", Type.VOID_TYPE, new Type[] {A_OBJECT_TYPE, A_OBJECT_TYPE});
  private static final Method CTOR__TABLE_COLLECTOR =
      new Method(
          "<init>",
          Type.VOID_TYPE,
          new Type[] {Type.getType(Tuple.class), Type.getType(Pair[].class)});
  private static final Imyhat FORMAT_TYPE =
      new ObjectImyhat(
          Stream.of(
              new Pair<>("data_end", Imyhat.STRING),
              new Pair<>("data_separator", Imyhat.STRING),
              new Pair<>("data_start", Imyhat.STRING),
              new Pair<>("header_end", Imyhat.STRING),
              new Pair<>("header_separator", Imyhat.STRING),
              new Pair<>("header_start", Imyhat.STRING),
              new Pair<>("header_underline", Imyhat.STRING.asOptional())));
  private final List<Pair<ExpressionNode, ExpressionNode>> columns;
  private List<String> definedNames;
  private final ExpressionNode format;

  public CollectNodeTable(
      int line,
      int column,
      ExpressionNode format,
      List<Pair<ExpressionNode, ExpressionNode>> columns) {
    super(line, column);
    this.format = format;
    this.columns = columns;
  }

  @Override
  public void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate) {
    format.collectFreeVariables(names, predicate);
    // We do the columns in two parts because the scoping rules on the keys is different from the
    // values
    for (final Pair<ExpressionNode, ExpressionNode> column : columns) {
      column.first().collectFreeVariables(names, predicate);
    }
    final List<String> remove =
        definedNames.stream().filter(name -> !names.contains(name)).collect(Collectors.toList());
    for (final Pair<ExpressionNode, ExpressionNode> column : columns) {
      column.second().collectFreeVariables(names, predicate);
    }
    names.removeAll(remove);
  }

  @Override
  public void collectPlugins(Set<Path> pluginFileNames) {
    format.collectPlugins(pluginFileNames);
    for (final Pair<ExpressionNode, ExpressionNode> column : columns) {
      column.first().collectPlugins(pluginFileNames);
      column.second().collectPlugins(pluginFileNames);
    }
  }

  @Override
  public void render(JavaStreamBuilder builder, LoadableConstructor name) {
    builder.collector(
        A_STRING_TYPE,
        renderer -> {
          renderer.methodGen().newInstance(A_TABLE_COLLECTOR_TYPE);
          renderer.methodGen().dup();
          format.render(renderer);
          renderer.methodGen().push(columns.size());
          renderer.methodGen().newArray(A_PAIR_TYPE);
          for (int i = 0; i < columns.size(); i++) {
            renderer.methodGen().dup();
            renderer.methodGen().push(i);
            renderer.methodGen().newInstance(A_PAIR_TYPE);
            renderer.methodGen().dup();
            columns.get(i).first().render(renderer);
            final Set<String> capturedNames = new HashSet<>();
            columns.get(i).second().collectFreeVariables(capturedNames, Flavour::needsCapture);
            final LambdaBuilder lambda =
                new LambdaBuilder(
                    renderer.root(),
                    String.format("Column %d Table %d:%d", i, line(), column()),
                    LambdaBuilder.function(
                        columns.get(i).second().type().apply(TO_ASM), builder.currentType()),
                    renderer.streamType(),
                    renderer
                        .allValues()
                        .filter(v -> capturedNames.contains(v.name()))
                        .toArray(LoadableValue[]::new));
            lambda.push(renderer);
            renderer.methodGen().invokeConstructor(A_PAIR_TYPE, CTOR__PAIR);
            renderer.methodGen().arrayStore(A_PAIR_TYPE);

            final Renderer columnRenderer = lambda.renderer(renderer.signerEmitter());
            columnRenderer.methodGen().visitCode();
            name.create(r -> r.methodGen().loadArg(lambda.trueArgument(0)))
                .forEach(v -> columnRenderer.define(v.name(), v));

            columns.get(i).second().render(columnRenderer);
            columnRenderer.methodGen().returnValue();
            columnRenderer.methodGen().endMethod();
          }
          renderer.methodGen().invokeConstructor(A_TABLE_COLLECTOR_TYPE, CTOR__TABLE_COLLECTOR);
        });
  }

  @Override
  public boolean resolve(
      DestructuredArgumentNode name, NameDefinitions defs, Consumer<String> errorHandler) {
    final NameDefinitions innerDefs = defs.bind(name);
    final boolean ok =
        format.resolve(defs, errorHandler)
            & columns
                    .stream()
                    .filter(
                        c ->
                            c.first().resolve(defs, errorHandler)
                                & c.second().resolve(innerDefs, errorHandler))
                    .count()
                == columns.size();
    definedNames = name.targets().map(Target::name).collect(Collectors.toList());
    return ok;
  }

  @Override
  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    return format.resolveDefinitions(expressionCompilerServices, errorHandler)
        & columns
                .stream()
                .filter(
                    column ->
                        column.first().resolveDefinitions(expressionCompilerServices, errorHandler)
                            & column
                                .second()
                                .resolveDefinitions(expressionCompilerServices, errorHandler))
                .count()
            == columns.size();
  }

  @Override
  public Imyhat type() {
    return Imyhat.STRING;
  }

  @Override
  public boolean typeCheck(Imyhat incoming, Consumer<String> errorHandler) {
    boolean ok = format.typeCheck(errorHandler);
    if (ok) {
      if (!format.type().isSame(FORMAT_TYPE)) {
        format.typeError(FORMAT_TYPE, format.type(), errorHandler);
        ok = false;
      }
    }
    return ok
        & columns
                .stream()
                .flatMap(p -> Stream.of(p.first(), p.second()))
                .filter(
                    e -> {
                      if (e.typeCheck(errorHandler)) {
                        if (e.type().isSame(Imyhat.STRING)) {
                          return false;
                        }
                        e.typeError(Imyhat.STRING, e.type(), errorHandler);
                      }
                      return true;
                    })
                .count()
            == 0;
  }
}
