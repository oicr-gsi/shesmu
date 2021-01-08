package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.compiler.definitions.ActionDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.ActionParameterDefinition;
import ca.on.oicr.gsi.shesmu.compiler.description.OliveTable;
import ca.on.oicr.gsi.shesmu.compiler.description.Produces;
import ca.on.oicr.gsi.shesmu.compiler.description.VariableInformation;
import ca.on.oicr.gsi.shesmu.compiler.description.VariableInformation.Behaviour;
import ca.on.oicr.gsi.shesmu.plugin.action.Action;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

public final class OliveNodeRun extends OliveNodeWithClauses {

  private static final Type A_ACTION_TYPE = Type.getType(Action.class);
  private static final Method METHOD_ACTION__PREPARE =
      new Method("prepare", Type.VOID_TYPE, new Type[] {});
  private final String actionName;
  private final List<OliveArgumentNode> arguments;
  private final int column;
  private ActionDefinition definition;
  private final String description;
  private final int line;
  private final Set<String> tags;
  private final List<VariableTagNode> variableTags;

  public OliveNodeRun(
      int line,
      int column,
      String actionName,
      List<OliveArgumentNode> arguments,
      List<OliveClauseNode> clauses,
      Set<String> tags,
      String description,
      List<VariableTagNode> variableTags) {
    super(clauses);
    this.line = line;
    this.column = column;
    this.actionName = actionName;
    this.arguments = arguments;
    this.tags = tags;
    this.description = description;
    this.variableTags = variableTags;
  }

  @Override
  public void build(RootBuilder builder, Map<String, CallableDefinitionRenderer> definitions) {
    // Do nothing.
  }

  @Override
  public boolean checkUnusedDeclarationsExtra(Consumer<String> errorHandler) {
    return true;
  }

  @Override
  protected void collectArgumentSignableVariables() {
    arguments.forEach(
        arg -> arg.collectFreeVariables(signableNames, Flavour.STREAM_SIGNABLE::equals));
  }

  @Override
  public boolean collectDefinitions(
      Map<String, CallableDefinition> definedOlives,
      Map<String, Target> definedConstants,
      Consumer<String> errorHandler) {
    return true;
  }

  @Override
  public void collectPluginsExtra(Set<Path> pluginFileNames) {
    arguments.forEach(arg -> arg.collectPlugins(pluginFileNames));
    variableTags.forEach(tag -> tag.collectPlugins(pluginFileNames));
  }

  @Override
  public Stream<OliveTable> dashboard() {
    return Stream.of(
        new OliveTable(
            "Run " + actionName,
            line,
            column,
            Produces.ACTIONS,
            tags,
            description,
            definition.supplementaryInformation(),
            clauses().stream().flatMap(OliveClauseNode::dashboard),
            arguments
                .stream()
                .flatMap(
                    arg -> {
                      final Set<String> inputs = new HashSet<>();
                      arg.collectFreeVariables(inputs, Flavour::isStream);
                      return arg.targets()
                          .map(
                              t ->
                                  new VariableInformation(
                                      t.name(), t.type(), inputs.stream(), Behaviour.DEFINITION));
                    })));
  }

  @Override
  public void processExport(ExportConsumer exportConsumer) {
    // Not exportable
  }

  private static final Type A_STRING_TYPE = Type.getType(String.class);

  @Override
  public void render(
      RootBuilder builder, Function<String, CallableDefinitionRenderer> definitions) {
    final Set<String> captures = new HashSet<>();
    arguments.forEach(arg -> arg.collectFreeVariables(captures, Flavour::needsCapture));
    variableTags.forEach(arg -> arg.collectFreeVariables(captures, Flavour::needsCapture));
    final OliveBuilder oliveBuilder =
        builder.buildRunOlive(
            line, column, definition.name(), signableNames, signableVariableChecks);
    clauses().forEach(clause -> clause.render(builder, oliveBuilder, definitions));
    oliveBuilder.line(line);
    final Renderer action =
        oliveBuilder.finish(
            "Run " + actionName,
            oliveBuilder.loadableValues().filter(v -> captures.contains(v.name())));
    action.methodGen().visitCode();
    action.methodGen().visitLineNumber(line, action.methodGen().mark());
    definition.initialize(action.methodGen());
    final int local = action.methodGen().newLocal(A_ACTION_TYPE);
    action.methodGen().storeLocal(local);

    arguments.forEach(
        parameter -> {
          parameter.render(action, local);
        });
    action.methodGen().visitLineNumber(line, action.methodGen().mark());
    action.methodGen().loadLocal(local);
    action.methodGen().invokeVirtual(A_ACTION_TYPE, METHOD_ACTION__PREPARE);
    action
        .methodGen()
        .push(variableTags.stream().mapToInt(VariableTagNode::staticSize).sum() + tags.size());
    final List<IntConsumer> dynamicTagLocal = new ArrayList<>();
    for (final VariableTagNode tag : variableTags) {
      tag.renderDynamicSize(action).ifPresent(dynamicTagLocal::add);
    }
    action.methodGen().newArray(A_STRING_TYPE);
    int tagIndex = 0;
    for (final String tag : tags) {
      action.methodGen().dup();
      action.methodGen().push(tagIndex++);
      action.methodGen().push(tag);
      action.methodGen().arrayStore(A_STRING_TYPE);
    }
    for (final VariableTagNode tag : variableTags) {
      tagIndex += tag.renderStaticTag(action, tagIndex);
    }
    final int dynamicTagIndex = action.methodGen().newLocal(Type.INT_TYPE);
    action.methodGen().push(tagIndex);
    action.methodGen().storeLocal(dynamicTagIndex);
    for (final IntConsumer consumer : dynamicTagLocal) {
      consumer.accept(dynamicTagIndex);
    }
    int tagArray = action.methodGen().newLocal(A_STRING_TYPE);
    action.methodGen().storeLocal(tagArray);
    oliveBuilder.emitAction(action.methodGen(), local, tagArray);
    action.methodGen().visitInsn(Opcodes.RETURN);
    action.methodGen().visitMaxs(0, 0);
    action.methodGen().visitEnd();
  }

