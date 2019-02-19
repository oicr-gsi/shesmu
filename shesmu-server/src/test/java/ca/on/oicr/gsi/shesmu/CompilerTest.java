package ca.on.oicr.gsi.shesmu;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.compiler.Compiler;
import ca.on.oicr.gsi.shesmu.compiler.Renderer;
import ca.on.oicr.gsi.shesmu.compiler.TypeUtils;
import ca.on.oicr.gsi.shesmu.compiler.definitions.ActionDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.ActionParameterDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.ConstantDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.InputFormatDefinition;
import ca.on.oicr.gsi.shesmu.core.StandardDefinitions;
import ca.on.oicr.gsi.shesmu.plugin.action.Action;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionServices;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.util.NameLoader;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Assert;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.util.CheckClassAdapter;

public class CompilerTest {
  public static class TestAction extends Action {
    public long memory;
    public Set<String> input;
    public boolean ok;
    public boolean junk;

    public TestAction() {
      super("test");
    }

    @Override
    public boolean equals(Object other) {
      return false;
    }

    @Override
    public int hashCode() {
      return 0;
    }

    @Override
    public ActionState perform(ActionServices services) {
      return ActionState.SUCCEEDED;
    }

    @Override
    public int priority() {
      return 0;
    }

    @Override
    public long retryMinutes() {
      return 0;
    }

    @Override
    public boolean search(Pattern query) {
      return false;
    }

    @Override
    public ObjectNode toJson(ObjectMapper mapper) {
      return null;
    }
  }

  private static class TestActionDefinition extends ActionDefinition {

    public TestActionDefinition(
        String name, String description, Stream<ActionParameterDefinition> parameters) {
      super(name, description, null, parameters);
    }

    @Override
    public void initialize(GeneratorAdapter methodGen) {
      methodGen.newInstance(Type.getType(TestAction.class));
      methodGen.dup();
      methodGen.invokeConstructor(
          Type.getType(TestAction.class), new Method("<init>", Type.VOID_TYPE, new Type[] {}));
    }
  }

  public static ActionParameterDefinition forField(String name, Imyhat type, boolean required) {
    return new ActionParameterDefinition() {
      @Override
      public String name() {
        return name;
      }

      @Override
      public boolean required() {
        return required;
      }

      @Override
      public void store(Renderer renderer, int actionLocal, Consumer<Renderer> loadParameter) {
        renderer.methodGen().loadLocal(actionLocal);
        renderer.methodGen().checkCast(Type.getType(TestAction.class));
        loadParameter.accept(renderer);
        renderer
            .methodGen()
            .putField(Type.getType(TestAction.class), name, type.apply(TypeUtils.TO_ASM));
      }

      @Override
      public Imyhat type() {
        return type;
      }
    };
  }

  private final ActionDefinition ACTIONS[] =
      new ActionDefinition[] {
        new TestActionDefinition(
            "fastqc",
            "TEST",
            Stream.of(
                forField("memory", Imyhat.INTEGER, true),
                forField("input", Imyhat.STRING.asList(), true))),
        new TestActionDefinition("ok", "TEST", Stream.of(forField("ok", Imyhat.BOOLEAN, true))),
        new TestActionDefinition(
            "optional",
            "TEST",
            Stream.of(
                forField("junk", Imyhat.BOOLEAN, true), forField("ok", Imyhat.BOOLEAN, false))),
      };

  public final class CompilerHarness extends Compiler {
    private Set<String> allowedErrors;
    private boolean dirty;

    public CompilerHarness(Path file) throws IOException {
      super(false);
      try (Stream<String> lines =
          Files.lines(
              file.getParent()
                  .resolve(file.getFileName().toString().replaceFirst("\\.shesmu$", ".errors")))) {
        allowedErrors = lines.collect(Collectors.toSet());
      }
    }

    @Override
    protected ClassVisitor createClassVisitor() {
      final ClassWriter outputWriter =
          new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
      return new ClassVisitor(Opcodes.ASM5, outputWriter) {

        @Override
        public void visitEnd() {
          super.visitEnd();
          final ClassReader reader = new ClassReader(outputWriter.toByteArray());
          final CheckClassAdapter check = new CheckClassAdapter(new ClassWriter(0), true);
          reader.accept(check, 0);
        }
      };
    }

    @Override
    protected void errorHandler(String message) {
      if (allowedErrors.remove(message)) {
        return;
      }
      dirty = true;
      System.err.println(message);
    }

    @Override
    protected ActionDefinition getAction(String name) {
      return actions.get(name);
    }

    @Override
    protected FunctionDefinition getFunction(String name) {
      return functions.get(name);
    }

    @Override
    protected InputFormatDefinition getInputFormats(String name) {
      return RunTest.INPUT_FORMATS.get(name);
    }

    public boolean ok() {
      allowedErrors.forEach(e -> System.err.printf("Missing error: %s\n", e));
      return !dirty && allowedErrors.isEmpty();
    }
  }

  private static final List<ConstantDefinition> CONSTANTS =
      Arrays.asList(
          ConstantDefinition.of("alwaystrue", true, "It's true. I swear."),
          ConstantDefinition.of("notpi", 3, "Any value which is not pi."));

  private final NameLoader<ActionDefinition> actions =
      new NameLoader<>(
          Stream.concat(Stream.of(ACTIONS), new StandardDefinitions().actions()),
          ActionDefinition::name);
  private final NameLoader<FunctionDefinition> functions =
      new NameLoader<>(new StandardDefinitions().functions(), FunctionDefinition::name);

  @Test
  public void testCompiler() throws IOException {
    try (Stream<Path> files =
        Files.walk(Paths.get(this.getClass().getResource("/compiler").getPath()), 1)) {
      Assert.assertTrue(
          "Compilation output not as expected!",
          files
                  .filter(Files::isRegularFile)
                  .filter(p -> p.getFileName().getFileName().toString().endsWith(".shesmu"))
                  .map(this::testFile)
                  .filter(
                      Pair.predicate(
                          (file, ok) -> {
                            System.err.printf("%s %s\n", ok ? "OK" : "FAIL", file.getFileName());
                            return !ok;
                          }))
                  .count()
              == 0);
    }
  }

  private Pair<Path, Boolean> testFile(Path file) {
    try {
      final CompilerHarness compiler = new CompilerHarness(file);
      // Attempt to compile and throw away whether the compiler was successful; we
      // know everything based on the errors generated.
      compiler.compile(
          Files.readAllBytes(file),
          "dyn/shesmu/Program",
          file.toString(),
          CONSTANTS::stream,
          Stream::empty,
          null);
      return new Pair<>(file, compiler.ok());
    } catch (final Exception e) {
      e.printStackTrace();
      return new Pair<>(file, false);
    }
  }
}
