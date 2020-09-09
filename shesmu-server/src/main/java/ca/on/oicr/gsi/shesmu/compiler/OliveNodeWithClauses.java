package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.definitions.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.runtime.ActionGenerator;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/** An olive stanza declaration */
public abstract class OliveNodeWithClauses extends OliveNode {

  private final List<OliveClauseNode> clauses;
  protected final Set<String> signableNames = new TreeSet<>();
  protected final List<SignableVariableCheck> signableVariableChecks = new ArrayList<>();

  public OliveNodeWithClauses(List<OliveClauseNode> clauses) {
    super();
    this.clauses = clauses;
  }

  @Override
  public final boolean checkUnusedDeclarations(Consumer<String> errorHandler) {
    return checkUnusedDeclarationsExtra(errorHandler) & skipCheckUnusedDeclarations()
        || clauses.stream().filter(c -> c.checkUnusedDeclarations(errorHandler)).count()
            == clauses.size();
  }

  public abstract boolean checkUnusedDeclarationsExtra(Consumer<String> errorHandler);

  /** Check the rules that call clauses must only precede “Group” clauses */
  @Override
  public final boolean checkVariableStream(Consumer<String> errorHandler) {
    ClauseStreamOrder state = ClauseStreamOrder.PURE;
    for (final OliveClauseNode clause : clauses()) {
      state = clause.ensureRoot(state, signableNames, signableVariableChecks::add, errorHandler);
    }
    if (state == ClauseStreamOrder.PURE || state == ClauseStreamOrder.ALMOST_PURE) {
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
  public abstract void render(
      RootBuilder builder, Function<String, CallableDefinitionRenderer> definitions);

  /** Resolve all variable plugins */
  @Override
  public abstract boolean resolve(
      OliveCompilerServices oliveCompilerServices, Consumer<String> errorHandler);

  /**
   * Resolve all non-variable plugins
   *
   * <p>This does the clauses and {@link #resolveDefinitionsExtra(OliveCompilerServices, Consumer)}
   */
  @Override
  public final boolean resolveDefinitions(
      OliveCompilerServices oliveCompilerServices, Consumer<String> errorHandler) {
    final boolean clausesOk =
        clauses
                .stream()
                .filter(clause -> clause.resolveDefinitions(oliveCompilerServices, errorHandler))
                .count()
            == clauses.size();
    return clausesOk & resolveDefinitionsExtra(oliveCompilerServices, errorHandler);
  }

  /** Do any further non-variable definition resolution specific to this class */
  protected abstract boolean resolveDefinitionsExtra(
      OliveCompilerServices oliveCompilerServices, Consumer<String> errorHandler);

  public abstract boolean skipCheckUnusedDeclarations();

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