  @Override
  public boolean resolve(
      OliveCompilerServices oliveCompilerServices, Consumer<String> errorHandler) {
    final NameDefinitions defs =
        clauses()
            .stream()
            .reduce(
                NameDefinitions.root(
                    oliveCompilerServices.inputFormat(),
                    oliveCompilerServices.constants(true),
                    oliveCompilerServices.signatures()),
                (d, clause) -> clause.resolve(oliveCompilerServices, d, errorHandler),
                (a, b) -> {
                  throw new UnsupportedOperationException();
                });
    boolean ok =
        defs.isGood()
            & arguments.stream().filter(argument -> argument.resolve(defs, errorHandler)).count()
                == arguments.size()
            & variableTags.stream().filter(tag -> tag.resolve(defs, errorHandler)).count()
                == variableTags.size();

    final Map<String, Long> argumentNames =
        arguments
            .stream()
            .flatMap(OliveArgumentNode::targets)
            .collect(Collectors.groupingBy(Target::name, Collectors.counting()));
    for (final Map.Entry<String, Long> entry : argumentNames.entrySet()) {
      if (entry.getValue() > 1) {
        errorHandler.accept(
            String.format(
                "%d:%d: Duplicate argument “%s” to action.", line, column, entry.getKey()));
        ok = false;
      }
    }

    final Set<String> definedArgumentNames =
        definition.parameters().map(ActionParameterDefinition::name).collect(Collectors.toSet());
    final Set<String> requiredArgumentNames =
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
    switch (arguments
        .stream()
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
        final UndefinedVariableProvider provider =
            arguments
                .stream()
                .map(x -> (UndefinedVariableProvider) x)
                .reduce(UndefinedVariableProvider.NONE, UndefinedVariableProvider::combine);
        for (final String requiredName : requiredArgumentNames) {
          if (!definedArgumentNames.contains(requiredName)) {
            provider.handleUndefinedVariable(requiredName);
          }
        }
        break;
      case BAD:
        ok = false;
        break;
    }
    return ok;
  }

  @Override
  protected boolean resolveDefinitionsExtra(
      OliveCompilerServices oliveCompilerServices, Consumer<String> errorHandler) {
    definition = oliveCompilerServices.action(actionName);
    if (definition == null) {
      errorHandler.accept(
          String.format("%d:%d: Unknown action for “%s”.", line, column, actionName));
    }
    return definition != null
        & arguments
                .stream()
                .filter(arg -> arg.resolveFunctions(oliveCompilerServices, errorHandler))
                .count()
            == arguments.size()
        & variableTags
                .stream()
                .filter(tag -> tag.resolveDefinitions(oliveCompilerServices, errorHandler))
                .count()
            == variableTags.size();
  }

  @Override
  protected void setPurity(ClauseStreamOrder state) {
    // Do nothing.
  }

  @Override
  public boolean skipCheckUnusedDeclarations() {
    return false;
  }

  @Override
  public boolean resolveTypes(
      OliveCompilerServices oliveCompilerServices, Consumer<String> errorHandler) {
    return true;
  }

  @Override
  protected boolean typeCheckExtra(Consumer<String> errorHandler) {
    boolean ok =
        arguments
                .stream()
                .filter(
                    argument ->
                        argument.typeCheck(errorHandler) && argument.checkName(errorHandler))
                .count()
            == arguments.size();
    if (ok) {
      final Map<String, ActionParameterDefinition> parameterInfo =
          definition
              .parameters()
              .collect(Collectors.toMap(ActionParameterDefinition::name, Function.identity()));
      ok =
          arguments
                  .stream()
                  .filter(argument -> argument.checkArguments(parameterInfo::get, errorHandler))
                  .count()
              == arguments.size();
    }
    for (final VariableTagNode tag : variableTags) {
      if (!tag.typeCheck(errorHandler)) {
        ok = false;
      }
    }
    return ok;
  }
}
