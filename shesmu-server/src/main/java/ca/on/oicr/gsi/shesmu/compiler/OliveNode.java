package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.definitions.ActionDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.InputFormatDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.SignatureDefinition;
import ca.on.oicr.gsi.shesmu.compiler.description.OliveTable;
import ca.on.oicr.gsi.shesmu.plugin.Parser;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.runtime.ActionGenerator;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** An olive stanza declaration */
public abstract class OliveNode {
  protected enum ClauseStreamOrder {
    BAD,
    PURE,
    TRANSFORMED
  }

  private interface OliveConstructor {
    OliveNode create(
        int line, int column, List<OliveClauseNode> clauses, Set<String> tags, String description);
  }

  private static final Parser.ParseDispatch<OliveNode> ROOTS = new Parser.ParseDispatch<>();
  private static final Parser.ParseDispatch<OliveConstructor> TERMINAL =
      new Parser.ParseDispatch<>();

  private static final Pattern DESCRIPTION = Pattern.compile("[^\"\\n]*");

  static {
    TERMINAL.addKeyword(
        "Run",
        (input, output) -> {
          final AtomicReference<String> name = new AtomicReference<>();
          final AtomicReference<List<OliveArgumentNode>> arguments = new AtomicReference<>();
          final Parser result =
              input
                  .whitespace()
                  .identifier(name::set)
                  .whitespace()
                  .keyword("With")
                  .whitespace()
                  .list(arguments::set, OliveArgumentNode::parse, ',');
          if (result.isGood()) {
            output.accept(
                (line, column, clauses, tags, description) ->
                    new OliveNodeRun(
                        line, column, name.get(), arguments.get(), clauses, tags, description));
          }
          return result;
        });
    TERMINAL.addKeyword(
        "Alert",
        (input, output) -> {
          final AtomicReference<List<OliveArgumentNode>> labels = new AtomicReference<>();
          final AtomicReference<List<OliveArgumentNode>> annotations =
              new AtomicReference<>(Collections.emptyList());
          final AtomicReference<ExpressionNode> ttl = new AtomicReference<>();
          Parser result =
              input.whitespace().list(labels::set, OliveArgumentNode::parse, ',').whitespace();
          final Parser annotationsParser = result.keyword("Annotations");
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
        });

    ROOTS.addKeyword(
        "Define",
        (input, output) -> {
          final AtomicReference<String> name = new AtomicReference<>();
          final AtomicReference<List<OliveParameter>> params = new AtomicReference<>();
          final AtomicReference<List<OliveClauseNode>> clauses = new AtomicReference<>();
          final Parser result =
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
                    input.line(), input.column(), name.get(), params.get(), clauses.get()));
          }
          return result;
        });
    ROOTS.addKeyword(
        "Olive",
        (input, output) -> {
          final AtomicReference<String> description =
              new AtomicReference<>("No documentation provided.");
          final AtomicReference<List<String>> tags = new AtomicReference<>(Collections.emptyList());
          final AtomicReference<List<OliveClauseNode>> clauses = new AtomicReference<>();
          final AtomicReference<OliveConstructor> terminal = new AtomicReference<>();
          final Parser result =
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
    ROOTS.addKeyword(
        "Function",
        (input, output) -> {
          final AtomicReference<String> name = new AtomicReference<>();
          final AtomicReference<List<OliveParameter>> params = new AtomicReference<>();
          final AtomicReference<ExpressionNode> body = new AtomicReference<>();
          final Parser result =
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
                    input.line(), input.column(), name.get(), params.get(), body.get()));
          }
          return result;
        });
    ROOTS.addRaw(
        "constant declaration",
        (input, output) -> {
          final AtomicReference<String> name = new AtomicReference<>();
          final AtomicReference<ExpressionNode> body = new AtomicReference<>();
          final Parser result =
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
                new OliveNodeConstant(input.line(), input.column(), name.get(), body.get()));
          }
          return result;
        });
  }

  /** Parse a single olive node stanza */
  public static Parser parse(Parser input, Consumer<OliveNode> output) {
    return input.dispatch(ROOTS, output).whitespace();
  }

  /**
   * Create {@link OliveDefineBuilder} instances for this olive, if required
   *
   * <p>This is part of bytecode generation and happens well after {@link #collectDefinitions(Map,
   * Map, Consumer)}
   */
  public abstract void build(RootBuilder builder, Map<String, OliveDefineBuilder> definitions);

  /** Check the rules that “Call” clauses must only precede “Group” clauses */
  public abstract boolean checkVariableStream(Consumer<String> errorHandler);

  /**
   * Find all the olive plugins
   *
   * <p>This is part of analysis and happens well before {@link #build(RootBuilder, Map)}
   */
  public abstract boolean collectDefinitions(
      Map<String, OliveNodeDefinition> definedOlives,
      Map<String, Target> definedConstants,
      Consumer<String> errorHandler);

  public abstract boolean collectFunctions(
      Predicate<String> isDefined,
      Consumer<FunctionDefinition> defineFunctions,
      Consumer<String> errorHandler);

  public abstract void collectPlugins(Set<Path> pluginFileNames);

  public abstract Stream<OliveTable> dashboard();

  /** Generate bytecode for this stanza into the {@link ActionGenerator#run} method */
  public abstract void render(RootBuilder builder, Map<String, OliveDefineBuilder> definitions);

  /** Resolve all variable plugins */
  public abstract boolean resolve(
      InputFormatDefinition inputFormatDefinition,
      Supplier<Stream<SignatureDefinition>> signatureDefinitions,
      Function<String, InputFormatDefinition> definedFormats,
      Consumer<String> errorHandler,
      ConstantRetriever constants);

  /** Resolve all non-variable plugins */
  public abstract boolean resolveDefinitions(
      Map<String, OliveNodeDefinition> definedOlives,
      Function<String, FunctionDefinition> definedFunctions,
      Function<String, ActionDefinition> definedActions,
      Set<String> metricNames,
      Map<String, List<Imyhat>> dumpers,
      Consumer<String> errorHandler);

  public abstract boolean resolveTypes(
      Function<String, Imyhat> definedTypes, Consumer<String> errorHandler);

  /** Type check this olive and all its constituent parts */
  public abstract boolean typeCheck(Consumer<String> errorHandler);
}
