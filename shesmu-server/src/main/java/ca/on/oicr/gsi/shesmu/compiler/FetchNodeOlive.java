package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.compiler.OliveNode.ClauseStreamOrder;
import ca.on.oicr.gsi.shesmu.compiler.definitions.ActionDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.DefinitionRepository;
import ca.on.oicr.gsi.shesmu.compiler.definitions.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.InputFormatDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.InputVariable;
import ca.on.oicr.gsi.shesmu.compiler.definitions.SignatureDefinition;
import ca.on.oicr.gsi.shesmu.plugin.Parser;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat.ObjectImyhat;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import ca.on.oicr.gsi.shesmu.util.NameLoader;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FetchNodeOlive extends FetchNode {
  private final List<OliveClauseNode> clauses;
  private final int column;
  private final List<ObjectElementNode> constants;
  private final String format;
  private InputFormatDefinition formatDefinition = InputFormatDefinition.DUMMY;
  private final int line;
  private OliveCompilerServices oliveCompilerServices;
  private List<Target> refillerTypes;
  private final String script;
  private Imyhat type = Imyhat.BAD;

  public FetchNodeOlive(
      int line,
      int column,
      String name,
      List<ObjectElementNode> constants,
      String format,
      List<OliveClauseNode> clauses,
      String script) {
    super(name);
    this.clauses = clauses;
    this.column = column;
    this.constants = constants;
    this.format = format;
    this.line = line;
    this.script = script;
  }

  @Override
  public Flavour flavour() {
    return Flavour.LAMBDA;
  }

  @Override
  public void read() {
    // Do nothing
  }

  @Override
  public String renderEcma(EcmaScriptRenderer r) {
    final StringBuilder fullScript = new StringBuilder();
    fullScript
        .append("Version 1; Input ")
        .append(format)
        .append(";Olive ")
        .append(script)
        .append("\nRefill export_to_meditation With ");
    boolean first = true;
    for (final Target refillerType : refillerTypes) {
      if (first) {
        first = false;
      } else {
        fullScript.append(", ");
      }
      fullScript.append(refillerType.name()).append(" = ").append(refillerType.name());
    }
    fullScript.append(";");

    try {
      return String.format(
          "{type: \"refiller\", compare: (a, b) => %s, script: %s, fakeRefiller: {export_to_meditation: %s}, fakeConstants: %s}",
          type.apply(EcmaScriptRenderer.COMPARATOR),
          RuntimeSupport.MAPPER.writeValueAsString(fullScript.toString()),
          refillerTypes
              .stream()
              .map(t -> t.name() + ": \"" + t.type().descriptor() + "\"")
              .collect(Collectors.joining(", ", "{", "}")),
          constants
              .stream()
              .flatMap(c -> c.renderConstant(r))
              .collect(Collectors.joining(", ", "{", "}")));
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    if (constants.stream().filter(c -> c.resolve(defs, errorHandler)).count() != constants.size()) {
      return false;
    }
    ClauseStreamOrder state = ClauseStreamOrder.PURE;
    final Set<String> signableNames = new TreeSet<>();
    for (final OliveClauseNode clause : clauses) {
      state = clause.ensureRoot(state, signableNames, v -> {}, errorHandler);
    }
    if (state == ClauseStreamOrder.BAD) {
      return false;
    }
    final NameDefinitions clauseDefs =
        clauses
            .stream()
            .reduce(
                NameDefinitions.root(
                    formatDefinition,
                    oliveCompilerServices.constants(false),
                    oliveCompilerServices.signatures()),
                (d, clause) -> clause.resolve(oliveCompilerServices, d, errorHandler),
                (a, b) -> {
                  throw new UnsupportedOperationException();
                });
    if (clauseDefs.isGood()) {
      refillerTypes =
          clauseDefs
              .stream()
              .filter(v -> v.flavour().isStream() && Parser.IDENTIFIER.matcher(v.name()).matches())
              .collect(Collectors.toList());
      return true;
    } else {
      return false;
    }
  }

  @Override
  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices,
      DefinitionRepository nativeDefinitions,
      Consumer<String> errorHandler) {
    if (constants
            .stream()
            .filter(c -> c.resolveDefinitions(expressionCompilerServices, errorHandler))
            .count()
        != constants.size()) {
      return false;
    }
    formatDefinition = expressionCompilerServices.inputFormat(format);
    if (formatDefinition == null) {
      errorHandler.accept(String.format("%d:%d: Unknown input format %s.", line, column, format));
      return false;
    }
    oliveCompilerServices =
        new OliveCompilerServices() {
          final Map<String, DumperDefinition> dumpers = new HashMap<>();
          private final NameLoader<FunctionDefinition> functions =
              new NameLoader<>(nativeDefinitions.functions(), FunctionDefinition::name);
          private final Set<String> metrics = new TreeSet<>();
          private final Map<String, Imyhat> types =
              formatDefinition
                  .baseStreamVariables()
                  .collect(Collectors.toMap(v -> v.name() + "_type", InputVariable::type));

          @Override
          public ActionDefinition action(String name) {
            return null;
          }

          @Override
          public boolean addMetric(String metricName) {
            return metrics.add(metricName);
          }

          @Override
          public Stream<? extends Target> constants(boolean allowUserDefined) {
            return Stream.concat(
                nativeDefinitions.constants(),
                constants
                    .stream()
                    .flatMap(ObjectElementNode::names)
                    .map(
                        p ->
                            new Target() {
                              private final String name =
                                  String.join(
                                      Parser.NAMESPACE_SEPARATOR, "shesmu", "simulated", p.first());
                              private final Imyhat type = p.second();

                              @Override
                              public Flavour flavour() {
                                return Flavour.CONSTANT;
                              }

                              @Override
                              public String name() {
                                return name;
                              }

                              @Override
                              public void read() {
                                // Do nothing.
                              }

                              @Override
                              public Imyhat type() {
                                return type;
                              }
                            }));
          }

          @Override
          public FunctionDefinition function(String name) {
            return functions.get(name);
          }

          @Override
          public Imyhat imyhat(String name) {
            return types.get(name);
          }

          @Override
          public InputFormatDefinition inputFormat(String format) {
            return expressionCompilerServices.inputFormat(format);
          }

          @Override
          public InputFormatDefinition inputFormat() {
            return formatDefinition;
          }

          @Override
          public CallableDefinition olive(String name) {
            return null;
          }

          @Override
          public RefillerDefinition refiller(String name) {
            return null;
          }

          @Override
          public Stream<SignatureDefinition> signatures() {
            return nativeDefinitions.signatures();
          }

          @Override
          public DumperDefinition upsertDumper(String dumper) {
            return dumpers.computeIfAbsent(dumper, DumperDefinition::new);
          }
        };
    return clauses
            .stream()
            .filter(c -> c.resolveDefinitions(oliveCompilerServices, errorHandler))
            .count()
        == clauses.size();
  }

  @Override
  public Imyhat type() {
    return type.asList();
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    final boolean result =
        constants.stream().filter(c -> c.typeCheck(errorHandler)).count() == constants.size()
            && clauses.stream().filter(c -> c.typeCheck(errorHandler)).count() == clauses.size();
    if (result) {
      type = new ObjectImyhat(refillerTypes.stream().map(t -> new Pair<>(t.name(), t.type())));
    }
    return result;
  }
}
