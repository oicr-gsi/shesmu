package ca.on.oicr.gsi.shesmu.compiler;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

public class PragmaNodeFrequency extends PragmaNode {
  private static final Type A_INSTANT_TYPE = Type.getType(Instant.class);
  private static final Method METHOD_INSTANT__GET_EPOCH_SECOND =
      new Method("getEpochSecond", Type.LONG_TYPE, new Type[] {});
  private static final Method METHOD_INSTANT__NOW =
      new Method("now", A_INSTANT_TYPE, new Type[] {});
  private final int frequency;

  public PragmaNodeFrequency(int frequency) {
    super();
    this.frequency = frequency;
  }

  @Override
  public Stream<ImportRewriter> imports() {
    return Stream.empty();
  }

  @Override
  public void renderAtExit(RootBuilder builder) {
    Renderer renderer = builder.rootRenderer(false, null);
    renderer.methodGen().loadThis();
    renderer
        .methodGen()
        .invokeStatic(A_INSTANT_TYPE, new Method("now", A_INSTANT_TYPE, new Type[] {}));
    renderer
        .methodGen()
        .invokeVirtual(A_INSTANT_TYPE, new Method("getEpochSecond", Type.LONG_TYPE, new Type[] {}));
    renderer.methodGen().putField(builder.selfType(), "$lastOliveRun", Type.LONG_TYPE);
  }

  @Override
  public void renderGuard(RootBuilder builder) {
    builder
        .classVisitor
        .visitField(
            Opcodes.ACC_PRIVATE, "$lastOliveRun", Type.LONG_TYPE.getDescriptor(), null, null)
        .visitEnd();

    builder.addGuard(
        methodGen -> {
          methodGen.invokeStatic(A_INSTANT_TYPE, METHOD_INSTANT__NOW);
          methodGen.invokeVirtual(A_INSTANT_TYPE, METHOD_INSTANT__GET_EPOCH_SECOND);
          methodGen.loadThis();
          methodGen.getField(builder.selfType(), "$lastOliveRun", Type.LONG_TYPE);
          methodGen.math(Opcodes.ISUB, Type.LONG_TYPE);
          methodGen.push((long) frequency);
          methodGen.visitInsn(Opcodes.LCMP);
          final Label end = methodGen.newLabel();
          final Label skip = methodGen.newLabel();
          methodGen.ifZCmp(GeneratorAdapter.GE, skip);
          methodGen.push(true);
          methodGen.goTo(end);
          methodGen.mark(skip);
          methodGen.push(false);
          methodGen.mark(end);
        });
  }

  @Override
  public void timeout(AtomicInteger timeout) {
    // do nothing
  }
}
