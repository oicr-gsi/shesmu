package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.definitions.ActionDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.InputFormatDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.SignatureDefinition;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.runtime.ActionGenerator;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

/** An olive stanza declaration */
public abstract class OliveNodeWithClauses extends OliveNode {

  private final List<OliveClauseNode> clauses;
  protected final Set<String> signableNames = new TreeSet<>();

  public OliveNodeWithClauses(List<OliveClauseNode> clauses) {
    super();
    this.clauses = clauses;
  }

  /** Check the rules that call clauses must only precede “Group” clauses */
  @Override
  public final boolean checkVariableStream(Consumer<String> errorHandler) {
    ClauseStreamOrder state = ClauseStreamOrder.PURE;
    for (final OliveClauseNode clause : clauses()) {
      state = clause.ensureRoot(state, signableNames, errorHandler);
    }
    if (state == ClauseStreamOrder.PURE) {
      collectArgumentSignableVariables();
    }
    return state != ClauseStreamOrder.BAD;
  }

  /** List all the clauses in this node */
  protected List<OliveClauseNode> clauses() {
    return clauses;
  }

  protected abstract void collectArgumentSignableVariables();

  @Override
  public final boolean collectFunctions(
      Predicate<String> isDefined,
      Consumer<FunctionDefinition> defineFunction,
      Consumer<String> errorHandler) {
    return true;
  }

  @Override
  public final void collectPlugins(Set<Path> pluginFileNames) {
    clauses.forEach(clause -> clause.collectPlugins(pluginFileNames));
    collectPluginsExtra(pluginFileNames);
  }

  public abstract void collectPluginsExtra(Set<Path> pluginFileNames);

  /** Generate bytecode for this stanza into the {@link ActionGenerator#run} method */
  @Override
  public abstract void render(RootBuilder builder, Map<String, OliveDefineBuilder> definitions);

  /** Resolve all variable plugins */
  @Override
  public abstract boolean resolve(
      InputFormatDefinition inputFormatDefinition,
      Supplier<Stream<SignatureDefinition>> signatureDefinitions,
      Function<String, InputFormatDefinition> definedFormats,
      Consumer<String> errorHandler,
      ConstantRetriever constants);

  /**
   * Resolve all non-variable plugins
   *
   * <p>This does the clauses and {@link #resolveDefinitionsExtra(Map, Function, Function,
   * Consumer)}
   */
  @Override
  public final boolean resolveDefinitions(
      Map<String, OliveNodeDefinition> definedOlives,
      Function<String, FunctionDefinition> definedFunctions,
      Function<String, ActionDefinition> definedActions,
      Set<String> metricNames,
      Map<String, List<Imyhat>> dumpers,
      Consumer<String> errorHandler) {
    final boolean clausesOk =
        clauses
                .stream()
                .filter(
                    clause ->
                        clause.resolveDefinitions(
                            definedOlives,
                            definedFunctions,
                            definedActions,
                            metricNames,
                            dumpers,
                            errorHandler))
                .count()
            == clauses.size();
    return clausesOk
        & resolveDefinitionsExtra(definedOlives, definedFunctions, definedActions, errorHandler);
  }

  /** Do any further non-variable definition resolution specific to this class */
  protected abstract boolean resolveDefinitionsExtra(
      Map<String, OliveNodeDefinition> definedOlives,
      Function<String, FunctionDefinition> definedFunctions,
      Function<String, ActionDefinition> definedActions,
      Consumer<String> errorHandler);

  /** Type check this olive and all its constituent parts */
  @Override
  public final boolean typeCheck(Consumer<String> errorHandler) {
    for (final OliveClauseNode clause : clauses) {
      if (!clause.typeCheck(errorHandler)) {
        return false;
      }
    }
    return typeCheckExtra(errorHandler);
  }

  protected abstract boolean typeCheckExtra(Consumer<String> errorHandler);
}
