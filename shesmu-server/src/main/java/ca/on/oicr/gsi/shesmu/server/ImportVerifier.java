package ca.on.oicr.gsi.shesmu.server;

import ca.on.oicr.gsi.shesmu.compiler.RefillerDefinition;
import ca.on.oicr.gsi.shesmu.compiler.RefillerParameterDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.ActionDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.ActionParameterDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.ConstantDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.DefinitionRepository;
import ca.on.oicr.gsi.shesmu.compiler.definitions.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.plugin.functions.FunctionParameter;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

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
      ActionVerifier that = (ActionVerifier) o;
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
      ConstantVerifier that = (ConstantVerifier) o;
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
      FunctionVerifier that = (FunctionVerifier) o;
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
      RefillerVerifier that = (RefillerVerifier) o;
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
