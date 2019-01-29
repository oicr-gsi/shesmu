package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.SampleNode.Consumption;
import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.compiler.definitions.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class ListNodeSubsample extends ListNode {

  private Imyhat incoming;
  private String name;
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
  public final String name() {
    return name;
  }

  @Override
  public final String nextName() {
    return name;
  }

  @Override
  public final Imyhat nextType() {
    return incoming;
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
  public final void render(JavaStreamBuilder builder) {
    builder.subsample(
        samplers
            .stream()
            .<JavaStreamBuilder.RenderSubsampler>map(s -> s::render)
            .collect(Collectors.toList()));
  }

  @Override
  public final Optional<String> resolve(
      String name, NameDefinitions defs, Consumer<String> errorHandler) {
    this.name = name;
    final boolean ok =
        samplers.stream().filter(sampler -> sampler.resolve(name, defs, errorHandler)).count()
            == samplers.size();
    return ok ? Optional.of(name) : Optional.empty();
  }

  @Override
  public final boolean resolveFunctions(
      Function<String, FunctionDefinition> definedFunctions, Consumer<String> errorHandler) {
    final boolean ok =
        samplers
                .stream()
                .filter(sampler -> sampler.resolveFunctions(definedFunctions, errorHandler))
                .count()
            == samplers.size();
    return ok;
  }

  @Override
  public final boolean typeCheck(Imyhat incoming, Consumer<String> errorHandler) {
    this.incoming = incoming;
    final boolean ok =
        samplers.stream().filter(sampler -> sampler.typeCheck(incoming, errorHandler)).count()
            == samplers.size();
    Consumption consumption = Consumption.LIMITED;
    for (final SampleNode sampler : samplers) {
      consumption = sampler.consumptionCheck(consumption, errorHandler);
    }
    return ok && consumption != Consumption.BAD;
  }
}
