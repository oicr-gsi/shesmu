package ca.on.oicr.gsi.shesmu.compiler;

import static org.objectweb.asm.Type.VOID_TYPE;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.compiler.OliveNode.ClauseStreamOrder;
import ca.on.oicr.gsi.shesmu.compiler.description.OliveClauseRow;
import ca.on.oicr.gsi.shesmu.compiler.description.VariableInformation;
import ca.on.oicr.gsi.shesmu.compiler.description.VariableInformation.Behaviour;
import ca.on.oicr.gsi.shesmu.plugin.dumper.Dumper;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.*;
import java.util.function.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

public abstract class OliveClauseNodeBaseDump extends OliveClauseNode implements RejectNode {

  private static final Type A_DUMPER_TYPE = Type.getType(Dumper.class);
  private static final Type A_OBJECT_TYPE = Type.getType(Object.class);
  private static final Method METHOD_DUMPER__WRITE =
      new Method("write", Type.VOID_TYPE, new Type[] {Type.getType(Object[].class)});
  private static final Method METHOD_DUMPER__STOP = new Method("stop", VOID_TYPE, new Type[] {});
  private final int column;
  private final String dumper;
  private DumperDefinition dumperDefinition;

  private final Optional<String> label;
  private final int line;
  private final String selfName;

  public OliveClauseNodeBaseDump(Optional<String> label, int line, int column, String dumper) {
    super();
    this.label = label;
    this.line = line;
    this.column = column;
    this.dumper = dumper;
    selfName = String.format("Dumper %d:%d", line, column);
  }

  protected abstract Predicate<String> captureVariable();

  @Override
  public boolean checkUnusedDeclarations(Consumer<String> errorHandler) {
    return true;
  }

  @Override
  public final int column() {
    return column;
  }

  protected abstract int columnCount();

  protected abstract Stream<String> columnInputs(int index);

  public abstract Pair<String, Imyhat> columnDefinition(int index);

  @Override
  public final Stream<OliveClauseRow> dashboard() {
    return Stream.of(
        new OliveClauseRow(
            label.orElse("Dump"),
            line(),
            column(),
            false,
            false,
            IntStream.range(0, columnCount())
                .mapToObj(
                    index ->
                        new VariableInformation(
                            String.format("%d:%d(%d)", line(), column(), index),
                            columnDefinition(index).second(),
                            columnInputs(index),
                            Behaviour.DEFINITION))));
  }

  @Override
  public final ClauseStreamOrder ensureRoot(
      ClauseStreamOrder state,
      Set<String> signableNames,
      Consumer<SignableVariableCheck> addSignableCheck,
      Consumer<String> errorHandler) {
    return state;
  }

  @Override
  public final int line() {
    return line;
  }

  @Override
  public final void render(
      RootBuilder builder,
      BaseOliveBuilder oliveBuilder,
      Function<String, CallableDefinitionRenderer> definitions) {
    final var shouldCapture = captureVariable();
    final var captures =
        Stream.concat(
                requiredCaptures(builder),
                oliveBuilder.loadableValues().filter(v -> shouldCapture.test(v.name())))
            .toArray(LoadableValue[]::new);
    final var renderer = oliveBuilder.peek("Dump " + dumper, line, column, captures);
    renderer.methodGen().visitCode();
    render(builder, renderer);
    renderer.methodGen().visitInsn(Opcodes.RETURN);
    renderer.methodGen().visitMaxs(0, 0);
    renderer.methodGen().visitEnd();

    final var closeRenderer = oliveBuilder.onClose("Dump " + dumper, line, column, captures);
    closeRenderer.methodGen().visitCode();
    renderOnClose(closeRenderer);
    closeRenderer.methodGen().visitInsn(Opcodes.RETURN);
    closeRenderer.methodGen().visitMaxs(0, 0);
    closeRenderer.methodGen().visitEnd();
  }

  @Override
  public final void renderOnClose(Renderer closeRenderer) {
    closeRenderer.emitNamed(selfName);
    closeRenderer.methodGen().invokeInterface(A_DUMPER_TYPE, METHOD_DUMPER__STOP);
  }

  @Override
  public final void render(RootBuilder builder, Renderer renderer) {
    renderer.emitNamed(selfName);
    renderer.methodGen().push(columnCount());
    renderer.methodGen().newArray(A_OBJECT_TYPE);
    for (var it = 0; it < columnCount(); it++) {
      renderer.methodGen().dup();
      renderer.methodGen().push(it);
      renderColumn(it, renderer);
      renderer.methodGen().valueOf(columnDefinition(it).second().apply(TypeUtils.TO_ASM));
      renderer.methodGen().arrayStore(A_OBJECT_TYPE);
    }
    renderer.methodGen().invokeInterface(A_DUMPER_TYPE, METHOD_DUMPER__WRITE);
  }

  protected abstract void renderColumn(int index, Renderer renderer);

  @Override
  public final Stream<LoadableValue> requiredCaptures(RootBuilder builder) {
    return Stream.of(
        new LoadableValue() {
          private int local = -1;

          @Override
          public void accept(Renderer renderer) {
            /*
             * Okay, we're going to be a little sneaky and gross here. A
             * loadable value is normally responsible for getting a particular
             * value and pushing it on the stack...once. This loadable value
             * will be used twice: once for creating the lambda where this
             * dumper is used and once for creating the Stream.onClose lambda.
             * We only want to create the dumper once, and pass the same
             * instance to both of those lambdas, so we will create a local
             * variable, stuff the dumper into a local and retrieve it when
             * required for generating the subsequent lambda.
             */
            if (local == -1) {
              dumperDefinition.create(builder, renderer);
              local = renderer.methodGen().newLocal(A_DUMPER_TYPE);
              renderer.methodGen().storeLocal(local);
            }
            renderer.methodGen().loadLocal(local);
          }

          @Override
          public String name() {
            return selfName;
          }

          @Override
          public Type type() {
            return A_DUMPER_TYPE;
          }
        });
  }

  @Override
  public final boolean resolveDefinitions(
      OliveCompilerServices oliveCompilerServices, Consumer<String> errorHandler) {

    dumperDefinition = oliveCompilerServices.upsertDumper(dumper);
    return resolveDefinitionsExtra(oliveCompilerServices, errorHandler);
  }

  protected abstract boolean resolveDefinitionsExtra(
      OliveCompilerServices oliveCompilerServices, Consumer<String> errorHandler);

  @Override
  public final boolean typeCheck(Consumer<String> errorHandler) {
    if (!typeCheckExtra(errorHandler)) {
      return false;
    }
    if (dumperDefinition.isFresh()) {
      for (var i = 0; i < columnCount(); i++) {
        final var column = columnDefinition(i);
        dumperDefinition.add(column.first(), column.second());
      }
      return true;
    }
    if (dumperDefinition.size() != columnCount()) {
      errorHandler.accept(
          String.format(
              "%d:%d: Number of arguments (%d) to dumper %s is different from previously (%d).",
              line, column, columnCount(), dumper, dumperDefinition.size()));
      return false;
    }
    var ok = true;
    for (var i = 0; i < dumperDefinition.size(); i++) {
      if (!dumperDefinition.type(i).isSame(columnDefinition(i).second())) {
        errorHandler.accept(
            String.format(
                "%d:%d: The %d argument to dumper %s is was previously %s and is now %s.",
                line,
                column,
                i,
                dumper,
                dumperDefinition.type(i).name(),
                columnDefinition(i).second().name()));
        ok = false;
      }
    }
    return ok;
  }

  protected abstract boolean typeCheckExtra(Consumer<String> errorHandler);
}
