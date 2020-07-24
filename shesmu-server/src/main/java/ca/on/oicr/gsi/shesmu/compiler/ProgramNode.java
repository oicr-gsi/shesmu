package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.compiler.definitions.*;
import ca.on.oicr.gsi.shesmu.compiler.definitions.ConstantDefinition.AliasedConstantDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.SignatureDefinition.AliasedSignatureDefintion;
import ca.on.oicr.gsi.shesmu.compiler.description.FileTable;
import ca.on.oicr.gsi.shesmu.plugin.ErrorConsumer;
import ca.on.oicr.gsi.shesmu.plugin.Parser;
import ca.on.oicr.gsi.shesmu.plugin.Parser.Rule;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Representation of a complete Shesmu script */
public class ProgramNode {
  private static final Map<Long, Rule<BiFunction<Integer, Integer, ProgramNode>>> VERSIONS =
      new TreeMap<>();

  static {
    VERSIONS.put(
        1L,
        (parser, output) -> {
          final AtomicReference<String> inputFormat = new AtomicReference<>();
          final AtomicReference<List<PragmaNode>> pragmas = new AtomicReference<>();
          final AtomicReference<List<TypeAliasNode>> typeAliases = new AtomicReference<>();
          final AtomicReference<List<OliveNode>> olives = new AtomicReference<>();
          final Parser result =
              parser
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
            output.accept(
                (line, column) ->
                    new ProgramNode(
                        line,
                        column,
                        inputFormat.get(),
                        pragmas.get(),
                        typeAliases.get(),
                        olives.get()));
          }
          return result;
        });
  }
  /** Parse a file of olive nodes */
  public static boolean parseFile(
      CharSequence input, Consumer<ProgramNode> output, ErrorConsumer errorHandler) {

    // This is a bit weird; we want to support multiple versions of the olive language in the
    // future, so we parse the version and the version gives us the parser for the rest of the file.
    final AtomicReference<Pair<Integer, Integer>> start = new AtomicReference<>();
    final AtomicLong version = new AtomicLong();
    Parser result =
        Parser.start(input, errorHandler)
            .whitespace()
            .location(start::set)
            .keyword("Version")
            .whitespace()
            .integer(version::set, 10)
            .whitespace()
            .symbol(";")
            .whitespace();

    if (result.isGood()) {
      result =
          result.then(
              VERSIONS.getOrDefault(
                  version.get(),
                  (p, o) -> p.raise("Version is not supported by this Shesmu server.")),
              f -> output.accept(f.apply(start.get().first(), start.get().second())));
    }
    return result.finished();
  }

  private final int column;

  private final String input;

  private InputFormatDefinition inputFormatDefinition;

  private final int line;

  private final List<OliveNode> olives;
  private final List<PragmaNode> pragmas;
  private final List<TypeAliasNode> typeAliases;

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

  public void collectPlugins(Set<Path> pluginFileNames) {
    olives.forEach(olive -> olive.collectPlugins(pluginFileNames));
  }

  public FileTable dashboard(String filename, String hash, String bytecode) {
    return new FileTable(
        filename,
        inputFormatDefinition,
        hash,
        bytecode,
        olives.stream().flatMap(OliveNode::dashboard));
  }

  public InputFormatDefinition inputFormatDefinition() {
    return inputFormatDefinition;
  }

  public void processExports(ExportConsumer exportConsumer) {
    olives.forEach(olive -> olive.processExport(exportConsumer));
  }

  /** Generate bytecode for this definition */
  public void render(RootBuilder builder) {
    final Map<String, OliveDefineBuilder> definitions = new HashMap<>();
    pragmas.forEach(pragma -> pragma.renderGuard(builder));
    olives.forEach(olive -> olive.build(builder, definitions));
    olives.forEach(olive -> olive.render(builder, definitions));
    pragmas.forEach(pragma -> pragma.renderAtExit(builder));
  }

  public int timeout() {
    AtomicInteger timeout = new AtomicInteger(20 * 60);
    pragmas.forEach(pragma -> pragma.timeout(timeout));
    return timeout.get();
  }

  /**
   * Check that a collection of olives, assumed to be a self-contained program, is well-formed.
   *
   * @param allowDuplicates allow duplicate function names; only useful when checking
   * @param definedFunctions the functions available; if a function is not found, null should be
   *     returned
   * @param definedActions the actions available; if an action is not found, null should be returned
   */
  public boolean validate(
      Function<String, InputFormatDefinition> inputFormatDefinitions,
      Function<String, FunctionDefinition> definedFunctions,
      Function<String, ActionDefinition> definedActions,
      Function<String, RefillerDefinition> definedRefillers,
      Consumer<String> errorHandler,
      Supplier<Stream<ConstantDefinition>> constants,
      Supplier<Stream<SignatureDefinition>> signatures,
      boolean allowDuplicates) {

    inputFormatDefinition = inputFormatDefinitions.apply(input);
    if (inputFormatDefinition == null) {
      errorHandler.accept(
          String.format(
              "%d:%d: No input format of data named “%s” is available.", line, column, input));
      return false;
    }
    final Map<String, Imyhat> userDefinedTypes =
        InputFormatDefinition.predefinedTypes(signatures.get(), inputFormatDefinition);
    final Map<String, OliveNodeDefinition> definedOlives = new HashMap<>();
    final Map<String, FunctionDefinition> userDefinedFunctions = new HashMap<>();
    final Map<String, Target> userDefinedConstants = new HashMap<>();
    final List<ImportRewriter> importRewriters =
        Stream.concat(Stream.of(ImportRewriter.NULL), pragmas.stream().flatMap(PragmaNode::imports))
            .collect(Collectors.toList());
    // Find and resolve olive “Define” and “Matches”
    final OliveCompilerServices compilerServices =
        new OliveCompilerServices() {
          final Map<String, List<Imyhat>> dumpers = new HashMap<>();
          final Set<String> metricNames = new HashSet<>();

          @Override
          public ActionDefinition action(String name) {
            return checkImports(definedActions, name);
          }

          @Override
          public boolean addMetric(String metricName) {
            if (metricNames.contains(metricName)) return true;
            metricNames.add(metricName);
            return false;
          }

          private <T> T checkImports(Function<String, T> getter, String name) {
            for (final ImportRewriter importRewriter : importRewriters) {
              final String original = importRewriter.rewrite(name);
              if (original == null) continue;
              final T result = getter.apply(original);
              if (result != null) {
                return result;
              }
            }
            return null;
          }

          @Override
          public Stream<? extends Target> constants(boolean allowUserDefined) {
            return allowUserDefined
                ? Stream.concat(
                    duplicateImport(constants.get(), AliasedConstantDefinition::new),
                    userDefinedConstants.values().stream())
                : constants.get();
          }

          private <T extends Target> Stream<T> duplicateImport(
              Stream<T> input, BiFunction<T, String, T> alias) {
            return input.flatMap(
                item ->
                    importRewriters
                        .stream()
                        .map(importRewriter -> importRewriter.rewrite(item.name()))
                        .filter(Objects::nonNull)
                        .map(name -> alias.apply(item, name)));
          }

          @Override
          public FunctionDefinition function(String name) {
            final FunctionDefinition external = checkImports(definedFunctions, name);
            return external == null ? userDefinedFunctions.get(name) : external;
          }

          @Override
          public Imyhat imyhat(String name) {
            return userDefinedTypes.get(name);
          }

          @Override
          public InputFormatDefinition inputFormat() {
            return inputFormatDefinition;
          }

          @Override
          public InputFormatDefinition inputFormat(String format) {
            return inputFormatDefinitions.apply(format);
          }

          @Override
          public OliveNodeDefinition olive(String name) {
            return definedOlives.get(name);
          }

          @Override
          public RefillerDefinition refiller(String name) {
            return checkImports(definedRefillers, name);
          }

          @Override
          public Stream<SignatureDefinition> signatures() {
            return duplicateImport(signatures.get(), AliasedSignatureDefintion::new);
          }

          @Override
          public List<Imyhat> upsertDumper(String dumper) {
            return dumpers.computeIfAbsent(dumper, k -> new ArrayList<>());
          }
        };

    for (final TypeAliasNode alias : typeAliases) {
      final Imyhat type = alias.resolve(compilerServices, errorHandler);
      if (type.isBad()) {
        return false;
      }
      userDefinedTypes.put(alias.name(), type);
    }

    boolean ok =
        pragmas.stream().filter(pragma -> pragma.check(compilerServices, errorHandler)).count()
                == pragmas.size()
            & olives
                    .stream()
                    .filter(
                        olive ->
                            olive.collectDefinitions(
                                definedOlives, userDefinedConstants, errorHandler))
                    .count()
                == olives.size();
    ok =
        ok && olives.stream().allMatch(olive -> olive.resolveTypes(compilerServices, errorHandler));
    ok =
        ok
            && olives
                .stream()
                .allMatch(
                    olive ->
                        olive.collectFunctions(
                            name ->
                                userDefinedFunctions.containsKey(name)
                                    || !allowDuplicates && definedFunctions.apply(name) != null,
                            f -> userDefinedFunctions.put(f.name(), f),
                            errorHandler));
    ok =
        ok
            && olives
                    .stream()
                    .filter(olive -> olive.resolveDefinitions(compilerServices, errorHandler))
                    .count()
                == olives.size();

    // Resolve variables
    ok =
        ok
            && olives
                    .stream()
                    .filter(olive -> olive.resolve(compilerServices, errorHandler))
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
    // Check for unused definitions
    ok =
        ok
            && olives.stream().filter(olive -> olive.checkUnusedDeclarations(errorHandler)).count()
                == olives.size();
    return ok;
  }
}
