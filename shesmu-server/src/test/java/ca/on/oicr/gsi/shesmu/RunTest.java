package ca.on.oicr.gsi.shesmu;

import ca.on.oicr.gsi.shesmu.compiler.LiveExportConsumer;
import ca.on.oicr.gsi.shesmu.compiler.RefillerDefinition;
import ca.on.oicr.gsi.shesmu.compiler.RefillerParameterDefinition;
import ca.on.oicr.gsi.shesmu.compiler.Renderer;
import ca.on.oicr.gsi.shesmu.compiler.definitions.*;
import ca.on.oicr.gsi.shesmu.compiler.description.FileTable;
import ca.on.oicr.gsi.shesmu.core.StandardDefinitions;
import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import ca.on.oicr.gsi.shesmu.plugin.action.Action;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionServices;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import ca.on.oicr.gsi.shesmu.plugin.dumper.Dumper;
import ca.on.oicr.gsi.shesmu.plugin.functions.FunctionParameter;
import ca.on.oicr.gsi.shesmu.plugin.input.InputFormat;
import ca.on.oicr.gsi.shesmu.plugin.refill.Refiller;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.runtime.ActionGenerator;
import ca.on.oicr.gsi.shesmu.runtime.InputProvider;
import ca.on.oicr.gsi.shesmu.runtime.OliveServices;
import ca.on.oicr.gsi.shesmu.server.HotloadingCompiler;
import ca.on.oicr.gsi.shesmu.server.plugins.AnnotatedInputFormatDefinition;
import ca.on.oicr.gsi.shesmu.server.plugins.JsonInputFormatDefinition;
import ca.on.oicr.gsi.shesmu.util.NameLoader;
import ca.on.oicr.gsi.status.ConfigurationSection;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

public class RunTest {
  private static class ActionChecker implements OliveServices {

    private int bad;
    private int good;

    @Override
    public boolean accept(
        Action action, String filename, int line, int column, String hash, String[] tags) {
      if (action.perform(null, Duration.ZERO, true) == ActionState.SUCCEEDED) {
        good++;
      } else {
        bad++;
      }
      return false;
    }

    @Override
    public boolean accept(
        String[] labels,
        String[] annotation,
        long ttl,
        String filename,
        int line,
        int column,
        String hash) {
      if (IntStream.range(0, labels.length / 2)
          .anyMatch(i -> labels[i * 2].equals("value") && labels[i * 2 + 1].equals("true"))) {
        good++;
      } else {
        bad++;
      }
      return true;
    }

    @Override
    public Dumper findDumper(String name, String[] columns, Imyhat... types) {
      if (columns.length != types.length) {
        bad++;
      }
      // We assume this test is bad unless it cleans up the dumper, as is required by that API.
      bad++;
      return new Dumper() {
        @Override
        public void stop() {
          bad--;
        }

        @Override
        public void write(Object... values) {
          if (values.length != types.length) {
            bad++;
          }
        }
      };
    }

    @Override
    public boolean isOverloaded(String... services) {
      return false;
    }

    @Override
    public <T> Stream<T> measureFlow(
        Stream<T> input,
        String filename,
        int line,
        int column,
        String hash,
        String oliveFile,
        int oliveLine,
        int oliveColumn,
        String oliveHash) {
      return input;
    }

    public boolean ok() {
      return bad == 0 && good > 0;
    }

    @Override
    public void oliveRuntime(String filename, int line, int column, long timeInNs) {
      // Don't care
    }
  }

  public static class DummyRefiller<I> extends Refiller<I> {

    public Function<I, Boolean> value;

    @Override
    public void consume(Stream<I> items) {
      REFILL_OKAY.set(items.allMatch(value::apply));
    }
  }

  private static class InputProviderChecker implements InputProvider {
    private final Set<String> usedFormats = new HashSet<>();

    public Stream<Object> fetch(String format) {
      usedFormats.add(format);
      return switch (format) {
        case "inner_test" -> Stream.of(INNER_TEST_DATA);
        case "test" -> Stream.of(TEST_DATA);
        case "test_json" -> JSON_DATA.stream();
        default -> Stream.empty();
      };
    }

