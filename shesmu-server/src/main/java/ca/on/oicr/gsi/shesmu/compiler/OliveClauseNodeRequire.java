package ca.on.oicr.gsi.shesmu.compiler;

import static ca.on.oicr.gsi.shesmu.compiler.BaseOliveBuilder.A_OLIVE_SERVICES_TYPE;

import ca.on.oicr.gsi.shesmu.compiler.OliveNode.ClauseStreamOrder;
import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.compiler.description.OliveClauseRow;
import ca.on.oicr.gsi.shesmu.compiler.description.VariableInformation;
import ca.on.oicr.gsi.shesmu.compiler.description.VariableInformation.Behaviour;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

public class OliveClauseNodeRequire extends OliveClauseNode {

  private static final Type A_OPTIONAL_TYPE = Type.getType(Optional.class);
  private static final Type A_RUNTIME_SUPPORT_TYPE = Type.getType(RuntimeSupport.class);
  private static final Method METHOD_OPTIONAL__IS_PRESENT =
      new Method("isPresent", Type.BOOLEAN_TYPE, new Type[] {});
  private static final Method METHOD_RUNTIME_SUPPORT__STEAM_OPTIONAL =
      new Method("stream", Type.getType(Stream.class), new Type[] {A_OPTIONAL_TYPE});
  private final int column;
  private final DestructuredArgumentNode name;
  private final ExpressionNode expression;
  private final List<RejectNode> handlers;
  private final int line;

  public OliveClauseNodeRequire(
      int line,
      int column,
      DestructuredArgumentNode name,
      ExpressionNode expression,
      List<RejectNode> handlers) {
    super();
    this.line = line;
    this.column = column;
    this.name = name;
    this.expression = expression;
    this.handlers = handlers;
    name.setFlavour(Flavour.STREAM);
  }

  @Override
  public boolean checkUnusedDeclarations(Consumer<String> errorHandler) {
    return name.checkUnusedDeclarations(errorHandler);
  }

  @Override
  public void collectPlugins(Set<Path> pluginFileNames) {
    expression.collectPlugins(pluginFileNames);
    handlers.forEach(handler -> handler.collectPlugins(pluginFileNames));
  }

  @Override
  public int column() {
    return column;
  }

  @Override
  public Stream<OliveClauseRow> dashboard() {
    final Set<String> inputs = new TreeSet<>();
    expression.collectFreeVariables(inputs, Flavour::isStream);
    return Stream.of(
        new OliveClauseRow(
            "Require",
            line,
            column,
            true,
            false,
            Stream.concat(
                inputs
                    .stream()
                    .map(
                        n ->
                            new VariableInformation(
                                n, Imyhat.BOOLEAN, Stream.of(n), Behaviour.OBSERVER)),
                name.targets()
                    .map(
                        t ->
                            new VariableInformation(
                                t.name(),
                                t.type(),
                                inputs.stream(),
                                VariableInformation.Behaviour.DEFINITION)))));
  }

  @Override
  public ClauseStreamOrder ensureRoot(
      ClauseStreamOrder state, Set<String> signableNames, Consumer<String> errorHandler) {
    if (state == ClauseStreamOrder.PURE) {
      expression.collectFreeVariables(signableNames, Flavour.STREAM_SIGNABLE::equals);
    }
    // All though we technically manipulate the stream, we're only adding, so we can pretend the
    // stream is still pure.
    return state;
  }

  @Override
  public int line() {
    return line;
  }

  private List<Target> incoming;

