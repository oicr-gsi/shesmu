package ca.on.oicr.gsi.shesmu.compiler;

import static ca.on.oicr.gsi.shesmu.compiler.TypeUtils.TO_ASM;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.compiler.OliveNode.ClauseStreamOrder;
import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.compiler.description.OliveClauseRow;
import ca.on.oicr.gsi.shesmu.compiler.description.VariableInformation;
import ca.on.oicr.gsi.shesmu.compiler.description.VariableInformation.Behaviour;
import ca.on.oicr.gsi.shesmu.plugin.grouper.GrouperDefinition;
import ca.on.oicr.gsi.shesmu.plugin.grouper.GrouperKind;
import ca.on.oicr.gsi.shesmu.plugin.grouper.GrouperOutput;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.server.plugins.JarHashRepository;
import java.nio.file.Path;
import java.util.*;
import java.util.function.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

public final class OliveClauseNodeGroupWithGrouper extends OliveClauseNode {

  public static Stream<GrouperDefinition> definitions() {
    return GROUPERS.values().stream();
  }

  private static final Type A_BICONSUMER_TYPE = Type.getType(BiConsumer.class);
  private static final Type A_FUNCTION_TYPE = Type.getType(Function.class);
  private static final Map<String, GrouperDefinition> GROUPERS = new HashMap<>();

  public static final JarHashRepository<GrouperDefinition> GROUPER_HASHES =
      new JarHashRepository<>();

  static {
    for (var definition : ServiceLoader.load(GrouperDefinition.class)) {
      GROUPER_HASHES.add(definition);
      GROUPERS.put(definition.name(), definition);
    }
  }

  private final List<GroupNode> children;
  protected final int column;
  private final List<DiscriminatorNode> discriminators;
  private final GrouperDefinition grouper;
  private final String grouperName;
  private final List<ExpressionNode> inputExpressions = new ArrayList<>();
  private final Optional<String> label;
  protected final int line;
  private List<String> outputNames;
  private final List<Pair<String, ExpressionNode>> rawInputExpressions;
  private final List<RejectNode> rejectHandlers;
  private final Map<String, Imyhat> typeVariables = new HashMap<>();
  private final Optional<ExpressionNode> where;

  public OliveClauseNodeGroupWithGrouper(
      Optional<String> label,
      int line,
      int column,
      String grouperName,
      List<Pair<String, ExpressionNode>> inputExpressions,
      List<String> outputNames,
      List<GroupNode> children,
      List<DiscriminatorNode> discriminators,
      Optional<ExpressionNode> where,
      List<RejectNode> rejectHandlers) {
    this.label = label;
    this.line = line;
    this.column = column;
    this.grouperName = grouperName;
    this.outputNames = outputNames;
    this.grouper = GROUPERS.get(grouperName);
    this.rawInputExpressions = inputExpressions;
    this.children = children;
    this.discriminators = discriminators;
    this.where = where;
    this.rejectHandlers = rejectHandlers;
  }

  @Override
  public boolean checkUnusedDeclarations(Consumer<String> errorHandler) {
    var ok = true;
    for (final var child : children) {
      if (!child.isRead()) {
        ok = false;
        errorHandler.accept(
            String.format(
                "%d:%d: Collected result “%s” is never used.",
                child.line(), child.column(), child.name()));
      }
    }
    return ok;
  }

  @Override
  public void collectPlugins(Set<Path> pluginFileNames) {
    children.forEach(child -> child.collectPlugins(pluginFileNames));
    discriminators.forEach(discrminator -> discrminator.collectPlugins(pluginFileNames));
    inputExpressions.forEach(expression -> expression.collectPlugins(pluginFileNames));
    where.ifPresent(w -> w.collectPlugins(pluginFileNames));
    rejectHandlers.forEach(r -> r.collectPlugins(pluginFileNames));
  }

  @Override
  public int column() {
    return column;
  }

