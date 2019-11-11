package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.definitions.GangDefinition;
import ca.on.oicr.gsi.shesmu.compiler.description.VariableInformation;
import ca.on.oicr.gsi.shesmu.compiler.description.VariableInformation.Behaviour;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class DiscriminatorNodeGang extends DiscriminatorNode {

  private class GroupElement implements DefinedTarget, Consumer<Renderer> {
    private final Target source;
    private final Imyhat expectedType;

    private GroupElement(Target source, Imyhat expectedType, boolean dropIfDefault) {
      this.source = source;
      this.expectedType = expectedType;
    }

    @Override
    public void accept(Renderer renderer) {
      renderer.loadTarget(source);
    }

    @Override
    public int column() {
      return column;
    }

    @Override
    public Flavour flavour() {
      return Flavour.STREAM;
    }

    @Override
    public int line() {
      return line;
    }

    @Override
    public String name() {
      return source.name();
    }

    public boolean typeCheck(Consumer<String> errorHandler) {
      if (expectedType.isSame(source.type())) {
        return true;
      }
      errorHandler.accept(
          String.format(
              "%d:%d: Variable %s in gang should have type %s but got %s.",
              line, column, source.name(), expectedType.name(), source.type()));
      return false;
    }

    @Override
    public Imyhat type() {
      return source.type();
    }
  }

  private final int column;
  private GangDefinition definition;
  private final String gangName;
  private final int line;
  private List<GroupElement> outputTargets;

  public DiscriminatorNodeGang(int line, int column, String gangName) {
    this.line = line;
    this.column = column;
    this.gangName = gangName;
  }

  @Override
  public void collectFreeVariables(Set<String> names, Predicate<Target.Flavour> predicate) {
    for (final GroupElement element : outputTargets) {
      if (predicate.test(element.source.flavour())) {
        names.add(element.source.name());
      }
    }
  }

  @Override
  public void collectPlugins(Set<Path> pluginFileNames) {
    // Do nothing
  }

  @Override
  public Stream<VariableInformation> dashboard() {
    return outputTargets
        .stream()
        .map(
            target ->
                new VariableInformation(
                    target.name(),
                    target.type(),
                    Stream.of(target.name()),
                    Behaviour.DEFINITION_BY));
  }

  @Override
  public void render(RegroupVariablesBuilder builder) {
    for (GroupElement element : outputTargets) {
      builder.addKey(element.type().apply(TypeUtils.TO_ASM), element.name(), element);
    }
  }

  @Override
  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    final Optional<List<GroupElement>> result =
        TypeUtils.matchGang(line, column, defs, definition, GroupElement::new, errorHandler);
    result.ifPresent(x -> outputTargets = x);
    return result.isPresent();
  }

  @Override
  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    final Optional<? extends GangDefinition> gang =
        expressionCompilerServices
            .inputFormat()
            .gangs()
            .filter(g -> g.name().equals(gangName))
            .findAny();
    if (gang.isPresent()) {
      definition = gang.get();
      return true;
    }
    errorHandler.accept(
        String.format(
            "%d:%d: Unknown gang “%s” for input format “%s”.",
            line, column, gangName, expressionCompilerServices.inputFormat().name()));
    return false;
  }

  @Override
  public Stream<DefinedTarget> targets() {
    return outputTargets.stream().map(x -> x);
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    return outputTargets.stream().filter(e -> e.typeCheck(errorHandler)).count()
        == outputTargets.size();
  }
}