    public boolean ok(ActionGenerator generator) {
      return generator.inputs().count() == usedFormats.size()
          && generator.inputs().allMatch(usedFormats::contains);
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
    public void generateUUID(Consumer<byte[]> digest) {
      digest.accept(new byte[] {(byte) (ok ? 1 : 0)});
    }

    @Override
    public int hashCode() {
      return 31;
    }

    @Override
    public ActionState perform(
        ActionServices services, Duration lastGeneratedByOlive, boolean isOliveLive) {
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
    public boolean search(Pattern query) {
      return false;
    }

    @Override
    public ObjectNode toJson(ObjectMapper mapper) {
      return mapper.createObjectNode().put("ok", ok);
    }
  }

  private static JsonNode horror1() {
    final var node = JsonNodeFactory.instance.objectNode();
    node.put("foo", 3);
    node.put("bar", "hi");
    return node;
  }

  private static JsonNode horror2() {
    final var node = JsonNodeFactory.instance.arrayNode();
    node.add(0);
    return node;
  }

  private static final Type A_OK_ACTION_TYPE = Type.getType(OkAction.class);
  private static final List<ConstantDefinition> CONSTANTS =
      List.of(ConstantDefinition.of("project_constant", "the_foo_study", "Testing constant"));
  private static final InnerTestValue[] INNER_TEST_DATA =
      new InnerTestValue[] {new InnerTestValue(300, "a"), new InnerTestValue(307, "b")};
  public static final NameLoader<InputFormatDefinition> INPUT_FORMATS;
  private static final FunctionDefinition INT2DATE =
      new FunctionDefinition() {
        @Override
        public String description() {
          return "Testing function";
        }

        @Override
        public Path filename() {
          return null;
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
        public String renderEcma(Object[] args) {
          throw new UnsupportedOperationException();
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
        public Path filename() {
          return null;
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
        public String renderEcma(Object[] args) {
          throw new UnsupportedOperationException();
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
  public static final List<Object> JSON_DATA;
  private static final ActionDefinition OK_ACTION_DEFINITION =
      new ActionDefinition(
          "ok",
          "For unit tests.",
          null,
          Stream.of(
              new ActionParameterDefinition() {
                @Override
                public String name() {
                  return "ok";
                }

                @Override
                public boolean required() {
                  return true;
                }

                @Override
                public void store(
                    Renderer renderer, int actionLocal, Consumer<Renderer> loadParameter) {
                  renderer.methodGen().loadLocal(actionLocal);
                  renderer.methodGen().checkCast(A_OK_ACTION_TYPE);
                  loadParameter.accept(renderer);
                  renderer.methodGen().putField(A_OK_ACTION_TYPE, "ok", Type.BOOLEAN_TYPE);
                }

                @Override
                public Imyhat type() {
                  return Imyhat.BOOLEAN;
                }
              })) {

        @Override
        public void initialize(GeneratorAdapter methodGen) {
          methodGen.newInstance(A_OK_ACTION_TYPE);
          methodGen.dup();
          methodGen.invokeConstructor(
              A_OK_ACTION_TYPE, new Method("<init>", Type.VOID_TYPE, new Type[] {}));
        }
      };
  public static final ThreadLocal<Boolean> REFILL_OKAY = new ThreadLocal<>();
  private static final TestValue[] TEST_DATA =
      new TestValue[] {
        new TestValue(
            "1",
            "/foo1",
            3,
            "SlowA",
            new Tuple(1L, 2L, 3L),
            "the_foo_study",
            307L,
            Instant.EPOCH,
            horror1()),
        new TestValue(
            "2",
            "/foo2",
            3,
            "SlowA",
            new Tuple(1L, 2L, 3L),
            "the_foo_study",
            300L,
            Instant.EPOCH.plusSeconds(500),
            horror2())
      };

  static {
    NameLoader<InputFormatDefinition> inputFormats = null;
    List<Object> jsonData = null;
    try {
      final var json =
          JsonInputFormatDefinition.create(
              Path.of(RunTest.class.getResource("/test_json.shesmuschema").getPath()));
      jsonData = json.readJsonString("[{\"x\":1,\"y\":\"a\",\"z\":[]}]");
      inputFormats =
          new NameLoader<>(
              Stream.of(
                  AnnotatedInputFormatDefinition.create(
                      new InputFormat("test", TestValue.class, MethodHandles.lookup())),
                  AnnotatedInputFormatDefinition.create(
                      new InputFormat("inner_test", InnerTestValue.class, MethodHandles.lookup())),
                  json),
              InputFormatDefinition::name);
    } catch (IllegalAccessException | IOException e) {
      e.printStackTrace();
    }
    INPUT_FORMATS = inputFormats;
    JSON_DATA = jsonData;
  }

  @Test
  public void testData() throws IOException {
    System.err.println("Testing data-handling code");
    try (var files = Files.walk(Paths.get(this.getClass().getResource("/run").getPath()), 1)) {
      Assertions.assertTrue(
          files
                  .filter(Files::isRegularFile)
                  .filter(f -> f.getFileName().toString().endsWith(".shesmu"))
                  .sorted(Comparator.comparing(Path::getFileName))
                  .filter(this::testFile)
                  .count()
              == 0,
          "Sample program failed to run!");
    }
  }

  private boolean testFile(Path file) {
    final var dashboard = new AtomicReference<FileTable>();
    try {
      final var compiler =
          new HotloadingCompiler(
              INPUT_FORMATS::get,
              DefinitionRepository.concat(
                  new StandardDefinitions(),
                  new DefinitionRepository() {
                    @Override
                    public Stream<ActionDefinition> actions() {
                      return Stream.of(OK_ACTION_DEFINITION);
                    }

                    @Override
                    public Stream<ConstantDefinition> constants() {
                      return CONSTANTS.stream();
                    }

                    @Override
                    public Stream<CallableOliveDefinition> oliveDefinitions() {
                      return Stream.empty();
                    }

                    @Override
                    public Stream<FunctionDefinition> functions() {
                      return Stream.of(INT2STR, INT2DATE);
                    }

                    @Override
                    public Stream<ConfigurationSection> listConfiguration() {
                      return Stream.empty();
                    }

                    @Override
                    public Stream<RefillerDefinition> refillers() {
                      return Stream.of(
                          new RefillerDefinition() {
                            @Override
                            public String description() {
                              return "Test";
                            }

                            @Override
                            public Path filename() {
                              return null;
                            }

                            @Override
                            public String name() {
                              return "testdb";
                            }

                            @Override
                            public Stream<RefillerParameterDefinition> parameters() {
                              return Stream.of(
                                  new RefillerParameterDefinition() {
                                    @Override
                                    public String name() {
                                      return "value";
                                    }

                                    @Override
                                    public void render(
                                        Renderer renderer, int refillerLocal, int functionLocal) {
                                      renderer.methodGen().loadLocal(refillerLocal);
                                      renderer.methodGen().loadLocal(functionLocal);
                                      renderer
                                          .methodGen()
                                          .putField(
                                              Type.getType(DummyRefiller.class),
                                              "value",
                                              Type.getType(Function.class));
                                    }

                                    @Override
                                    public Imyhat type() {
                                      return Imyhat.BOOLEAN;
                                    }
                                  });
                            }

                            @Override
                            public void render(Renderer renderer) {
                              renderer.methodGen().newInstance(Type.getType(DummyRefiller.class));
                              renderer.methodGen().dup();
                              renderer
                                  .methodGen()
                                  .invokeConstructor(
                                      Type.getType(DummyRefiller.class),
                                      new Method("<init>", Type.VOID_TYPE, new Type[0]));
                            }
                          });
                    }

                    @Override
                    public Stream<SignatureDefinition> signatures() {
                      return Stream.empty();
                    }

                    @Override
                    public void writeJavaScriptRenderer(PrintStream writer) {
                      // Do nothing.
                    }
                  }));
      final var generator =
          compiler
              .compile(
                  file,
                  new LiveExportConsumer() {
                    @Override
                    public void constant(MethodHandle method, String name, Imyhat type) {
                      // Do nothing
                    }

                    @Override
                    public void defineOlive(
                        MethodHandle method,
                        String name,
                        String inputFormatName,
                        boolean isRoot,
                        List<Imyhat> parameterTypes,
                        List<DefineVariableExport> variables,
                        List<DefineVariableExport> checks) {
                      // Do nothing
                    }

                    @Override
                    public void function(
                        MethodHandle method,
                        String name,
                        Imyhat returnType,
                        Supplier<Stream<FunctionParameter>> parameters) {
                      // Do nothing
                    }
                  },
                  dashboard::set,
                  importVerifier -> {})
              .orElse(ActionGenerator.NULL);
      compiler.errors().forEach(System.err::println);
      final var checker = new ActionChecker();
      final var input = new InputProviderChecker();
      REFILL_OKAY.set(false);
      generator.run(checker, input);
      if ((checker.ok() || REFILL_OKAY.get())
              != file.getFileName().toString().endsWith("-fail.shesmu")
          && input.ok(generator)) {
        System.err.printf("OK %s\n", file.getFileName());
        return false;
      } else {
        compiler.errors().forEach(System.out::println);
        System.err.printf("FAIL %s\n", file.getFileName());
        return true;
      }
    } catch (Exception
        | VerifyError
        | BootstrapMethodError
        | IncompatibleClassChangeError
        | StackOverflowError e) {
      System.err.printf("EXCP %s\n", file.getFileName());
      if (dashboard.get() != null) {
        System.err.println(dashboard.get().bytecode());
      }
      e.printStackTrace();
      return true;
    }
  }
}