  @Override
  public Stream<OliveClauseRow> dashboard() {
    final Set<String> whereInputs = new TreeSet<>();
    where.ifPresent(w -> w.collectFreeVariables(whereInputs, Flavour::isStream));
    final List<VariableInformation> inputVariables = new ArrayList<>();
    for (var i = 0; i < inputExpressions.size(); i++) {
      final Set<String> inputs = new TreeSet<>();
      inputExpressions.get(i).collectFreeVariables(inputs, Flavour::isStream);
      inputVariables.add(
          new VariableInformation(
              grouper.input(i).name(),
              grouper.input(i).type().render(typeVariables),
              inputs.stream(),
              Behaviour.EPHEMERAL));
    }
    return Stream.of(
        new OliveClauseRow(
            label.orElse("Group Using " + grouperName),
            line,
            column,
            true,
            true,
            Stream.concat(
                Stream.concat(
                    children.stream()
                        .map(
                            child -> {
                              final Set<String> inputs = new TreeSet<>(whereInputs);
                              child.collectFreeVariables(inputs, Flavour::isStream);
                              return new VariableInformation(
                                  child.name(),
                                  child.type(),
                                  inputs.stream(),
                                  Behaviour.DEFINITION);
                            }),
                    discriminators.stream().flatMap(DiscriminatorNode::dashboard)),
                inputVariables.stream())));
  }

  @Override
  public final ClauseStreamOrder ensureRoot(
      ClauseStreamOrder state,
      Set<String> signableNames,
      Consumer<SignableVariableCheck> addSignableCheck,
      Consumer<String> errorHandler) {
    if (state == ClauseStreamOrder.PURE || state == ClauseStreamOrder.ALMOST_PURE) {
      discriminators.forEach(
          d -> d.collectFreeVariables(signableNames, Flavour.STREAM_SIGNABLE::equals));
      inputExpressions.forEach(
          d -> d.collectFreeVariables(signableNames, Flavour.STREAM_SIGNABLE::equals));
      children.forEach(c -> c.collectFreeVariables(signableNames, Flavour.STREAM_SIGNABLE::equals));
      where.ifPresent(w -> w.collectFreeVariables(signableNames, Flavour.STREAM_SIGNABLE::equals));
      return ClauseStreamOrder.TRANSFORMED;
    }
    return state;
  }

  @Override
  public int line() {
    return line;
  }

  private Pair<String, Type> pairForOutput(int i) {
    return new Pair<>(
        outputNames.get(i),
        grouper.output(i).kind() == GrouperKind.ROW_VALUE
            ? grouper.output(i).type().render(typeVariables).apply(TO_ASM)
            : null);
  }

