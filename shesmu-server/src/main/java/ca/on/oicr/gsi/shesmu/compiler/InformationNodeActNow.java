package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.definitions.ActionDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.ActionParameterDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.DefinitionRepository;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class InformationNodeActNow extends InformationNodeBaseRepeat {
  private final int line;
  private final int column;
  private final List<OliveArgumentNode> arguments;
  private final List<VariableTagNode> variableTags;
  private ActionDefinition definition;
  private final ExpressionNode fileName;
  private final String actionName;

  public InformationNodeActNow(
      int line,
      int column,
      DestructuredArgumentNode name,
      SourceNode source,
      List<ListNode> transforms,
      String actionName,
      List<VariableTagNode> variableTags,
      List<OliveArgumentNode> arguments,
      ExpressionNode fileName) {
    super(name, source, transforms);
    this.line = line;
    this.column = column;
    this.arguments = arguments;
    this.variableTags = variableTags;
    this.fileName = fileName;
    this.actionName = actionName;
  }

  @Override
  protected String renderBlock(EcmaScriptRenderer renderer, String data) {
    return String.format(
        "{type: \"download\", isJson: true, mimetype: \"application/json\", contents: %s, file: (%s) + \".actnow\"}",
        data, fileName.renderEcma(renderer));
  }

  @Override
  public String renderRow(EcmaScriptRenderer renderer) {
    try {
      return String.format(
          "{name: %s, parameters: {%s}, tags: [%s]}",
          RuntimeSupport.MAPPER.writeValueAsString(definition.name()),
          arguments.stream()
              .map(argument -> argument.renderEcma(renderer))
              .collect(Collectors.joining(", ")),
          variableTags.stream()
              .map(tag -> tag.renderEcma(renderer))
              .collect(Collectors.joining(", ")));
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  protected boolean resolveTerminal(
      NameDefinitions parentName, NameDefinitions collectorName, Consumer<String> errorHandler) {
    var ok =
        arguments.stream()
                .filter(collector -> collector.resolve(collectorName, errorHandler))
                .count()
            == arguments.size();
    if (ok) {

      final var argumentNames =
          arguments.stream()
              .flatMap(OliveArgumentNode::targets)
              .collect(Collectors.groupingBy(Target::name, Collectors.counting()));
      for (final var entry : argumentNames.entrySet()) {
        if (entry.getValue() > 1) {
          errorHandler.accept(
              String.format(
                  "%d:%d: Duplicate argument “%s” to action.", line, column, entry.getKey()));
          ok = false;
        }
      }

      final var definedArgumentNames =
          definition.parameters().map(ActionParameterDefinition::name).collect(Collectors.toSet());
      final var requiredArgumentNames =
          definition
              .parameters()
              .filter(ActionParameterDefinition::required)
              .map(ActionParameterDefinition::name)
              .collect(Collectors.toSet());
      if (!definedArgumentNames.containsAll(argumentNames.keySet())) {
        ok = false;
        final Set<String> badTerms = new HashSet<>(argumentNames.keySet());
        badTerms.removeAll(definedArgumentNames);
        errorHandler.accept(
            String.format(
                "%d:%d: Extra arguments for action %s: %s",
                line, column, actionName, String.join(", ", badTerms)));
      }
      switch (arguments.stream()
          .map(argument -> argument.checkWildcard(errorHandler))
          .reduce(WildcardCheck.NONE, WildcardCheck::combine)) {
        case NONE:
          if (!argumentNames.keySet().containsAll(requiredArgumentNames)) {
            ok = false;
            final Set<String> badTerms = new HashSet<>(requiredArgumentNames);
            badTerms.removeAll(argumentNames.keySet());
            errorHandler.accept(
                String.format(
                    "%d:%d: Missing arguments for action %s: %s",
                    line, column, actionName, String.join(", ", badTerms)));
          }
          break;
        case HAS_WILDCARD:
          final var provider =
              arguments.stream()
                  .map(x -> (UndefinedVariableProvider) x)
                  .reduce(UndefinedVariableProvider.NONE, UndefinedVariableProvider::combine);
          for (final var requiredName : requiredArgumentNames) {
            if (!definedArgumentNames.contains(requiredName)) {
              provider.handleUndefinedVariable(requiredName);
            }
          }
          break;
        case BAD:
          ok = false;
          break;
      }
    }
    return ok
        & variableTags.stream().filter(tag -> tag.resolve(collectorName, errorHandler)).count()
            == variableTags.size()
        & fileName.resolve(parentName, errorHandler);
  }

  @Override
  protected boolean resolveTerminalDefinitions(
      ExpressionCompilerServices expressionCompilerServices,
      DefinitionRepository nativeDefinitions,
      Consumer<String> errorHandler) {
    definition = expressionCompilerServices.action(actionName);
    if (definition == null) {
      errorHandler.accept(
          String.format("%d:%d: Unknown action for “%s”.", line, column, actionName));
    }
    return definition != null
        & arguments.stream()
                .filter(arg -> arg.resolveFunctions(expressionCompilerServices, errorHandler))
                .count()
            == arguments.size()
        & variableTags.stream()
                .filter(tag -> tag.resolveDefinitions(expressionCompilerServices, errorHandler))
                .count()
            == variableTags.size()
        & fileName.resolveDefinitions(expressionCompilerServices, errorHandler);
  }

  @Override
  protected boolean typeCheckTerminal(Consumer<String> errorHandler) {
    var ok = fileName.typeCheck(errorHandler);
    if (ok && !fileName.type().isSame(Imyhat.STRING)) {
      fileName.typeError(Imyhat.STRING, fileName.type(), errorHandler);
      ok = false;
    }
    for (final var tag : variableTags) {
      if (!tag.typeCheck(errorHandler)) {
        ok = false;
      }
    }
    if (arguments.stream()
            .filter(
                argument -> argument.typeCheck(errorHandler) && argument.checkName(errorHandler))
            .count()
        == arguments.size()) {
      final var parameterInfo =
          definition
              .parameters()
              .collect(Collectors.toMap(ActionParameterDefinition::name, Function.identity()));
      return arguments.stream()
                  .filter(argument -> argument.checkArguments(parameterInfo::get, errorHandler))
                  .count()
              == arguments.size()
          & ok;
    } else {
      return false;
    }
  }
}