  @Override
  public void render(
      RootBuilder builder,
      BaseOliveBuilder oliveBuilder,
      Map<String, OliveDefineBuilder> definitions) {
    final Set<String> freeVariables = new HashSet<>();
    expression.collectFreeVariables(freeVariables, Flavour::needsCapture);
    handlers.forEach(handler -> handler.collectFreeVariables(freeVariables));
    final FlattenBuilder flattenBuilder =
        oliveBuilder.flatten(
            line,
            column,
            type,
            Stream.concat(
                    Stream.of(
                        new LoadableValue() {

                          @Override
                          public void accept(Renderer renderer) {
                            oliveBuilder.loadOliveServices(renderer.methodGen());
                          }

                          @Override
                          public String name() {
                            return "Olive Services";
                          }

                          @Override
                          public Type type() {
                            return A_OLIVE_SERVICES_TYPE;
                          }
                        }),
                    oliveBuilder.loadableValues().filter(v -> freeVariables.contains(v.name())))
                .toArray(LoadableValue[]::new));

    flattenBuilder.add(name::render);
    incoming.forEach(flattenBuilder::add);

    flattenBuilder.explodeMethod().methodGen().visitCode();
    expression.render(flattenBuilder.explodeMethod());
    flattenBuilder.explodeMethod().methodGen().dup();
    flattenBuilder
        .explodeMethod()
        .methodGen()
        .invokeVirtual(A_OPTIONAL_TYPE, METHOD_OPTIONAL__IS_PRESENT);
    final Label end = flattenBuilder.explodeMethod().methodGen().newLabel();
    flattenBuilder.explodeMethod().methodGen().ifZCmp(GeneratorAdapter.NE, end);
    handlers.forEach(handler -> handler.render(builder, flattenBuilder.explodeMethod()));
    flattenBuilder.explodeMethod().methodGen().mark(end);
    flattenBuilder
        .explodeMethod()
        .methodGen()
        .invokeStatic(A_RUNTIME_SUPPORT_TYPE, METHOD_RUNTIME_SUPPORT__STEAM_OPTIONAL);
    flattenBuilder.explodeMethod().methodGen().returnValue();
    flattenBuilder.explodeMethod().methodGen().visitMaxs(0, 0);
    flattenBuilder.explodeMethod().methodGen().visitEnd();

    flattenBuilder.finish();

    oliveBuilder.measureFlow(builder.sourcePath(), line, column);
  }

  @Override
  public NameDefinitions resolve(
      OliveCompilerServices oliveCompilerServices,
      NameDefinitions defs,
      Consumer<String> errorHandler) {
    final Set<String> definedNames = name.targets().map(Target::name).collect(Collectors.toSet());
    final Set<String> duplicates =
        defs.stream()
            .filter(t -> t.flavour().isStream() && definedNames.contains(t.name()))
            .map(Target::name)
            .collect(Collectors.toSet());
    for (final String duplicate : duplicates) {
      errorHandler.accept(
          String.format("%d:%d: Name %s duplicates existing name.", line, column, duplicate));
    }
    incoming = defs.stream().filter(t -> t.flavour().isStream()).collect(Collectors.toList());
    return defs.replaceStream(
            Stream.concat(name.targets(), defs.stream().filter(t -> t.flavour().isStream())),
            duplicates.isEmpty()
                & expression.resolve(defs, errorHandler)
                & handlers
                        .stream()
                        .filter(
                            handler ->
                                handler.resolve(oliveCompilerServices, defs, errorHandler).isGood())
                        .count()
                    == handlers.size())
        .withProvider(name);
  }

  @Override
  public boolean resolveDefinitions(
      OliveCompilerServices oliveCompilerServices, Consumer<String> errorHandler) {
    return expression.resolveDefinitions(oliveCompilerServices, errorHandler)
        & handlers
                .stream()
                .filter(handler -> handler.resolveDefinitions(oliveCompilerServices, errorHandler))
                .count()
            == handlers.size()
        & name.checkWildcard(errorHandler) != WildcardCheck.BAD;
  }

  private Imyhat type = Imyhat.BAD;

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    boolean success = expression.typeCheck(errorHandler);
    if (success) {
      if (expression.type() instanceof Imyhat.OptionalImyhat) {
        type = ((Imyhat.OptionalImyhat) expression.type()).inner();
        success = name.typeCheck(type, errorHandler);
      } else {
        success = false;
        expression.typeError("optional", expression.type(), errorHandler);
      }
    }
    return success
        && handlers.stream().filter(handler -> handler.typeCheck(errorHandler)).count()
            == handlers.size();
  }
}