  @Override
  public void render(
      RootBuilder builder,
      BaseOliveBuilder oliveBuilder,
      Function<String, CallableDefinitionRenderer> definitions) {
    final Set<String> freeVariables = new HashSet<>();
    children.forEach(group -> group.collectFreeVariables(freeVariables, Flavour::needsCapture));
    discriminators.forEach(
        group -> group.collectFreeVariables(freeVariables, Flavour::needsCapture));
    var grouperCaptures = new LoadableValue[inputExpressions.size()];
    for (var i = 0; i < grouperCaptures.length; i++) {
      final var local = i;
      switch (grouper.input(i).kind()) {
        case STATIC:
          grouperCaptures[i] =
              new LoadableValue() {
                private final Type asmType =
                    grouper.input(local).type().render(typeVariables).apply(TO_ASM);

                @Override
                public void accept(Renderer renderer) {
                  inputExpressions.get(local).render(renderer);
                }

                @Override
                public String name() {
                  return "Grouper Parameter " + local;
                }

                @Override
                public Type type() {
                  return asmType;
                }
              };
          break;
        case ROW_VALUE:
          final Set<String> freeVariablesForLambda = new HashSet<>();
          inputExpressions
              .get(i)
              .collectFreeVariables(freeVariablesForLambda, Flavour::needsCapture);
          var lambda =
              new LambdaBuilder(
                  builder,
                  String.format("Grouper Function %d:%d %d", line, column, i),
                  LambdaBuilder.function(
                      inputExpressions.get(i).type(), oliveBuilder.currentType()),
                  oliveBuilder
                      .loadableValues()
                      .filter(value -> freeVariablesForLambda.contains(value.name()))
                      .toArray(LoadableValue[]::new));
          final var lambdaRenderer =
              lambda.renderer(oliveBuilder.currentType(), oliveBuilder::emitSigner);
          lambdaRenderer.methodGen().visitCode();
          inputExpressions.get(i).render(lambdaRenderer);
          lambdaRenderer.methodGen().returnValue();
          lambdaRenderer.methodGen().endMethod();

          grouperCaptures[i] =
              new LoadableValue() {
                @Override
                public void accept(Renderer renderer) {
                  lambda.push(renderer);
                }

                @Override
                public String name() {
                  return "Grouper Function " + local;
                }

                @Override
                public Type type() {
                  return A_FUNCTION_TYPE;
                }
              };
          break;
      }
    }
    inputExpressions.forEach(
        expression -> expression.collectFreeVariables(freeVariables, Flavour::needsCapture));

    final LambdaBuilder.LambdaType lambdaType;
    final List<Pair<String, Type>> outputBindings;
    switch (grouper.outputs()) {
      case 0:
        lambdaType = LambdaBuilder.supplier(A_BICONSUMER_TYPE);
        outputBindings = List.of();
        break;
      case 1:
        lambdaType = LambdaBuilder.function(A_BICONSUMER_TYPE, typeForOutput(0));
        outputBindings = List.of(pairForOutput(0));
        break;
      case 2:
        lambdaType =
            LambdaBuilder.bifunction(A_BICONSUMER_TYPE, typeForOutput(0), typeForOutput(1));
        outputBindings = List.of(pairForOutput(0), pairForOutput(1));
        break;
      case 3:
        lambdaType = LambdaBuilder.trigrouper(typeForOutput(0), typeForOutput(1), typeForOutput(2));
        outputBindings = List.of(pairForOutput(0), pairForOutput(1), pairForOutput(2));
        break;
      default:
        throw new UnsupportedOperationException(
            String.format(
                "No lambda implementation with %d arguments for %s.",
                grouper.outputs(), grouper.name()));
    }
    oliveBuilder.line(line);
    final var regroup =
        oliveBuilder.regroupWithGrouper(
            line,
            column,
            grouper.name(),
            lambdaType,
            grouperCaptures,
            outputBindings,
            FilterBuilder.of(rejectHandlers),
            oliveBuilder
                .loadableValues()
                .filter(value -> freeVariables.contains(value.name()))
                .toArray(LoadableValue[]::new));

    discriminators.forEach(d -> d.render(regroup));
    final var regrouperForChildren = where.map(w -> regroup.addWhere(w::render)).orElse(regroup);
    children.forEach(group -> group.render(regrouperForChildren, builder));
    regroup.finish();

    oliveBuilder.measureFlow(line, column);
    // We have now thoroughly hosed equals and hashCode in the output. If the next clause was
    // another grouper and it tried to stick things in a hashmap, everything might be endless
    // suffering. So, we make a hidden 1:1 let clause to stop the chaos.
    final var rowType = oliveBuilder.currentType();
    final var let = oliveBuilder.let(line, column);
    discriminators.stream()
        .flatMap(DiscriminatorNode::targets)
        .forEach(
            discriminator -> {
              final var type = discriminator.type().apply(TO_ASM);
              final var method = new Method(discriminator.name(), type, new Type[] {});
              let.add(
                  type,
                  discriminator.name(),
                  r -> {
                    r.loadStream();
                    r.methodGen().invokeVirtual(rowType, method);
                  });
            });
    for (var child : children) {
      final var type = child.type().apply(TO_ASM);
      final var method = new Method(child.name(), type, new Type[] {});
      let.add(
          type,
          child.name(),
          r -> {
            r.loadStream();
            r.methodGen().invokeVirtual(rowType, method);
          });
    }
    let.finish();
  }

