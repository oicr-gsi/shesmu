package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.compiler.definitions.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.compiler.description.OliveTable;
import ca.on.oicr.gsi.shesmu.plugin.Parser;
import ca.on.oicr.gsi.shesmu.runtime.ActionGenerator;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** An olive stanza declaration */
public abstract class OliveNode {
  protected enum ClauseStreamOrder {
    BAD,
    PURE,
    ALMOST_PURE,
    TRANSFORMED
  }

  interface OliveAlertConstructor {
    OliveNodeAlert create(
        int line, int column, List<OliveClauseNode> clauses, Set<String> tags, String description);
  }

  private interface OliveConstructor {
    OliveNode create(
        int line, int column, List<OliveClauseNode> clauses, Set<String> tags, String description);
  }

  private static final Pattern DESCRIPTION = Pattern.compile("[^\"\\n]*");
  private static final Parser.ParseDispatch<OliveNode> EXPORTABLE = new Parser.ParseDispatch<>();
  private static final Parser.ParseDispatch<OliveNode> ROOTS = new Parser.ParseDispatch<>();
  private static final Parser.ParseDispatch<OliveConstructor> TERMINAL =
      new Parser.ParseDispatch<>();

  static {
    TERMINAL.addKeyword(
        "Run",
        (input, output) -> {
          final var name = new AtomicReference<String>();
          final var variableTags = new AtomicReference<List<VariableTagNode>>();
          final var arguments = new AtomicReference<List<OliveArgumentNode>>();
          final var result =
              input
                  .whitespace()
                  .qualifiedIdentifier(name::set)
                  .whitespace()
                  .list(variableTags::set, VariableTagNode::parse)
                  .whitespace()
                  .keyword("With")
                  .whitespace()
                  .list(arguments::set, OliveArgumentNode::parse, ',');
          if (result.isGood()) {
            output.accept(
                (line, column, clauses, tags, description) ->
                    new OliveNodeRun(
                        line,
                        column,
                        name.get(),
                        arguments.get(),
                        clauses,
                        tags,
                        description,
                        variableTags.get()));
          }
          return result;
        });
    TERMINAL.addKeyword("Alert", (p, o) -> parseAlert(p, v -> o.accept(v::create)));

    TERMINAL.addKeyword(
        "Refill",
        (input, output) -> {
          final var name = new AtomicReference<String>();
          final var arguments =
              new AtomicReference<List<Pair<DestructuredArgumentNode, ExpressionNode>>>();
          final var result =
              input
                  .whitespace()
                  .qualifiedIdentifier(name::set)
                  .whitespace()
                  .keyword("With")
                  .whitespace()
                  .list(
                      arguments::set,
                      (ap, ao) -> {
                        final var argName = new AtomicReference<DestructuredArgumentNode>();
                        final var expression = new AtomicReference<ExpressionNode>();
                        final var argResult =
                            ap.whitespace()
                                .then(DestructuredArgumentNode::parse, argName::set)
                                .whitespace()
                                .symbol("=")
                                .whitespace()
                                .then(ExpressionNode::parse, expression::set)
                                .whitespace();
                        if (argResult.isGood()) {
                          ao.accept(new Pair<>(argName.get(), expression.get()));
                        }
                        return argResult;
                      },
                      ',');
          if (result.isGood()) {
            output.accept(
                (line, column, clauses, tags, description) ->
                    new OliveNodeRefill(
                        line, column, name.get(), arguments.get(), clauses, tags, description));
          }
          return result;
        });
    ROOTS.addKeyword("Define", (input, output) -> parseDefine(input, false, output));
    EXPORTABLE.addKeyword("Define", (input, output) -> parseDefine(input, true, output));
    ROOTS.addKeyword(
        "Olive",
        (input, output) -> {
          final var description = new AtomicReference<>("No documentation provided.");
          final var tags = new AtomicReference<List<String>>(List.of());
          final var clauses = new AtomicReference<List<OliveClauseNode>>();
          final var terminal = new AtomicReference<OliveConstructor>();
          final var result =
              input
                  .whitespace()
                  .keyword(
                      "Description",
                      p ->
                          p.whitespace()
                              .symbol("\"")
                              .regex(
                                  DESCRIPTION,
                                  m -> description.set(m.group()),
                                  "Expected string containing description.")
                              .symbol("\"")
                              .whitespace())
                  .list(
                      tags::set,
                      (p, o) ->
                          p.whitespace().keyword("Tag").whitespace().identifier(o).whitespace())
                  .whitespace()
                  .list(clauses::set, OliveClauseNode::parse)
                  .whitespace()
                  .dispatch(TERMINAL, terminal::set)
                  .symbol(";")
                  .whitespace();
          if (result.isGood()) {
            output.accept(
                terminal
                    .get()
                    .create(
                        input.line(),
                        input.column(),
                        clauses.get(),
                        new TreeSet<>(tags.get()),
                        description.get()));
          }
          return result;
        });
    ROOTS.addKeyword("Function", (i, o) -> parseFunction(false, i, o));
    EXPORTABLE.addKeyword("Function", (i, o) -> parseFunction(true, i, o));
    ROOTS.addKeyword("Export", (i, o) -> i.whitespace().dispatch(EXPORTABLE, o));
    ROOTS.addRaw("constant declaration", (input, output) -> parseConstant(input, false, output));
    EXPORTABLE.addRaw(
        "constant declaration", (input, output) -> parseConstant(input, true, output));
  }

