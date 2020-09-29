package ca.on.oicr.gsi.shesmu.compiler;

import static ca.on.oicr.gsi.shesmu.compiler.TypeUtils.TO_ASM;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.compiler.definitions.GangDefinition;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

public final class LetArgumentNodeGang extends LetArgumentNode {
  private final int column;
  private GangDefinition definition;
  private final String gangName;
  private final int line;
  // This is a list of input variable to output variable
  private List<Pair<Target, DefinedTarget>> targets;

  public LetArgumentNodeGang(int line, int column, String gangName) {
    this.gangName = gangName;
    this.line = line;
    this.column = column;
  }

  @Override
  public boolean blankCheck(Consumer<String> errorHandler) {
    return true;
  }

  @Override
  public boolean checkUnusedDeclarations(Consumer<String> errorHandler) {
    for (final String name : unusedNames) {
      errorHandler.accept(
          String.format(
              "%d:%d: Gang “@%s” contains “%s”, but this is never used.",
              line, column, gangName, name));
    }
    return unusedNames.isEmpty();
  }

  @Override
  public WildcardCheck checkWildcard(Consumer<String> errorHandler) {
    return WildcardCheck.NONE;
  }

  @Override
  public void collectFreeVariables(Set<String> names, Predicate<Target.Flavour> predicate) {
    for (final Pair<Target, DefinedTarget> target : targets) {
      if (predicate.test(target.first().flavour())) {
        names.add(target.first().name());
      }
    }
  }

  @Override
  public void collectPlugins(Set<Path> pluginFileNames) {
    // Do nothing
  }

  @Override
  public Optional<Target> handleUndefinedVariable(String name) {
    return Optional.empty();
  }

  @Override
  public boolean filters() {
    return false;
  }

  @Override
  public void render(LetBuilder let) {
    for (final Pair<Target, DefinedTarget> target : targets) {
      let.add(
          target.first().type().apply(TO_ASM),
          target.first().name(),
          r -> r.loadTarget(target.first()));
    }
  }

  private final Set<String> unusedNames = new TreeSet<>();

  @Override
  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    final Optional<List<Pair<Target, DefinedTarget>>> results =
        TypeUtils.matchGang(
            line,
            column,
            defs,
            definition,
            (inputTarget, expectedType, dropIfDefault) -> {
              unusedNames.add(inputTarget.name());
              return new Pair<>(
                  inputTarget,
                  new DefinedTarget() {
                    @Override
                    public int column() {
                      return column;
                    }

                    @Override
                    public int line() {
                      return line;
                    }

                    @Override
                    public Flavour flavour() {
                      return Flavour.STREAM;
                    }

                    @Override
                    public String name() {
                      return inputTarget.name();
                    }

                    @Override
                    public void read() {
                      unusedNames.remove(inputTarget.name());
                    }

                    @Override
                    public Imyhat type() {
                      return expectedType;
                    }
                  });
            },
            errorHandler);
    results.ifPresent(l -> targets = l);
    return results.isPresent();
  }

  @Override
  public boolean resolveFunctions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    final Optional<? extends GangDefinition> gang =
        expressionCompilerServices
            .inputFormat()
            .gangs()
            .filter(g -> g.name().equals(gangName))
            .findAny();
    gang.ifPresent(g -> definition = g);
    if (!gang.isPresent()) {
      errorHandler.accept(String.format("%d:%d: Unknown gang “%s”.", line, column, gangName));
    }
    return gang.isPresent();
  }

  @Override
  public Stream<DefinedTarget> targets() {
    return targets.stream().map(Pair::second);
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    return targets
            .stream()
            .filter(
                p -> {
                  if (p.first().type().isSame(p.second().type())) {
                    return true;
                  }
                  errorHandler.accept(
                      String.format(
                          "%d:%d: Gang variable %s should have type %s but got %s.",
                          line,
                          column,
                          p.first().name(),
                          p.second().type().name(),
                          p.first().type().name()));
                  return false;
                })
            .count()
        == targets.size();
  }
}
