package ca.on.oicr.gsi.shesmu.compiler;

import static ca.on.oicr.gsi.shesmu.compiler.JoinSourceNode.A_STREAM_TYPE;
import static ca.on.oicr.gsi.shesmu.compiler.JoinSourceNode.METHOD_INPUT_PROVIDER__FETCH;

import ca.on.oicr.gsi.shesmu.compiler.ExtractionNode.OutputCollector;
import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.compiler.definitions.ActionDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.ConstantDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.ExtractionDataSource;
import ca.on.oicr.gsi.shesmu.compiler.definitions.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.InputFormatDefinition;
import ca.on.oicr.gsi.shesmu.plugin.Parser;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.server.OutputFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

public final class ExtractRuleNode implements ExtractionDataSource {

  private static final Method METHOD_STREAM__FILTER =
      new Method("filter", A_STREAM_TYPE, new Type[] {Type.getType(Predicate.class)});

  public static Parser parse(Parser input, Consumer<ExtractRuleNode> output) {
    var initial = input.whitespace().symbol("{").whitespace();
    final var filter = new AtomicReference<ExpressionNode>();
    if (!initial.isGood()) {
      initial =
          input
              .whitespace()
              .then(ExpressionNode::parse, filter::set)
              .whitespace()
              .symbol("{")
              .whitespace();
      if (!initial.isGood()) {
        return initial;
      }
    }
    final var columns = new AtomicReference<List<ExtractionNode>>();
    final var result =
        initial
            .list(columns::set, ExtractionNode::parse, ',')
            .whitespace()
            .symbol("}")
            .whitespace();
    if (result.isGood()) {
      output.accept(
          new ExtractRuleNode(
              input.line(), input.column(), Optional.ofNullable(filter.get()), columns.get()));
    }
    return result;
  }

  private final int column;
  private final List<ExtractionNode> columns;
  private final Optional<ExpressionNode> filter;
  private InputFormatDefinition inputFormatDefinition;
  private final int line;

  public ExtractRuleNode(
      int line, int column, Optional<ExpressionNode> filter, List<ExtractionNode> columns) {
    this.line = line;
    this.column = column;
    this.filter = filter;
    this.columns = columns;
  }

  @Override
  public Set<String> captures() {
    final var captures = new TreeSet<String>();
    for (final var column : columns) {
      column.collectFreeVariables(captures, Flavour::needsCapture);
    }
    return captures;
  }

  public int column() {
    return column;
  }

  public List<String> columns() {
    return columns.stream().flatMap(ExtractionNode::names).toList();
  }

  public int line() {
    return line;
  }

  @Override
  public String name() {
    return String.format("Extractor %d:%d", line, column);
  }

  @Override
  public void renderColumns(OutputCollector collector) {
    for (final var column : columns) {
      column.render(collector);
    }
  }

  @Override
  public void renderStream(Renderer renderer) {
    renderer.emitNamed(ExtractBuilder.INPUT_PROVIDER.name());
    renderer.methodGen().push(inputFormatDefinition.name());
    renderer
        .methodGen()
        .invokeInterface(ExtractBuilder.INPUT_PROVIDER.type(), METHOD_INPUT_PROVIDER__FETCH);
    filter.ifPresent(
        f -> {
          final var freeFilterVariables = new TreeSet<String>();
          f.collectFreeVariables(freeFilterVariables, Flavour::needsCapture);
          final var filterBuilder =
              new LambdaBuilder(
                  renderer.root(),
                  String.format("Extraction Filter %d_%d", line, column),
                  LambdaBuilder.predicate(inputFormatDefinition.type()),
                  null,
                  renderer
                      .allValues()
                      .filter(l -> freeFilterVariables.contains(l.name()))
                      .toArray(LoadableValue[]::new));
          filterBuilder.push(renderer);
          renderer.methodGen().invokeInterface(A_STREAM_TYPE, METHOD_STREAM__FILTER);
          final var filterRenderer = filterBuilder.renderer(inputFormatDefinition.type(), null);
          filterRenderer.methodGen().visitCode();
          f.render(filterRenderer);
          filterRenderer.methodGen().returnValue();
          filterRenderer.methodGen().endMethod();
        });
  }

  public boolean validate(
      InputFormatDefinition inputFormatDefinition,
      OutputFormat outputFormat,
      Function<String, FunctionDefinition> definedFunctions,
      Consumer<String> errorHandler,
      Supplier<Stream<ConstantDefinition>> constants,
      Map<String, List<ExtractionNode>> preparedColumns) {
    this.inputFormatDefinition = inputFormatDefinition;

    final var services =
        new ExpressionCompilerServices() {
          @Override
          public ActionDefinition action(String name) {
            return null;
          }

          @Override
          public FunctionDefinition function(String name) {
            return definedFunctions.apply(name);
          }

          @Override
          public Imyhat imyhat(String name) {
            return Imyhat.BAD;
          }

          @Override
          public InputFormatDefinition inputFormat() {
            return inputFormatDefinition;
          }

          @Override
          public InputFormatDefinition inputFormat(String format) {
            if (format.equals(inputFormatDefinition.name())) {
              return inputFormatDefinition;
            } else {
              return null;
            }
          }
        };

    final var used = new TreeSet<String>();

    var ok =
        columns.stream()
                .filter(c -> c.resolvePrepared("", preparedColumns, used, errorHandler))
                .count()
            == columns.size();

    if (ok) {
      ok =
          filter.map(f -> f.resolveDefinitions(services, errorHandler)).orElse(true)
              & columns.stream().filter(c -> c.resolveDefinitions(services, errorHandler)).count()
                  == columns.size();
    }

    if (ok) {
      final var defs =
          new NameDefinitions(
              Stream.concat(constants.get(), inputFormatDefinition.baseStreamVariables())
                  .collect(Collectors.toMap(Target::name, Function.identity())),
              true);
      ok =
          filter.map(f -> f.resolve(defs, errorHandler)).orElse(true)
              & columns.stream().filter(c -> c.resolve(defs, errorHandler)).count()
                  == columns.size();
    }
    if (ok) {
      for (final var entry :
          columns.stream()
              .flatMap(ExtractionNode::names)
              .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
              .entrySet()) {
        if (entry.getValue() > 1) {
          ok = false;
          errorHandler.accept(
              String.format(
                  "%d:%d: Column “%s” is repeated %d times.",
                  line, column, entry.getKey(), entry.getValue()));
        }
      }
    }

    if (ok) {
      ok =
          filter.map(f -> f.typeCheck(errorHandler)).orElse(true)
              & columns.stream().filter(c -> c.typeCheck(errorHandler, outputFormat)).count()
                  == columns.size();
    }
    if (ok && filter.map(f -> !f.type().isSame(Imyhat.BOOLEAN)).orElse(false)) {
      ok = false;
      filter.get().typeError(Imyhat.BOOLEAN, filter.get().type(), errorHandler);
    }
    return ok;
  }
}
