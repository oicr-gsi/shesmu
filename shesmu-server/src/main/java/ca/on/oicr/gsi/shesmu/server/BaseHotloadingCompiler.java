package ca.on.oicr.gsi.shesmu.server;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

/** Compiles a user-specified file into a usable program and updates it as necessary */
public abstract class BaseHotloadingCompiler {
  final class WritingClassVisitor extends ClassVisitor {

    private String className;

    private final ClassWriter writer;

    private WritingClassVisitor(ClassWriter writer) {
      super(Opcodes.ASM5, writer);
      this.writer = writer;
    }

    @Override
    public void visit(
        int version,
        int access,
        String className,
        String signature,
        String super_name,
        String[] interfaces) {
      this.className = className;
      super.visit(version, access, className, signature, super_name, interfaces);
    }

    @Override
    public void visitEnd() {
      super.visitEnd();
      bytecode.put(className.replace('/', '.'), writer.toByteArray());
    }
  }

  private final Map<String, byte[]> bytecode = new HashMap<>();

  private final ClassLoader classloader =
      new ClassLoader() {

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
          if (bytecode.containsKey(name)) {
            final var contents = bytecode.get(name);
            return defineClass(name, contents, 0, contents.length);
          }
          return super.findClass(name);
        }
      };

  /** Create a new class that will be registered with this loader */
  protected final ClassVisitor createClassVisitor() {
    return new WritingClassVisitor(
        new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS));
  }

  /**
   * Create an instantiate a new instance of a just compiled class.
   *
   * <p>This requires a zero-arguments constructor.
   *
   * @param clazz a super class of the target class
   * @param className the internal name of the target class
   */
  protected final <T> T load(Class<T> clazz, String className)
      throws InstantiationException, IllegalAccessException, ClassNotFoundException,
          NoSuchMethodException, InvocationTargetException {
    return loadClass(clazz, className).getConstructor().newInstance();
  }

  public <T> Class<? extends T> loadClass(Class<T> clazz, String className)
      throws ClassNotFoundException {
    return classloader.loadClass(className).asSubclass(clazz);
  }
}
