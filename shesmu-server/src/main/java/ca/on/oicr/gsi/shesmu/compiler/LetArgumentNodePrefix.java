package ca.on.oicr.gsi.shesmu.compiler;

import static ca.on.oicr.gsi.shesmu.compiler.TypeUtils.TO_ASM;

import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class LetArgumentNodePrefix extends LetArgumentNode {
  private class PrefixTarget implements DefinedTarget {
    public final Target backing;

    private PrefixTarget(Target backing) {
      this.backing = backing;
    }

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
      return prefix + backing.name();
    }

    @Override
    public void read() {
      unused.remove(backing.name());
    }

    @Override
    public Imyhat type() {
      return backing.type();
    }
  }

  private final int column;
  private final int line;
  private final List<String> names;
  private final String prefix;
  private final List<PrefixTarget> targets = new ArrayList<>();
  private final Set<String> unused = new TreeSet<>();

  public LetArgumentNodePrefix(int line, int column, String prefix, List<String> names) {
    this.line = line;
    this.column = column;
    this.prefix = prefix;
    this.names = names;
  }

  @Override
  public boolean blankCheck(Consumer<String> errorHandler) {
    return true;
  }

  @Override
  public boolean checkUnusedDeclarations(Consumer<String> errorHandler) {
    for (final var variable : unused) {
      errorHandler.accept(
          String.format("%d:%d: Variable %s%s is never used.", line, column, prefix, variable));
    }
    return unused.isEmpty();
  }

  @Override
  public WildcardCheck checkWildcard(Consumer<String> errorHandler) {
    return WildcardCheck.NONE;
  }

  @Override
  public void collectFreeVariables(Set<String> names, Predicate<Target.Flavour> predicate) {
    for (final var target : targets) {
      if (predicate.test(target.backing.flavour())) {
        names.add(target.backing.name());
      }
    }
  }

  @Override
  public void collectPlugins(Set<Path> pluginFileNames) {
    // Do nothing.
  }

  @Override
  public boolean filters() {
    return false;
  }

  @Override
  public Optional<Target> handleUndefinedVariable(String name) {
    return Optional.empty();
  }

  @Override
  public void render(LetBuilder let) {
    for (final var target : targets) {
      let.add(target.type().apply(TO_ASM), target.name(), r -> r.loadTarget(target.backing));
    }
  }

  @Override
  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    var ok = true;
    for (final var name : names) {
      final var target = defs.get(name);
      if (target.isEmpty()) {
        ok = false;
        errorHandler.accept(String.format("%d:%d: Undefined variable “%s”.", line, column, name));
        continue;
      }
      if (!target.get().flavour().isStream()) {
        ok = false;
        errorHandler.accept(
            String.format("%d:%d: Variable “%s” is not from the input data.", line, column, name));
        continue;
      }
      targets.add(new PrefixTarget(target.get()));
      unused.add(name);
      target.get().read();
    }
    return ok;
  }

  @Override
  public boolean resolveFunctions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    return true;
  }

  @Override
  public Stream<DefinedTarget> targets() {
    return targets.stream().map(x -> x);
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    return true;
  }
}
