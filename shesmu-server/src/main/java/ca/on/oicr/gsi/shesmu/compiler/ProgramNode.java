package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.ActionDefinition;
import ca.on.oicr.gsi.shesmu.ConstantDefinition;
import ca.on.oicr.gsi.shesmu.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.InputFormatDefinition;
import ca.on.oicr.gsi.shesmu.compiler.description.FileTable;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Representation of a complete Shesmu script */
public class ProgramNode {
  /** Parse a file of olive nodes */
  public static boolean parseFile(
      CharSequence input, Consumer<ProgramNode> output, ErrorConsumer errorHandler) {
    final AtomicReference<Pair<Integer, Integer>> start = new AtomicReference<>();
    final AtomicReference<String> inputFormat = new AtomicReference<>();
    final AtomicReference<List<PragmaNode>> pragmas = new AtomicReference<>();
    final AtomicReference<List<TypeAliasNode>> typeAliases = new AtomicReference<>();
    final AtomicReference<List<OliveNode>> olives = new AtomicReference<>();
    final Parser result =
        Parser.start(input, errorHandler)
            .whitespace()
            .location(start::set)
            .keyword("Input")
            .whitespace()
            .identifier(inputFormat::set)
            .whitespace()
            .symbol(";")
            .whitespace()
            .list(pragmas::set, PragmaNode::parse)
            .list(typeAliases::set, TypeAliasNode::parse)
            .list(olives::set, OliveNode::parse)
            .whitespace();
    if (result.isGood()) {
      if (result.isEmpty()) {
        output.accept(
            new ProgramNode(
                start.get().first(),
                start.get().second(),
                inputFormat.get(),
                pragmas.get(),
                typeAliases.get(),
                olives.get()));
        return true;
      } else {
        errorHandler.raise(result.line(), result.column(), "Junk at end of file.");
      }
    }
    return false;
  }

  private final int column;

  private final String input;

  private InputFormatDefinition inputFormatDefinition;

  private final int line;

  private final List<OliveNode> olives;

  private final List<TypeAliasNode> typeAliases;

  private final List<PragmaNode> pragmas;

  public ProgramNode(
      int line,
      int column,
      String input,
      List<PragmaNode> pragmas,
      List<TypeAliasNode> typeAliases,
      List<OliveNode> olives) {
    super();
    this.line = line;
    this.column = column;
    this.input = input;
    this.pragmas = pragmas;
    this.typeAliases = typeAliases;
    this.olives = olives;
  }

  public FileTable dashboard(String filename, Instant timestamp, String bytecode) {
    return new FileTable(
        filename,
        inputFormatDefinition,
        timestamp,
        bytecode,
        olives.stream().flatMap(OliveNode::dashboard));
  }

  public InputFormatDefinition inputFormatDefinition() {
    return inputFormatDefinition;
  }

  /** Generate bytecode for this definition */
  public void render(RootBuilder builder) {
    final Map<String, OliveDefineBuilder> definitions = new HashMap<>();
    pragmas.forEach(pragma -> pragma.renderGuard(builder));
    olives.forEach(olive -> olive.build(builder, definitions));
    olives.forEach(olive -> olive.render(builder, definitions));
  }

  public int timeout() {
    AtomicInteger timeout = new AtomicInteger(20);
    pragmas.forEach(pragma -> pragma.timeout(timeout));
    return timeout.get();
  }

  /**
   * Check that a collection of olives, assumed to be a self-contained program, is well-formed.
   *
   * @param definedFunctions the functions available; if a function is not found, null should be
   *     returned
   * @param definedActions the actions available; if an action is not found, null should be returned
   * @param constants
   */
  public boolean validate(
      Function<String, InputFormatDefinition> inputFormatDefinitions,
      Function<String, FunctionDefinition> definedFunctions,
      Function<String, ActionDefinition> definedActions,
      Consumer<String> errorHandler,
      Supplier<Stream<ConstantDefinition>> constants) {

    inputFormatDefinition = inputFormatDefinitions.apply(input);
    if (inputFormatDefinition == null) {
      errorHandler.accept(
          String.format(
              "%d:%d: No input format of data named “%s” is available.", line, column, input));
      return false;
    }
    // Find and resolve olive “Define” and “Matches”
    final Map<String, OliveNodeDefinition> definedOlives = new HashMap<>();
    final Set<String> metricNames = new HashSet<>();
    final Map<String, List<Imyhat>> dumpers = new HashMap<>();
    final Map<String, FunctionDefinition> userDefinedFunctions = new HashMap<>();
    final Map<String, Target> userDefinedConstants = new HashMap<>();
    final Map<String, Imyhat> userDefinedTypes =
        Stream.<Target>concat(
                inputFormatDefinition.baseStreamVariables(),
                NameDefinitions.signatureVariables().<Target>map(x -> x))
            .collect(Collectors.toMap(t -> t.name() + "_type", Target::type));

    for (final TypeAliasNode alias : typeAliases) {
      final Imyhat type = alias.resolve(userDefinedTypes::get, errorHandler);
      if (type.isBad()) {
        return false;
      }
      userDefinedTypes.put(alias.name(), type);
    }

    boolean ok =
        olives
                .stream()
                .filter(
                    olive ->
                        olive.collectDefinitions(definedOlives, userDefinedConstants, errorHandler))
                .count()
            == olives.size();
    ok =
        ok
            && olives
                .stream()
                .allMatch(olive -> olive.resolveTypes(userDefinedTypes::get, errorHandler));
    ok =
        ok
            && olives
                .stream()
                .allMatch(
                    olive ->
                        olive.collectFunctions(
                            name ->
                                userDefinedFunctions.containsKey(name)
                                    || definedFunctions.apply(name) != null,
                            f -> userDefinedFunctions.put(f.name(), f),
                            errorHandler));
    ok =
        ok
            && olives
                    .stream()
                    .filter(
                        olive ->
                            olive.resolveDefinitions(
                                definedOlives,
                                n ->
                                    userDefinedFunctions.containsKey(n)
                                        ? userDefinedFunctions.get(n)
                                        : definedFunctions.apply(n),
                                definedActions,
                                metricNames,
                                dumpers,
                                errorHandler))
                    .count()
                == olives.size();

    // Resolve variables
    ok =
        ok
            && olives
                    .stream()
                    .filter(
                        olive ->
                            olive.resolve(
                                inputFormatDefinition,
                                inputFormatDefinitions,
                                errorHandler,
                                allowUserDefined -> {
                                  final Stream<ConstantDefinition> externalConstants =
                                      constants.get();
                                  return allowUserDefined
                                      ? Stream.concat(
                                          userDefinedConstants.values().stream(), externalConstants)
                                      : externalConstants;
                                }))
                    .count()
                == olives.size();
    ok =
        ok
            && olives.stream().filter(olive -> olive.checkVariableStream(errorHandler)).count()
                == olives.size();

    // Type check the resolved structure
    ok =
        ok
            && olives.stream().filter(olive -> olive.typeCheck(errorHandler)).count()
                == olives.size();
    return ok;
  }
}
