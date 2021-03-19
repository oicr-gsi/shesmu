package ca.on.oicr.gsi.shesmu.server;

import ca.on.oicr.gsi.shesmu.compiler.CallableDefinition;
import ca.on.oicr.gsi.shesmu.compiler.RefillerDefinition;
import ca.on.oicr.gsi.shesmu.compiler.RefillerParameterDefinition;
import ca.on.oicr.gsi.shesmu.compiler.Target;
import ca.on.oicr.gsi.shesmu.compiler.definitions.ActionDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.ActionParameterDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.ConstantDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.DefinitionRepository;
import ca.on.oicr.gsi.shesmu.compiler.definitions.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.plugin.functions.FunctionParameter;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/** An import reference that can check if it is still valid */
public interface ImportVerifier {

  class ActionVerifier implements ImportVerifier {
    private final String name;
    private final Map<String, Imyhat> parameters;

    public ActionVerifier(ActionDefinition definition) {
      name = definition.name();
      parameters =
          definition
              .parameters()
              .collect(
                  Collectors.toMap(
                      ActionParameterDefinition::name, ActionParameterDefinition::type));
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      var that = (ActionVerifier) o;
      return name.equals(that.name) && parameters.equals(that.parameters);
    }

    @Override
    public int hashCode() {
      return Objects.hash(name, parameters);
    }

    @Override
    public boolean stillMatches(DefinitionRepository repository) {
      return repository
          .actions()
          .anyMatch(
              a ->
                  a.name().equals(name)
                      && a.parameters()
                          .collect(
                              Collectors.toMap(
                                  ActionParameterDefinition::name, ActionParameterDefinition::type))
                          .entrySet()
                          .containsAll(parameters.entrySet()));
    }
  }

  class ConstantVerifier implements ImportVerifier {

    private final String name;
    private final Imyhat type;

    public ConstantVerifier(ConstantDefinition definition) {
      name = definition.name();
      type = definition.type();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      var that = (ConstantVerifier) o;
      return name.equals(that.name) && type.equals(that.type);
    }

    @Override
    public int hashCode() {
      return Objects.hash(name, type);
    }

    @Override
    public boolean stillMatches(DefinitionRepository repository) {
      return repository.constants().anyMatch(c -> c.name().equals(name) && c.type().isSame(type));
    }
  }

  class FunctionVerifier implements ImportVerifier {

    private final String name;
    private final List<Imyhat> parameters;
    private final Imyhat returnType;

    public FunctionVerifier(FunctionDefinition definition) {
      name = definition.name();
      returnType = definition.returnType();
      parameters =
          definition.parameters().map(FunctionParameter::type).collect(Collectors.toList());
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      var that = (FunctionVerifier) o;
      return name.equals(that.name)
          && returnType.equals(that.returnType)
          && parameters.equals(that.parameters);
    }

    @Override
    public int hashCode() {
      return Objects.hash(name, returnType, parameters);
    }

    @Override
    public boolean stillMatches(DefinitionRepository repository) {
      return repository
          .functions()
          .anyMatch(
              f ->
                  f.name().equals(name)
                      && f.returnType().isSame(returnType)
                      && f.parameters()
                          .map(FunctionParameter::type)
                          .collect(Collectors.toList())
                          .equals(parameters));
    }
  }

  class OliveDefinitionVerifier implements ImportVerifier {

    private final boolean isRoot;
    private final String name;
    private final Map<String, Imyhat> output;
    private final List<Imyhat> parameters = new ArrayList<>();

    public OliveDefinitionVerifier(CallableDefinition definition) {
      name = definition.name();
      isRoot = definition.isRoot();
      for (var i = 0; i < definition.parameterCount(); i++) {
        parameters.add(definition.parameterType(i));
      }
      output =
          definition
              .outputStreamVariables(null, null)
              .get()
              .collect(Collectors.toMap(Target::name, Target::type));
    }

    private boolean checkOutput(Stream<Target> targets) {
      final Set<String> names = new TreeSet<>();
      return targets
              .peek(t -> names.add(t.name()))
              .allMatch(t -> output.getOrDefault(t.name(), Imyhat.BAD).isSame(t.type()))
          && names.equals(output.keySet());
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      var that = (OliveDefinitionVerifier) o;
      return isRoot == that.isRoot
          && name.equals(that.name)
          && parameters.equals(that.parameters)
          && output.equals(that.output);
    }

    @Override
    public int hashCode() {
      return Objects.hash(name, isRoot, parameters, output);
    }

    @Override
    public boolean stillMatches(DefinitionRepository repository) {
      return repository
          .oliveDefinitions()
          .anyMatch(
              d ->
                  d.name().equals(name)
                      && d.isRoot() == isRoot
                      && d.parameterCount() == parameters.size()
                      && IntStream.range(0, parameters.size())
                          .allMatch(i -> d.parameterType(i).isSame(parameters.get(i)))
                      && checkOutput(d.outputStreamVariables(null, null).get()));
    }
  }

  class RefillerVerifier implements ImportVerifier {
    private final String name;
    private final Map<String, Imyhat> parameters;

    public RefillerVerifier(RefillerDefinition definition) {
      name = definition.name();
      parameters =
          definition
              .parameters()
              .collect(
                  Collectors.toMap(
                      RefillerParameterDefinition::name, RefillerParameterDefinition::type));
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      var that = (RefillerVerifier) o;
      return name.equals(that.name) && parameters.equals(that.parameters);
    }

    @Override
    public int hashCode() {
      return Objects.hash(name, parameters);
    }

    @Override
    public boolean stillMatches(DefinitionRepository repository) {
      return repository
          .refillers()
          .anyMatch(
              a ->
                  a.name().equals(name)
                      && a.parameters()
                          .collect(
                              Collectors.toMap(
                                  RefillerParameterDefinition::name,
                                  RefillerParameterDefinition::type))
                          .entrySet()
                          .containsAll(parameters.entrySet()));
    }
  }

  /**
   * Check if an import is still available in the definition repository provided
   *
   * @param repository the repository to check
   * @return true if the repository contains a compatible definition
   */
  boolean stillMatches(DefinitionRepository repository);
}
