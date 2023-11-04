package ca.on.oicr.gsi.shesmu.compiler;

import static org.objectweb.asm.Type.VOID_TYPE;

import ca.on.oicr.gsi.shesmu.runtime.InputProvider;
import ca.on.oicr.gsi.shesmu.server.Extractor;
import java.util.stream.Stream;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

public abstract class ExtractBuilder implements OwningBuilder {
  private static final Type A_EXTRACTOR_TYPE = Type.getType(Extractor.class);
  private static final Type A_EXTRACTOR_VISITOR_TYPE = Type.getType(Extractor.ExtractVisitor.class);
  private static final Type A_INPUT_PROVIDER_TYPE = Type.getType(InputProvider.class);
  private static final Method CTOR_DEFAULT = new Method("<init>", VOID_TYPE, new Type[] {});
  public static final LoadableValue INPUT_PROVIDER =
      new LoadableValue() {
        @Override
        public void accept(Renderer renderer) {
          renderer.methodGen().loadArg(0);
        }

        @Override
        public String name() {
          return "Input Provider";
        }

        @Override
        public Type type() {
          return A_INPUT_PROVIDER_TYPE;
        }
      };
  private static final Method METHOD_EXTRACTOR__RUN =
      new Method("run", VOID_TYPE, new Type[] {A_INPUT_PROVIDER_TYPE, A_EXTRACTOR_VISITOR_TYPE});
  public static final LoadableValue OUTPUT_VISITOR =
      new LoadableValue() {
        @Override
        public void accept(Renderer renderer) {
          renderer.methodGen().loadArg(1);
        }

        @Override
        public String name() {
          return "Output Stream";
        }

        @Override
        public Type type() {
          return A_EXTRACTOR_VISITOR_TYPE;
        }
      };
  private final ClassVisitor classVisitor;
  private final GeneratorAdapter runMethod;
  private final Type selfType;

  public ExtractBuilder(String name) {
    selfType = Type.getObjectType(name);

    classVisitor = createClassVisitor();
    classVisitor.visit(
        Opcodes.V1_8, Opcodes.ACC_PUBLIC, name, null, A_EXTRACTOR_TYPE.getInternalName(), null);
    classVisitor.visitSource("Query", null);
    final var ctor =
        new GeneratorAdapter(Opcodes.ACC_PUBLIC, CTOR_DEFAULT, null, null, classVisitor);
    ctor.visitCode();
    ctor.loadThis();
    ctor.invokeConstructor(A_EXTRACTOR_TYPE, CTOR_DEFAULT);
    ctor.visitInsn(Opcodes.RETURN);
    ctor.endMethod();

    runMethod =
        new GeneratorAdapter(Opcodes.ACC_PUBLIC, METHOD_EXTRACTOR__RUN, null, null, classVisitor);
    runMethod.visitCode();
  }

  @Override
  public ClassVisitor classVisitor() {
    return classVisitor;
  }

  protected abstract ClassVisitor createClassVisitor();

  public void finish() {
    runMethod.visitInsn(Opcodes.RETURN);
    runMethod.endMethod();
    classVisitor.visitEnd();
  }

  public Renderer renderer() {
    return new RendererNoStream(this, runMethod, Stream.of(INPUT_PROVIDER, OUTPUT_VISITOR), null);
  }

  @Override
  public Type selfType() {
    return selfType;
  }

  @Override
  public String sourceLocation(int line, int column) {
    return String.format("%d:%d", line, column);
  }
}
