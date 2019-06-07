package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.compiler.definitions.InputFormatDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.SignatureDefinition;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A collection of all defined variables at any point in a program.
 *
 * <p>Also tracks if the program has resolved all variables so far.
 */
public class NameDefinitions {
  public static class DefaultStreamTarget implements Target {
    private final String name;
    private final boolean signable;
    private final Imyhat type;

    public DefaultStreamTarget(String name, Imyhat type, boolean signable) {
      this.name = name;
      this.type = type;
      this.signable = signable;
    }

    @Override
    public Flavour flavour() {
      return signable ? Flavour.STREAM_SIGNABLE : Flavour.STREAM;
    }

    @Override
    public String name() {
      return name;
    }

    @Override
    public Imyhat type() {
      return type;
    }
  }

  /**
   * Create a new collection of variables from the parameters provided.
   *
   * @param parameters the parameters for this environment
   */
  public static NameDefinitions root(
      InputFormatDefinition inputFormatDefinition,
      Stream<? extends Target> parameters,
      Stream<SignatureDefinition> signatureVariables) {
    return new NameDefinitions(
        Stream.concat(
                parameters.filter(
                    variable ->
                        variable.flavour() == Flavour.PARAMETER
                            || variable.flavour() == Flavour.CONSTANT),
                Stream.concat(inputFormatDefinition.baseStreamVariables(), signatureVariables))
            .collect(Collectors.toMap(Target::name, Function.identity(), (a, b) -> a)),
        true);
  }

  private final boolean isGood;

  private final Map<String, Target> variables;

  public NameDefinitions(Map<String, Target> variables, boolean isGood) {
    this.variables = variables;
    this.isGood = isGood;
  }

  /**
   * Bind a lambda parameter to the known names
   *
   * <p>This will eclipse any existing definition
   *
   * @param parameter the parameter to bind
   */
  public NameDefinitions bind(Target parameter) {
    return new NameDefinitions(
        Stream.concat(
                Stream.of(parameter),
                variables
                    .values()
                    .stream()
                    .filter(variable -> !variable.name().equals(parameter.name())))
            .collect(Collectors.toMap(Target::name, Function.identity(), (a, b) -> a)),
        isGood);
  }

  /**
   * Bind a lambda parameter to the known names
   *
   * <p>This will eclipse any existing definition
   *
   * @param parameters the parameters to bind
   */
  public NameDefinitions bind(List<Target> parameters) {
    final Set<String> shadowedNames =
        parameters.stream().map(Target::name).collect(Collectors.toSet());
    return new NameDefinitions(
        Stream.concat(
                parameters.stream(),
                variables
                    .values()
                    .stream()
                    .filter(variable -> !shadowedNames.contains(variable.name())))
            .collect(Collectors.toMap(Target::name, Function.identity(), (a, b) -> a)),
        isGood);
  }

  /** Create a new set of defined variables that is identical, but mark it as a failure. */
  public NameDefinitions fail(boolean ok) {
    return new NameDefinitions(variables, ok && isGood);
  }

  /** Get a variable from the collection. */
  public Optional<Target> get(String name) {
    return Optional.ofNullable(variables.get(name));
  }

  /** Determine if any failures have occurred so far. */
  public boolean isGood() {
    return isGood;
  }

  /**
   * Create a new set of defined names, replacing the stream variables with the ones provided
   *
   * @param newStreamVariables the new stream variables to be inserted
   * @param good whether a failure has occurred
   */
  public NameDefinitions replaceStream(Stream<Target> newStreamVariables, boolean good) {
    return new NameDefinitions(
        Stream.concat(
                newStreamVariables,
                variables.values().stream().filter(variable -> !variable.flavour().isStream()))
            .collect(Collectors.toMap(Target::name, Function.identity(), (a, b) -> a)),
        isGood && good);
  }

  public Stream<Target> stream() {
    return variables.values().stream();
  }
}