  @Override
  public NameDefinitions resolve(
      OliveCompilerServices oliveCompilerServices,
      NameDefinitions defs,
      Consumer<String> errorHandler) {
    final var defsPlusOutput =
        defs.bind(
            IntStream.range(0, grouper.outputs())
                .mapToObj(
                    i ->
                        new Target() {
                          @Override
                          public Flavour flavour() {
                            return Flavour.LAMBDA;
                          }

                          @Override
                          public String name() {
                            return outputNames.get(i);
                          }

                          @Override
                          public void read() {
                            // We don't care if the grouper's special outputs aren't used.
                          }

                          @Override
                          public Imyhat type() {
                            return grouper.output(i).type().render(typeVariables);
                          }
                        })
                .collect(Collectors.toList()));
    var ok = true;
    final var staticDefs = defs.replaceStream(Stream.empty(), true);
    for (var i = 0; i < inputExpressions.size(); i++) {
      final NameDefinitions inputDefs;
      switch (grouper.input(i).kind()) {
        case STATIC:
          inputDefs = staticDefs;
          break;
        case ROW_VALUE:
          inputDefs = defs;
          break;
        default:
          throw new IllegalArgumentException();
      }
      if (!inputExpressions.get(i).resolve(inputDefs, errorHandler)) {
        ok = false;
      }
    }
    final var rejectDefs =
        defs.replaceStream(discriminators.stream().flatMap(DiscriminatorNode::targets), true);
    ok =
        ok
            && children.stream()
                        .filter(
                            child -> child.resolve(defsPlusOutput, defsPlusOutput, errorHandler))
                        .count()
                    == children.size()
                & discriminators.stream()
                        .filter(discriminator -> discriminator.resolve(defs, errorHandler))
                        .count()
                    == discriminators.size()
                & rejectHandlers.stream()
                        .filter(
                            handler ->
                                handler
                                    .resolve(oliveCompilerServices, rejectDefs, errorHandler)
                                    .isGood())
                        .count()
                    == rejectHandlers.size();

    ok =
        ok
            && children.stream()
                .noneMatch(
                    group -> {
                      final var isDuplicate =
                          defs.get(group.name())
                              .filter(variable -> !variable.flavour().isStream())
                              .isPresent();
                      if (isDuplicate) {
                        errorHandler.accept(
                            String.format(
                                "%d:%d: Redefinition of variable “%s”.",
                                group.line(), group.column(), group.name()));
                      }
                      return isDuplicate;
                    });

    ok =
        ok
            && Stream.concat(
                            discriminators.stream().flatMap(DiscriminatorNode::targets),
                            children.stream())
                        .collect(Collectors.groupingBy(DefinedTarget::name))
                        .entrySet()
                        .stream()
                        .filter(e -> e.getValue().size() > 1)
                        .peek(
                            e ->
                                errorHandler.accept(
                                    String.format(
                                        "%d:%d: “Group” has duplicate name %s: %s",
                                        line,
                                        column,
                                        e.getKey(),
                                        e.getValue().stream()
                                            .sorted(
                                                Comparator.comparingInt(DefinedTarget::line)
                                                    .thenComparingInt(DefinedTarget::column))
                                            .map(l -> String.format("%d:%d", l.line(), l.column()))
                                            .collect(Collectors.joining(", ")))))
                        .count()
                    == 0
                & where.map(w -> w.resolve(defs, errorHandler)).orElse(true);
    return defs.replaceStream(
        Stream.concat(
            discriminators.stream().flatMap(DiscriminatorNode::targets), children.stream()),
        ok);
  }

