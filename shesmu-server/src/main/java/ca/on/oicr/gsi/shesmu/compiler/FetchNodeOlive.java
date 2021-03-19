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
  private static final class InjectedTarget implements Target {
    private final Target inner;
    private final String name;
    private final String unaliasedName;
    private boolean read;

    private InjectedTarget(Target inner, boolean prefixed) {
      this.inner = inner;
      this.unaliasedName =
          String.join(Parser.NAMESPACE_SEPARATOR, "shesmu", "simulated", inner.name());
      name = prefixed ? unaliasedName : inner.name();
    }

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
      read = true;
      inner.read();
    }

    @Override
    public Imyhat type() {
      return inner.type();
    }

    @Override
    public String unaliasedName() {
      return unaliasedName;
    }
  }

  private final List<OliveClauseNode> clauses;
  private final int column;
  private final String format;
  private InputFormatDefinition formatDefinition = InputFormatDefinition.DUMMY;
  private List<InjectedTarget> injections;
  private final int line;
  private OliveCompilerServices oliveCompilerServices;
  private List<Target> refillerTypes;
  private final String script;
  private Imyhat type = Imyhat.BAD;

  public FetchNodeOlive(
      int line,
      int column,
      String name,
      String format,
      List<OliveClauseNode> clauses,
      String script) {
    super(name);
    this.clauses = clauses;
    this.column = column;
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
    final var fullScript = new StringBuilder();
    fullScript
        .append("Version 1; Input ")
        .append(format)
        .append("; Import shesmu::simulated::*; Olive ")
        .append(script)
        .append("\nRefill export_to_meditation With ");
    var first = true;
    for (final var refillerType : refillerTypes) {
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
          refillerTypes.stream()
              .map(t -> t.name() + ": \"" + t.type().descriptor() + "\"")
              .collect(Collectors.joining(", ", "{", "}")),
          injections.stream()
              .filter(c -> c.read)
              .map(
                  c -> {
                    try {
                      return String.format(
                          "%s: { type: \"%s\", value: %s}",
                          RuntimeSupport.MAPPER.writeValueAsString(c.inner.name()),
                          c.type().descriptor(),
                          r.load(c.inner));
                    } catch (JsonProcessingException e) {
                      throw new RuntimeException(e);
                    }
                  })
              .collect(Collectors.joining(", ", "{", "}")));
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    injections =
        defs.stream()
            .filter(t -> t.flavour() != Flavour.CONSTANT)
            .flatMap(t -> Stream.of(new InjectedTarget(t, true), new InjectedTarget(t, false)))
            .collect(Collectors.toList());
    var state = ClauseStreamOrder.PURE;
    final var clauseDefs =
        clauses.stream()
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
          clauseDefs.stream()
              .filter(v -> v.flavour().isStream() && Parser.IDENTIFIER.matcher(v.name()).matches())
              .collect(Collectors.toList());
      final Set<String> signableNames = new TreeSet<>();
      for (final var clause : clauses) {
        state = clause.ensureRoot(state, signableNames, v -> {}, errorHandler);
      }
      return state != ClauseStreamOrder.BAD;
    } else {
      return false;
    }
  }

  @Override
  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices,
      DefinitionRepository nativeDefinitions,
      Consumer<String> errorHandler) {

    formatDefinition = expressionCompilerServices.inputFormat(format);
    if (formatDefinition == null) {
      errorHandler.accept(String.format("%d:%d: Unknown input format %s.", line, column, format));
      return false;
    }
    oliveCompilerServices =
        new OliveCompilerServices() {
          private final NameLoader<CallableDefinition> callables =
              new NameLoader<>(nativeDefinitions.oliveDefinitions(), CallableDefinition::name);
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
            return Stream.concat(nativeDefinitions.constants(), injections.stream());
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
            return callables.get(name);
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
    return clauses.stream()
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
    final var result =
        clauses.stream().filter(c -> c.typeCheck(errorHandler)).count() == clauses.size();
    if (result) {
      type = new ObjectImyhat(refillerTypes.stream().map(t -> new Pair<>(t.name(), t.type())));
    }
    return result;
  }
}
