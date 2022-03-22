package ca.on.oicr.gsi.shesmu.compiler;

import static ca.on.oicr.gsi.shesmu.compiler.BaseOliveBuilder.A_OLIVE_SERVICES_TYPE;

import ca.on.oicr.gsi.shesmu.compiler.OliveNode.ClauseStreamOrder;
import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.compiler.description.OliveClauseRow;
import ca.on.oicr.gsi.shesmu.compiler.description.VariableInformation;
import ca.on.oicr.gsi.shesmu.compiler.description.VariableInformation.Behaviour;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;

public class OliveClauseNodeReject extends OliveClauseNode {

  private final int column;
  private final ExpressionNode expression;
  private final List<RejectNode> handlers;
  private final Optional<String> label;
  private final int line;

  public OliveClauseNodeReject(
      Optional<String> label,
      int line,
      int column,
      ExpressionNode expression,
      List<RejectNode> handlers) {
    super();
    this.label = label;
    this.line = line;
    this.column = column;
    this.expression = expression;
    this.handlers = handlers;
  }

  @Override
  public boolean checkUnusedDeclarations(Consumer<String> errorHandler) {
    return true;
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
            label.orElse("Reject"),
            line,
            column,
            true,
            false,
            inputs.stream()
                .map(
                    n ->
                        new VariableInformation(
                            n, Imyhat.BOOLEAN, Stream.of(n), Behaviour.OBSERVER))));
  }

  @Override
  public ClauseStreamOrder ensureRoot(
      ClauseStreamOrder state,
      Set<String> signableNames,
      Consumer<SignableVariableCheck> addSignableCheck,
      Consumer<String> errorHandler) {
    if (state == ClauseStreamOrder.PURE || state == ClauseStreamOrder.ALMOST_PURE) {
      expression.collectFreeVariables(signableNames, Flavour.STREAM_SIGNABLE::equals);
    }
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
    final var renderer = oliveBuilder.filter(line, column, captures);

    renderer.methodGen().visitCode();
    expression.render(renderer);
    final var handleReject = renderer.methodGen().newLabel();
    renderer.methodGen().ifZCmp(GeneratorAdapter.NE, handleReject);
    renderer.methodGen().push(true);
    renderer.methodGen().returnValue();
    renderer.methodGen().mark(handleReject);
    handlers.forEach(handler -> handler.render(builder, renderer));
    renderer.methodGen().push(false);
    renderer.methodGen().returnValue();
    renderer.methodGen().visitMaxs(0, 0);
    renderer.methodGen().visitEnd();

    final var closeRenderer = oliveBuilder.onClose("Reject", line, column, captures);
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
    return defs.fail(
        expression.resolve(defs, errorHandler)
            & handlers.stream()
                    .filter(
                        handler ->
                            handler.resolve(oliveCompilerServices, defs, errorHandler).isGood())
                    .count()
                == handlers.size());
  }

  @Override
  public boolean resolveDefinitions(
      OliveCompilerServices oliveCompilerServices, Consumer<String> errorHandler) {
    return expression.resolveDefinitions(oliveCompilerServices, errorHandler)
        & handlers.stream()
                .filter(handler -> handler.resolveDefinitions(oliveCompilerServices, errorHandler))
                .count()
            == handlers.size();
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    var success = expression.typeCheck(errorHandler);
    if (success) {
      if (!expression.type().isSame(Imyhat.BOOLEAN)) {
        success = false;
        expression.typeError(Imyhat.BOOLEAN, expression.type(), errorHandler);
      }
    }
    return success
        && handlers.stream().filter(handler -> handler.typeCheck(errorHandler)).count()
            == handlers.size();
  }
}