  @Override
  public final boolean resolveDefinitions(
      OliveCompilerServices oliveCompilerServices, Consumer<String> errorHandler) {
    if (grouper == null) {
      errorHandler.accept(
          String.format("%d:%d: Unknown grouping method %s.", line, column, grouperName));
      return false;
    }
    if (outputNames == null) {
      outputNames =
          IntStream.range(0, grouper.outputs())
              .mapToObj(grouper::output)
              .map(GrouperOutput::defaultName)
              .collect(Collectors.toList());
    } else if (outputNames.size() != grouper.outputs()) {
      errorHandler.accept(
          String.format(
              "%d:%d: Grouper %s outputs %d variables, but %d are provided.",
              line, column, grouperName, grouper.outputs(), outputNames.size()));
      return false;
    }
    if (rawInputExpressions.size() != grouper.inputs()) {
      errorHandler.accept(
          String.format(
              "%d:%d: Grouper %s requires %d inputs, but %d are provided.",
              line, column, grouperName, grouper.inputs(), outputNames.size()));
      return false;
    }
    inputExpressions.addAll(Collections.nCopies(grouper.inputs(), null));
    final Map<String, Integer> inputPositions;
    inputPositions =
        IntStream.range(0, grouper.inputs())
            .collect(TreeMap::new, (m, i) -> m.put(grouper.input(i).name(), i), Map::putAll);
    var ok = true;
    for (var inputPair : rawInputExpressions) {
      final int index = inputPositions.getOrDefault(inputPair.first(), -1);
      if (index == -1) {
        errorHandler.accept(
            String.format(
                "%d:%d: Unknown parameter “%s” to grouper %s.",
                line, column, inputPair.first(), grouperName));
        ok = false;
      } else if (inputExpressions.get(index) != null) {
        errorHandler.accept(
            String.format(
                "%d:%d: Duplicate parameter “%s” to grouper %s.",
                line, column, inputPair.first(), grouperName));
        ok = false;
      } else {
        inputExpressions.set(index, inputPair.second());
      }
    }

    ok =
        ok
            && children.stream()
                        .filter(
                            group -> group.resolveDefinitions(oliveCompilerServices, errorHandler))
                        .count()
                    == children.size()
                & inputExpressions.stream()
                        .filter(
                            expression ->
                                expression.resolveDefinitions(oliveCompilerServices, errorHandler))
                        .count()
                    == inputExpressions.size()
                & discriminators.stream()
                        .filter(
                            group -> group.resolveDefinitions(oliveCompilerServices, errorHandler))
                        .count()
                    == discriminators.size()
                & where
                    .map(w -> w.resolveDefinitions(oliveCompilerServices, errorHandler))
                    .orElse(true)
                & rejectHandlers.stream()
                        .filter(
                            rejectHandler ->
                                rejectHandler.resolveDefinitions(
                                    oliveCompilerServices, errorHandler))
                        .count()
                    == rejectHandlers.size();

    return ok;
  }

  @Override
  public final boolean typeCheck(Consumer<String> errorHandler) {
    var ok =
        inputExpressions.stream().filter(expression -> expression.typeCheck(errorHandler)).count()
            == inputExpressions.size();

    ok =
        ok
            && discriminators.stream()
                    .filter(discriminator -> discriminator.typeCheck(errorHandler))
                    .count()
                == discriminators.size();
    if (ok) {
      for (var i = 0; i < inputExpressions.size(); i++) {
        if (!grouper.input(i).type().check(typeVariables, inputExpressions.get(i).type())) {
          inputExpressions
              .get(i)
              .typeError(
                  grouper.input(i).type().render(typeVariables),
                  inputExpressions.get(i).type(),
                  errorHandler);
          ok = false;
        }
      }
    }

    ok =
        ok
            && children.stream().filter(group -> group.typeCheck(errorHandler)).count()
                == children.size();
    ok =
        ok
            && where
                    .map(
                        w -> {
                          var whereOk = w.typeCheck(errorHandler);
                          if (whereOk) {
                            if (!w.type().isSame(Imyhat.BOOLEAN)) {
                              w.typeError(Imyhat.BOOLEAN, w.type(), errorHandler);
                              whereOk = false;
                            }
                          }
                          return whereOk;
                        })
                    .orElse(true)
                & rejectHandlers.stream()
                        .filter(rejectHandler -> rejectHandler.typeCheck(errorHandler))
                        .count()
                    == rejectHandlers.size();
    return ok;
  }

  private Type typeForOutput(int i) {
    return grouper.output(i).kind() == GrouperKind.ROW_VALUE
        ? A_FUNCTION_TYPE
        : grouper.output(i).type().render(typeVariables).apply(TO_ASM);
  }
}
