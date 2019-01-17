package ca.on.oicr.gsi.shesmu;

import ca.on.oicr.gsi.shesmu.core.StandardDefinitions;
import ca.on.oicr.gsi.shesmu.runtime.Tuple;
import ca.on.oicr.gsi.shesmu.util.NameLoader;
import ca.on.oicr.gsi.shesmu.util.input.BaseInputFormatDefinition;
import ca.on.oicr.gsi.shesmu.util.server.HotloadingCompiler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.Assert;
import org.junit.Test;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

public class RunTest {

  private class ActionChecker implements ActionConsumer {

    private int bad;
    private int good;

    @Override
    public boolean accept(Action action, String filename, int line, int column, long time) {
      if (action.perform() == ActionState.SUCCEEDED) {
        good++;
      } else {
        bad++;
      }
      return false;
    }

    @Override
    public boolean accept(String[] labels, String[] annotation, long ttl) {
      if (IntStream.range(0, labels.length / 2)
          .anyMatch(i -> labels[i * 2].equals("value") && labels[i * 2 + 1].equals("true"))) {
        good++;
      } else {
        bad++;
      }
      return true;
    }

    public boolean ok() {
      return bad == 0 && good > 0;
    }
  }

  public static class OkAction extends Action {

    public boolean ok;

    public OkAction() {
      super("ok");
    }

    @Override
    public boolean equals(Object other) {
      return this == other;
    }

    @Override
    public int hashCode() {
      return 31;
    }

    @Override
    public ActionState perform() {
      return ok ? ActionState.SUCCEEDED : ActionState.FAILED;
    }

    @Override
    public int priority() {
      return 0;
    }

    @Override
    public long retryMinutes() {
      return 15;
    }

    @Override
    public ObjectNode toJson(ObjectMapper mapper) {
      return mapper.createObjectNode().put("ok", ok);
    }
  }

  private static final Type A_OK_ACTION_TYPE = Type.getType(OkAction.class);

  private static final List<ConstantDefinition> CONSTANTS =
      Arrays.asList(ConstantDefinition.of("project_constant", "the_foo_study", "Testing constant"));

  private static InnerTestValue[] INNER_TEST_DATA =
      new InnerTestValue[] {new InnerTestValue(300, "a"), new InnerTestValue(307, "b")};
  public static final NameLoader<InputFormatDefinition> INPUT_FORMATS =
      new NameLoader<>(
          Stream.of(
              new BaseInputFormatDefinition<TestValue, TestRepository>(
                  "test", TestValue.class, TestRepository.class) {},
              new BaseInputFormatDefinition<InnerTestValue, InnerTestRepository>(
                  "inner_test", InnerTestValue.class, InnerTestRepository.class) {}),
          InputFormatDefinition::name);
  private static final FunctionDefinition INT2DATE =
      new FunctionDefinition() {
        @Override
        public String description() {
          return "Testing function";
        }

        @Override
        public String name() {
          return "int_to_date";
        }

        @Override
        public Stream<FunctionParameter> parameters() {
          return Stream.of(new FunctionParameter("arg", Imyhat.INTEGER));
        }

        @Override
        public void render(GeneratorAdapter methodGen) {
          methodGen.invokeStatic(
              Type.getType(Instant.class),
              new Method(
                  "ofEpochSecond", Type.getType(Instant.class), new Type[] {Type.LONG_TYPE}));
        }

        @Override
        public final void renderStart(GeneratorAdapter methodGen) {
          // None required.
        }

        @Override
        public Imyhat returnType() {
          return Imyhat.DATE;
        }
      };

  private static final FunctionDefinition INT2STR =
      new FunctionDefinition() {

        @Override
        public String description() {
          return "Testing function";
        }

        @Override
        public String name() {
          return "int_to_str";
        }

        @Override
        public Stream<FunctionParameter> parameters() {
          return Stream.of(new FunctionParameter("arg", Imyhat.INTEGER));
        }

        @Override
        public void render(GeneratorAdapter methodGen) {
          methodGen.invokeStatic(
              Type.getType(Long.class),
              new Method("toString", Type.getType(String.class), new Type[] {Type.LONG_TYPE}));
        }

        @Override
        public final void renderStart(GeneratorAdapter methodGen) {
          // None required.
        }

        @Override
        public Imyhat returnType() {
          return Imyhat.STRING;
        }
      };

  private static final ActionDefinition OK_ACTION_DEFINITION =
      new ActionDefinition(
          "ok",
          A_OK_ACTION_TYPE,
          "For unit tests.",
          Stream.of(ActionParameterDefinition.forField("ok", "ok", Imyhat.BOOLEAN, true))) {

        @Override
        public void initialize(GeneratorAdapter methodGen) {
          methodGen.newInstance(A_OK_ACTION_TYPE);
          methodGen.dup();
          methodGen.invokeConstructor(
              A_OK_ACTION_TYPE, new Method("<init>", Type.VOID_TYPE, new Type[] {}));
        }
      };

  private static TestValue[] TEST_DATA =
      new TestValue[] {
        new TestValue(
            "1", "/foo1", 3, "SlowA", new Tuple(1L, 2L, 3L), "the_foo_study", 307L, Instant.EPOCH),
        new TestValue(
            "2",
            "/foo2",
            3,
            "SlowA",
            new Tuple(1L, 2L, 3L),
            "the_foo_study",
            300L,
            Instant.EPOCH.plusSeconds(500))
      };

  private Stream<ActionDefinition> actions() {
    return Stream.of(OK_ACTION_DEFINITION);
  }

  private <T> Stream<T> data(Class<T> clazz) {
    return clazz.equals(InnerTestValue.class)
        ? Stream.of(INNER_TEST_DATA).map(clazz::cast)
        : Stream.of(TEST_DATA).map(clazz::cast);
  }

  private Stream<FunctionDefinition> functions() {
    return Stream.concat(Stream.of(INT2STR, INT2DATE), new StandardDefinitions().functions());
  }

  @Test
  public void testData() throws IOException {
    System.err.println("Testing data-handling code");
    try (Stream<Path> files =
        Files.walk(Paths.get(this.getClass().getResource("/run").getPath()), 1)) {
      Assert.assertTrue(
          "Sample program failed to run!",
          files
                  .filter(Files::isRegularFile)
                  .filter(f -> f.getFileName().toString().endsWith(".shesmu"))
                  .sorted((a, b) -> a.getFileName().compareTo(b.getFileName()))
                  .filter(this::testFile)
                  .count()
              == 0);
    }
  }

  private boolean testFile(Path file) {
    try {
      final HotloadingCompiler compiler =
          new HotloadingCompiler(
              INPUT_FORMATS::get, this::functions, this::actions, CONSTANTS::stream);
      final ActionGenerator generator = compiler.compile(file, null).orElse(ActionGenerator.NULL);
      compiler.errors().forEach(System.err::println);
      final ActionChecker checker = new ActionChecker();
      generator.run(checker, this::data);
      if (checker.ok()) {
        System.err.printf("OK %s\n", file.getFileName());
        return false;
      } else {
        compiler.errors().forEach(System.out::println);
        System.err.printf("FAIL %s\n", file.getFileName());
        return true;
      }
    } catch (Exception | VerifyError | BootstrapMethodError | IncompatibleClassChangeError e) {
      System.err.printf("EXCP %s\n", file.getFileName());
      e.printStackTrace();
      return true;
    }
  }
}
