package ca.on.oicr.gsi.shesmu.compiler;

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
  private static final Method DUMPER__WRITE =
      new Method("write", Type.VOID_TYPE, new Type[] {Type.getType(Object[].class)});
  private final int column;
  private final String dumper;
  private List<Imyhat> dumperTypes;

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

  public abstract Imyhat columnType(int index);

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
                            columnType(index),
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
    final Predicate<String> shouldCapture = captureVariable();
    final Renderer renderer =
        oliveBuilder.peek(
            "Dump " + dumper,
            line,
            column,
            Stream.concat(
                    requiredCaptures(builder),
                    oliveBuilder.loadableValues().filter(v -> shouldCapture.test(v.name())))
                .toArray(LoadableValue[]::new));
    renderer.methodGen().visitCode();
    render(builder, renderer);
    renderer.methodGen().visitInsn(Opcodes.RETURN);
    renderer.methodGen().visitMaxs(0, 0);
    renderer.methodGen().visitEnd();
  }

  @Override
  public final void render(RootBuilder builder, Renderer renderer) {
    renderer.emitNamed(selfName);
    renderer.methodGen().push(columnCount());
    renderer.methodGen().newArray(A_OBJECT_TYPE);
    for (int it = 0; it < columnCount(); it++) {
      renderer.methodGen().dup();
      renderer.methodGen().push(it);
      renderColumn(it, renderer);
      renderer.methodGen().valueOf(columnType(it).apply(TypeUtils.TO_ASM));
      renderer.methodGen().arrayStore(A_OBJECT_TYPE);
    }
    renderer.methodGen().invokeInterface(A_DUMPER_TYPE, DUMPER__WRITE);
  }

  protected abstract void renderColumn(int index, Renderer renderer);

  @Override
  public final Stream<LoadableValue> requiredCaptures(RootBuilder builder) {
    return Stream.of(
        new LoadableValue() {
          @Override
          public void accept(Renderer renderer) {
            builder.createDumper(
                dumper,
                renderer,
                IntStream.range(0, columnCount())
                    .mapToObj(OliveClauseNodeBaseDump.this::columnType)
                    .toArray(Imyhat[]::new));
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

    dumperTypes = oliveCompilerServices.upsertDumper(dumper);
    return resolveDefinitionsExtra(oliveCompilerServices, errorHandler);
  }

  protected abstract boolean resolveDefinitionsExtra(
      OliveCompilerServices oliveCompilerServices, Consumer<String> errorHandler);

  @Override
  public final boolean typeCheck(Consumer<String> errorHandler) {
    if (!typeCheckExtra(errorHandler)) {
      return false;
    }
    if (dumperTypes.isEmpty()) {
      IntStream.range(0, columnCount()).mapToObj(this::columnType).forEachOrdered(dumperTypes::add);
      return true;
    }
    if (dumperTypes.size() != columnCount()) {
      errorHandler.accept(
          String.format(
              "%d:%d: Number of arguments (%d) to dumper %s is different from previously (%d).",
              line, column, columnCount(), dumper, dumperTypes.size()));
      return false;
    }
    boolean ok = true;
    for (int i = 0; i < dumperTypes.size(); i++) {
      if (!dumperTypes.get(i).isSame(columnType(i))) {
        errorHandler.accept(
            String.format(
                "%d:%d: The %d argument to dumper %s is was previously %s and is now %s.",
                line, column, i, dumper, dumperTypes.get(i).name(), columnType(i).name()));
        ok = false;
      }
    }
    return ok;
  }

  protected abstract boolean typeCheckExtra(Consumer<String> errorHandler);
}