  /** Parse a single olive node stanza */
  public static Parser parse(Parser input, Consumer<OliveNode> output) {
    return input.dispatch(ROOTS, output).whitespace();
  }

  public static Parser parseAlert(Parser input, Consumer<OliveAlertConstructor> output) {
    final var labels = new AtomicReference<List<OliveArgumentNode>>();
    final var annotations = new AtomicReference<List<OliveArgumentNode>>(Collections.emptyList());
    final var ttl = new AtomicReference<ExpressionNode>();
    var result = input.whitespace().list(labels::set, OliveArgumentNode::parse, ',').whitespace();
    final var annotationsParser = result.keyword("Annotations");
    if (annotationsParser.isGood()) {
      result =
          annotationsParser
              .whitespace()
              .listEmpty(annotations::set, OliveArgumentNode::parse, ',')
              .whitespace();
    }
    result = result.keyword("For").whitespace().then(ExpressionNode::parse, ttl::set);
    if (result.isGood()) {
      output.accept(
          (line, column, clauses, tags, description) ->
              new OliveNodeAlert(
                  line,
                  column,
                  labels.get(),
                  annotations.get(),
                  ttl.get(),
                  clauses,
                  tags,
                  description));
    }
    return result;
  }

  private static Parser parseConstant(Parser input, boolean exported, Consumer<OliveNode> output) {
    final var name = new AtomicReference<String>();
    final var body = new AtomicReference<ExpressionNode>();
    final var result =
        input
            .whitespace()
            .identifier(name::set)
            .whitespace()
            .symbol("=")
            .whitespace()
            .then(ExpressionNode::parse, body::set)
            .whitespace()
            .symbol(";")
            .whitespace();
    if (result.isGood()) {
      output.accept(
          new OliveNodeConstant(input.line(), input.column(), exported, name.get(), body.get()));
    }
    return result;
  }

  private static Parser parseDefine(Parser input, boolean export, Consumer<OliveNode> output) {
    final var name = new AtomicReference<String>();
    final var params = new AtomicReference<List<OliveParameter>>();
    final var clauses = new AtomicReference<List<OliveClauseNode>>();
    final var result =
        input
            .whitespace()
            .identifier(name::set)
            .whitespace()
            .symbol("(")
            .listEmpty(params::set, OliveParameter::parse, ',')
            .symbol(")")
            .whitespace()
            .list(clauses::set, OliveClauseNode::parse)
            .whitespace()
            .symbol(";")
            .whitespace();
    if (result.isGood()) {
      output.accept(
          new OliveNodeDefinition(
              input.line(), input.column(), export, name.get(), params.get(), clauses.get()));
    }
    return result;
  }

  private static Parser parseFunction(boolean exported, Parser input, Consumer<OliveNode> output) {
    final var name = new AtomicReference<String>();
    final var params = new AtomicReference<List<OliveParameter>>();
    final var body = new AtomicReference<ExpressionNode>();
    final var result =
        input
            .whitespace()
            .identifier(name::set)
            .whitespace()
            .symbol("(")
            .listEmpty(params::set, OliveParameter::parse, ',')
            .symbol(")")
            .whitespace()
            .then(ExpressionNode::parse, body::set)
            .whitespace()
            .symbol(";")
            .whitespace();
    if (result.isGood()) {
      output.accept(
          new OliveNodeFunction(
              input.line(), input.column(), name.get(), exported, params.get(), body.get()));
    }
    return result;
  }

  /**
   * Create {@link OliveDefineBuilder} instances for this olive, if required
   *
   * <p>This is part of bytecode generation and happens well after {@link #collectDefinitions(Map,
   * Map, Consumer)}
   */
  public abstract void build(
      RootBuilder builder, Map<String, CallableDefinitionRenderer> definitions);

  /** Check that every variable that is declare is used somewhere in the program */
  public abstract boolean checkUnusedDeclarations(Consumer<String> errorHandler);

  /** Check the rules that “Call” clauses must only precede “Group” clauses */
  public abstract boolean checkVariableStream(Consumer<String> errorHandler);

  /**
   * Find all the olive plugins
   *
   * <p>This is part of analysis and happens well before {@link #build(RootBuilder, Map)}
   */
  public abstract boolean collectDefinitions(
      Map<String, CallableDefinition> definedOlives,
      Map<String, Target> definedConstants,
      Consumer<String> errorHandler);

  public abstract boolean collectFunctions(
      Predicate<String> isDefined,
      Consumer<FunctionDefinition> defineFunctions,
      Consumer<String> errorHandler);

  public abstract void collectPlugins(Set<Path> pluginFileNames);

  public abstract Stream<OliveTable> dashboard();

  public abstract void processExport(ExportConsumer exportConsumer);

  /** Generate bytecode for this stanza into the {@link ActionGenerator#run} method */
  public abstract void render(
      RootBuilder builder, Function<String, CallableDefinitionRenderer> definitions);

  /** Resolve all variable plugins */
  public abstract boolean resolve(
      OliveCompilerServices oliveCompilerServices, Consumer<String> errorHandler);

  /** Resolve all non-variable plugins */
  public abstract boolean resolveDefinitions(
      OliveCompilerServices oliveCompilerServices, Consumer<String> errorHandler);

  public abstract boolean resolveTypes(
      OliveCompilerServices oliveCompilerServices, Consumer<String> errorHandler);

  /** Type check this olive and all its constituent parts */
  public abstract boolean typeCheck(Consumer<String> errorHandler);
}
