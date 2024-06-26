package ca.on.oicr.gsi.shesmu.compiler;

import static ca.on.oicr.gsi.shesmu.compiler.BaseOliveBuilder.A_OLIVE_SERVICES_TYPE;

import ca.on.oicr.gsi.shesmu.compiler.OliveNode.ClauseStreamOrder;
import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.compiler.description.OliveClauseRow;
import ca.on.oicr.gsi.shesmu.compiler.description.VariableInformation;
import ca.on.oicr.gsi.shesmu.compiler.description.VariableInformation.Behaviour;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

public class OliveClauseNodeRequire extends OliveClauseNode {

  private static final Type A_OPTIONAL_TYPE = Type.getType(Optional.class);
  private static final Method METHOD_OPTIONAL__IS_PRESENT =
      new Method("isPresent", Type.BOOLEAN_TYPE, new Type[] {});
  private static final Method METHOD_OPTIONAL__STEAM =
      new Method("stream", Type.getType(Stream.class), new Type[] {});
  private final int column;
  private boolean copySignatures;
  private final ExpressionNode expression;
  private final List<RejectNode> handlers;
  private List<Target> incoming;
  private final Optional<String> label;
  private final int line;
  private final DestructuredArgumentNode name;
  private Imyhat type = Imyhat.BAD;

  public OliveClauseNodeRequire(
      Optional<String> label,
      int line,
      int column,
      DestructuredArgumentNode name,
      ExpressionNode expression,
      List<RejectNode> handlers) {
    super();
    this.label = label;
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
            label.orElse("Require"),
            line,
            column,
            true,
            false,
            Stream.concat(
                inputs.stream()
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
      ClauseStreamOrder state,
      Set<String> signableNames,
      Consumer<SignableVariableCheck> addSignableCheck,
      Consumer<String> errorHandler) {
    if (state == ClauseStreamOrder.PURE || state == ClauseStreamOrder.ALMOST_PURE) {
      expression.collectFreeVariables(signableNames, Flavour.STREAM_SIGNABLE::equals);
      copySignatures = true;
    }
    // Although we technically manipulate the stream, we're only adding, so we can pretend the
    // stream is still pure.
    return state;
  }

  @Override
  public int line() {
    return line;
  }

  @Override
  public void render(
      RootBuilder builder,
      BaseOliveBuilder oliveBuilder,
      Function<String, CallableDefinitionRenderer> definitions) {
    final Set<String> freeVariables = new HashSet<>();
    expression.collectFreeVariables(freeVariables, Flavour::needsCapture);
    handlers.forEach(handler -> handler.collectFreeVariables(freeVariables));
    final var captures =
        Stream.of(
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
                handlers.stream().flatMap(handler -> handler.requiredCaptures(builder)),
                oliveBuilder.loadableValues().filter(v -> freeVariables.contains(v.name())))
            .flatMap(Function.identity())
            .toArray(LoadableValue[]::new);
    final var flattenBuilder = oliveBuilder.flatten(line, column, type, copySignatures, captures);

    flattenBuilder.add(name::render);
    incoming.forEach(flattenBuilder::add);

    flattenBuilder.explodeMethod().methodGen().visitCode();
    expression.render(flattenBuilder.explodeMethod());
    flattenBuilder.explodeMethod().methodGen().dup();
    flattenBuilder
        .explodeMethod()
        .methodGen()
        .invokeVirtual(A_OPTIONAL_TYPE, METHOD_OPTIONAL__IS_PRESENT);
    final var end = flattenBuilder.explodeMethod().methodGen().newLabel();
    flattenBuilder.explodeMethod().methodGen().ifZCmp(GeneratorAdapter.NE, end);
    handlers.forEach(handler -> handler.render(builder, flattenBuilder.explodeMethod()));
    flattenBuilder.explodeMethod().methodGen().mark(end);
    flattenBuilder
        .explodeMethod()
        .methodGen()
        .invokeVirtual(A_OPTIONAL_TYPE, METHOD_OPTIONAL__STEAM);
    flattenBuilder.explodeMethod().methodGen().returnValue();
    flattenBuilder.explodeMethod().methodGen().visitMaxs(0, 0);
    flattenBuilder.explodeMethod().methodGen().visitEnd();

    flattenBuilder.finish();

    final var closeRenderer = oliveBuilder.onClose("Require", line, column, captures);
    closeRenderer.methodGen().visitCode();
    handlers.forEach(handler -> handler.renderOnClose(closeRenderer));
    closeRenderer.methodGen().visitInsn(Opcodes.RETURN);
    closeRenderer.methodGen().visitMaxs(0, 0);
    closeRenderer.methodGen().visitEnd();

    oliveBuilder.measureFlow(line, column);
  }

  @Override
  public NameDefinitions resolve(
      OliveCompilerServices oliveCompilerServices,
      NameDefinitions defs,
      Consumer<String> errorHandler) {
    final var definedNames = name.targets().map(Target::name).collect(Collectors.toSet());
    final var duplicates =
        defs.stream()
            .filter(t -> t.flavour().isStream() && definedNames.contains(t.name()))
            .map(Target::name)
            .collect(Collectors.toSet());
    for (final var duplicate : duplicates) {
      errorHandler.accept(
          String.format("%d:%d: Name %s duplicates existing name.", line, column, duplicate));
    }
    incoming = defs.stream().filter(t -> t.flavour().isStream()).collect(Collectors.toList());
    final var good =
        duplicates.isEmpty()
            & expression.resolve(defs, errorHandler)
            & handlers.stream()
                    .filter(
                        handler ->
                            handler.resolve(oliveCompilerServices, defs, errorHandler).isGood())
                    .count()
                == handlers.size();
    return defs.replaceStream(
            Stream.concat(
                name.targets(),
                defs.stream().filter(t -> t.flavour().isStream()).map(Target::softWrap)),
            good)
        .withProvider(
            UndefinedVariableProvider.combine(
                name,
                UndefinedVariableProvider.listen(defs.undefinedVariableProvider(), incoming::add)));
  }

  @Override
  public boolean resolveDefinitions(
      OliveCompilerServices oliveCompilerServices, Consumer<String> errorHandler) {
    return expression.resolveDefinitions(oliveCompilerServices, errorHandler)
        & handlers.stream()
                .filter(handler -> handler.resolveDefinitions(oliveCompilerServices, errorHandler))
                .count()
            == handlers.size()
        & name.checkWildcard(errorHandler) != WildcardCheck.BAD;
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    var success = expression.typeCheck(errorHandler);
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
