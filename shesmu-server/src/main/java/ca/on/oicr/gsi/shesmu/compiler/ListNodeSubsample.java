package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.SampleNode.Consumption;
import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class ListNodeSubsample extends ListNode {

  private final List<SampleNode> samplers;

  public ListNodeSubsample(int line, int column, List<SampleNode> samplers) {
    super(line, column);
    this.samplers = samplers;
  }

  @Override
  public final void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate) {
    samplers.forEach(sampler -> sampler.collectFreeVariables(names, predicate));
  }

  @Override
  public void collectPlugins(Set<Path> pluginFileNames) {
    samplers.forEach(sampler -> sampler.collectPlugins(pluginFileNames));
  }

  @Override
  public final Ordering order(Ordering previous, Consumer<String> errorHandler) {
    if (previous == Ordering.RANDOM) {
      errorHandler.accept(
          String.format(
              "%d:%d: Cannot apply subsampling to an unordered stream.", line(), column()));
      return Ordering.BAD;
    }
    return previous;
  }

  @Override
  public final LoadableConstructor render(JavaStreamBuilder builder, LoadableConstructor name) {
    builder.subsample(samplers, name);
    return name;
  }

  @Override
  public EcmaLoadableConstructor render(EcmaStreamBuilder builder, EcmaLoadableConstructor name) {
    builder.subsample(samplers, name);
    return name;
  }

  @Override
  public final Optional<DestructuredArgumentNode> resolve(
      DestructuredArgumentNode name, NameDefinitions defs, Consumer<String> errorHandler) {
    final boolean ok =
        samplers.stream().filter(sampler -> sampler.resolve(name, defs, errorHandler)).count()
            == samplers.size();
    return ok ? Optional.of(name) : Optional.empty();
  }

  @Override
  public final boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    return samplers
            .stream()
            .filter(sampler -> sampler.resolveDefinitions(expressionCompilerServices, errorHandler))
            .count()
        == samplers.size();
  }

  @Override
  public final Optional<Imyhat> typeCheck(Imyhat incoming, Consumer<String> errorHandler) {
    final boolean ok =
        samplers.stream().filter(sampler -> sampler.typeCheck(incoming, errorHandler)).count()
            == samplers.size();
    Consumption consumption = Consumption.LIMITED;
    for (final SampleNode sampler : samplers) {
      consumption = sampler.consumptionCheck(consumption, errorHandler);
    }
    return ok && consumption != Consumption.BAD ? Optional.of(incoming) : Optional.empty();
  }
}
