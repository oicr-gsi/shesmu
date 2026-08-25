package ca.on.oicr.gsi.shesmu;

import ca.on.oicr.gsi.shesmu.compiler.ExtractBuilder;
import ca.on.oicr.gsi.shesmu.compiler.ExtractionNode;
import ca.on.oicr.gsi.shesmu.compiler.ExtractorScriptNode;
import ca.on.oicr.gsi.shesmu.compiler.definitions.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.core.StandardDefinitions;
import ca.on.oicr.gsi.shesmu.plugin.Parser;
import ca.on.oicr.gsi.shesmu.server.OutputFormat;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.util.CheckClassAdapter;
import org.objectweb.asm.util.TraceClassVisitor;

/**
 * Guards the bytecode emitted by {@link ExtractorScriptNode} against regressions introduced by the
 * Jackson 2 → Jackson 3 migration.
 *
 * <p>Jackson 3 made {@code JsonGenerator}'s write methods fluent: {@code writeStartObject()},
 * {@code writeName(String)} and friends return the generator instead of {@code void}, and {@code
 * writeFieldName} was renamed to {@code writeName}. Because this code generates calls to those
 * methods directly as ASM {@link org.objectweb.asm.commons.Method} descriptors, the change is
 * invisible to javac — the emitted bytecode silently started leaving a returned generator on the
 * operand stack after every write. That is still *verifiable* bytecode (the JVM discards leftover
 * operand stack at return), so it produced correct output and raised no {@code VerifyError}; the
 * only visible symptom was the operand stack growing without bound.
 *
 * <p>{@link #jsonOperandStackDepthDoesNotGrowWithColumnCount()} is the direct regression test for
 * that defect.
 */
public class ExtractorBytecodeTest {

  /** Columns of the {@code test} input format, used to build progressively wider queries. */
  private static final String[] COLUMNS = {
    "project", "workflow", "library_size", "accession", "file_size", "path", "timestamp"
  };

  /** Compile an extraction query and hand back the raw generated class file. */
  private static byte[] generate(String query, OutputFormat outputFormat) {
    final var definitions = new StandardDefinitions();
    final var inputFormat = RunTest.INPUT_FORMATS.get("test");
    final var errors = new ArrayList<String>();
    final var node = new AtomicReference<ExtractorScriptNode>();
    Assertions.assertTrue(
        Parser.start(query, (l, c, m) -> errors.add(String.format("%d:%d: %s", l, c, m)))
            .then((i, o) -> ExtractorScriptNode.parse(outputFormat, i, o), node::set)
            .finished(),
        () -> String.format("Failed to parse %s: %s", query, errors));
    Assertions.assertTrue(
        node.get()
            .validate(
                inputFormat,
                definitions
                        .functions()
                        .collect(Collectors.toMap(FunctionDefinition::name, Function.identity()))
                    ::get,
                errors::add,
                definitions::constants,
                new TreeMap<String, List<ExtractionNode>>()),
        () -> String.format("Failed to validate %s: %s", query, errors));

    final var writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
    final var builder =
        new ExtractBuilder("dyn/shesmu/Generated") {
          @Override
          protected ClassVisitor createClassVisitor() {
            return writer;
          }
        };
    node.get().render(builder);
    builder.finish();
    return writer.toByteArray();
  }

  /** A query selecting the first {@code count} columns of the test input format. */
  private static String queryOfWidth(int count) {
    return "{" + String.join(", ", java.util.Arrays.copyOfRange(COLUMNS, 0, count)) + "}";
  }

  /** {@code maxStack} of every method in a generated class, keyed by {@code name+descriptor}. */
  private static Map<String, Integer> maxStackByMethod(byte[] bytecode) {
    final Map<String, Integer> result = new LinkedHashMap<>();
    new ClassReader(bytecode)
        .accept(
            new ClassVisitor(Opcodes.ASM9) {
              @Override
              public MethodVisitor visitMethod(
                  int access, String name, String descriptor, String signature, String[] ex) {
                return new MethodVisitor(Opcodes.ASM9) {
                  @Override
                  public void visitMaxs(int maxStack, int maxLocals) {
                    result.put(name + descriptor, maxStack);
                  }
                };
              }
            },
            0);
    return result;
  }

  /**
   * The operand stack required by the generated JSON consumer must not depend on how many columns
   * are being extracted.
   *
   * <p>Each column emits one {@code writeName} call. Under Jackson 2 those returned {@code void};
   * under Jackson 3 they return the generator, so if the generated code does not discard the
   * result, {@code maxStack} grows by exactly one per column. This test pins the invariant rather
   * than a specific depth so it stays meaningful if the surrounding codegen changes.
   */
  @Test
  public void jsonOperandStackDepthDoesNotGrowWithColumnCount() {
    final var depths = new LinkedHashMap<Integer, Integer>();
    for (var width = 1; width <= COLUMNS.length; width++) {
      final var consumerDepths =
          maxStackByMethod(generate(queryOfWidth(width), OutputFormat.JSON)).entrySet().stream()
              .filter(e -> e.getKey().startsWith("JSON Consumer"))
              .map(Map.Entry::getValue)
              .toList();
      Assertions.assertEquals(
          1, consumerDepths.size(), "expected exactly one generated JSON consumer method");
      depths.put(width, consumerDepths.get(0));
    }
    final var distinct = Set.copyOf(depths.values());
    Assertions.assertEquals(
        1,
        distinct.size(),
        () ->
            "Operand stack depth of the generated JSON consumer varies with the number of extracted"
                + " columns, which means a fluent JsonGenerator return value is being left on the"
                + " stack. Depth by column count: "
                + depths);
  }

  /**
   * Every generated extractor must pass ASM's data-flow verifier.
   *
   * <p>The olive compiler path is checked this way by {@link CompilerTest}, but the extractor path
   * had no equivalent coverage, which is why the fluent-return regression above went unnoticed.
   */
  @Test
  public void generatedExtractorsVerify() {
    for (final var outputFormat : OutputFormat.values()) {
      for (var width = 1; width <= COLUMNS.length; width++) {
        final var query = queryOfWidth(width);
        final var bytecode = generate(query, outputFormat);
        final var messages = new StringWriter();
        CheckClassAdapter.verify(new ClassReader(bytecode), false, new PrintWriter(messages, true));
        Assertions.assertTrue(
            messages.toString().isEmpty(),
            () ->
                String.format(
                    "Verification failed for %s / %s:%n%s", outputFormat, query, messages));
      }
    }
  }

  /**
   * The generated bytecode must reference only Jackson 3 types.
   *
   * <p>Jackson 3 moved everything except {@code jackson-annotations} from {@code
   * com.fasterxml.jackson} to {@code tools.jackson}. Because these class and method references are
   * built by hand as ASM descriptors, a missed rename would not be a compile error here — it would
   * be a {@code NoClassDefFoundError} the first time a user ran an extraction.
   */
  @Test
  public void generatedExtractorsReferenceOnlyJackson3() {
    for (final var outputFormat : OutputFormat.values()) {
      final var query = queryOfWidth(COLUMNS.length);
      final var disassembly = disassemble(generate(query, outputFormat));
      Assertions.assertFalse(
          disassembly.contains("com/fasterxml/jackson/core")
              || disassembly.contains("com/fasterxml/jackson/databind"),
          () ->
              String.format(
                  "Generated bytecode for %s / %s still references Jackson 2 packages",
                  outputFormat, query));
    }
  }

  /**
   * The renamed {@code writeName} must be emitted with Jackson 3's fluent return type, and its
   * result must be discarded immediately.
   *
   * <p>Emitting the old {@code ()V} descriptor would compile fine here but fail at runtime with
   * {@code NoSuchMethodError}, since the JVM resolves methods by name *and* descriptor.
   */
  @Test
  public void jsonWriteCallsDiscardTheirFluentResult() {
    final var disassembly = disassemble(generate(queryOfWidth(3), OutputFormat.JSON));
    Assertions.assertTrue(
        disassembly.contains(
            "INVOKEVIRTUAL tools/jackson/core/JsonGenerator.writeName"
                + " (Ljava/lang/String;)Ltools/jackson/core/JsonGenerator;"),
        "expected a fluent writeName call in the generated bytecode");
    Assertions.assertFalse(
        disassembly.contains("writeFieldName"),
        "writeFieldName was removed in Jackson 3 and must not be emitted");

    // Every fluent JsonGenerator write call must be followed immediately by POP.
    final var lines = disassembly.lines().map(String::strip).toList();
    for (var i = 0; i < lines.size(); i++) {
      final var line = lines.get(i);
      if (line.startsWith("INVOKEVIRTUAL tools/jackson/core/JsonGenerator.write")
          && line.endsWith(")Ltools/jackson/core/JsonGenerator;")) {
        final var next = i + 1 < lines.size() ? lines.get(i + 1) : "<end of method>";
        final var index = i;
        Assertions.assertEquals(
            "POP",
            next,
            () ->
                String.format(
                    "Fluent generator call at line %d (%s) does not discard its result; the"
                        + " returned generator is left on the operand stack.",
                    index, line));
      }
    }
  }

  /**
   * Every method, constructor and field the generated bytecode refers to must actually exist, with
   * exactly the descriptor that was emitted.
   *
   * <p>These references are hand-written ASM {@link org.objectweb.asm.commons.Method} and {@link
   * Type} constants, so javac cannot check them and neither can a data-flow verifier — the verifier
   * only checks that the operands are type-compatible with the *declared* descriptor, not that the
   * declared descriptor names a real member. A stale descriptor therefore compiles, verifies, and
   * then dies with {@link NoSuchMethodError} the first time a user runs an extraction.
   *
   * <p>That is precisely the failure mode the Jackson 3 migration could introduce: {@code
   * writeFieldName} → {@code writeName}, {@code void} → {@code JsonGenerator} returns, {@code
   * ObjectMapper} → {@code JsonMapper}, and the {@code PackStreaming} constructor's {@code
   * JsonGenerator} parameter all had to change together.
   */
  @Test
  public void generatedReferencesResolveAgainstRealClasses() {
    for (final var outputFormat : OutputFormat.values()) {
      final var query = queryOfWidth(COLUMNS.length);
      final var problems = new ArrayList<String>();
      new ClassReader(generate(query, outputFormat))
          .accept(
              new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int a, String n, String d, String s, String[] e) {
                  return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(
                        int opcode, String owner, String name, String descriptor, boolean itf) {
                      checkMethod(owner, name, descriptor, problems);
                    }

                    @Override
                    public void visitFieldInsn(
                        int opcode, String owner, String name, String descriptor) {
                      checkField(owner, name, descriptor, problems);
                    }
                  };
                }
              },
              ClassReader.SKIP_DEBUG);
      Assertions.assertTrue(
          problems.isEmpty(),
          () ->
              String.format(
                  "Generated bytecode for %s / %s refers to members that do not exist:%n  %s",
                  outputFormat, query, String.join("\n  ", problems)));
    }
  }

  /** Internal name of the class being generated; its own members are not resolvable yet. */
  private static final String SELF = "dyn/shesmu/Generated";

  private static void checkMethod(
      String owner, String name, String descriptor, List<String> problems) {
    if (owner.equals(SELF) || owner.startsWith("[")) {
      return;
    }
    final Class<?> target;
    try {
      target = Class.forName(Type.getObjectType(owner).getClassName(), false, loader());
    } catch (ClassNotFoundException e) {
      problems.add(String.format("%s.%s%s -> class not found", owner, name, descriptor));
      return;
    }
    final Class<?>[] parameters;
    final Class<?> returnType;
    try {
      parameters = toClasses(Type.getArgumentTypes(descriptor));
      returnType = toClass(Type.getReturnType(descriptor));
    } catch (ClassNotFoundException e) {
      problems.add(
          String.format(
              "%s.%s%s -> descriptor mentions missing class: %s",
              owner, name, descriptor, e.getMessage()));
      return;
    }
    if (name.equals("<init>")) {
      for (final var candidate : target.getDeclaredConstructors()) {
        if (java.util.Arrays.equals(candidate.getParameterTypes(), parameters)) {
          return;
        }
      }
      problems.add(String.format("%s.<init>%s -> no such constructor", owner, descriptor));
      return;
    }
    for (var c = target; c != null; c = c.getSuperclass()) {
      if (matches(c.getDeclaredMethods(), name, parameters, returnType)) {
        return;
      }
    }
    // interface (default/abstract) methods
    final var queue = new java.util.ArrayDeque<Class<?>>();
    queue.add(target);
    while (!queue.isEmpty()) {
      final var c = queue.poll();
      if (matches(c.getDeclaredMethods(), name, parameters, returnType)) {
        return;
      }
      if (c.getSuperclass() != null) {
        queue.add(c.getSuperclass());
      }
      queue.addAll(java.util.Arrays.asList(c.getInterfaces()));
    }
    problems.add(
        String.format(
            "%s.%s%s -> no such method (name, parameters and return type must all match)",
            owner, name, descriptor));
  }

  private static boolean matches(
      java.lang.reflect.Method[] candidates,
      String name,
      Class<?>[] parameters,
      Class<?> returnType) {
    for (final var candidate : candidates) {
      if (candidate.getName().equals(name)
          && candidate.getReturnType().equals(returnType)
          && java.util.Arrays.equals(candidate.getParameterTypes(), parameters)) {
        return true;
      }
    }
    return false;
  }

  private static void checkField(
      String owner, String name, String descriptor, List<String> problems) {
    if (owner.equals(SELF)) {
      return;
    }
    try {
      final var target = Class.forName(Type.getObjectType(owner).getClassName(), false, loader());
      final var expected = toClass(Type.getType(descriptor));
      for (var c = target; c != null; c = c.getSuperclass()) {
        for (final var field : c.getDeclaredFields()) {
          if (field.getName().equals(name)) {
            if (!field.getType().equals(expected)) {
              problems.add(
                  String.format(
                      "%s.%s -> declared as %s but emitted as %s",
                      owner, name, field.getType().getName(), expected.getName()));
            }
            return;
          }
        }
      }
      problems.add(String.format("%s.%s -> no such field", owner, name));
    } catch (ClassNotFoundException e) {
      problems.add(String.format("%s.%s -> class not found", owner, name));
    }
  }

  private static ClassLoader loader() {
    return ExtractorBytecodeTest.class.getClassLoader();
  }

  private static Class<?>[] toClasses(Type[] types) throws ClassNotFoundException {
    final var result = new Class<?>[types.length];
    for (var i = 0; i < types.length; i++) {
      result[i] = toClass(types[i]);
    }
    return result;
  }

  private static Class<?> toClass(Type type) throws ClassNotFoundException {
    return switch (type.getSort()) {
      case Type.VOID -> void.class;
      case Type.BOOLEAN -> boolean.class;
      case Type.CHAR -> char.class;
      case Type.BYTE -> byte.class;
      case Type.SHORT -> short.class;
      case Type.INT -> int.class;
      case Type.FLOAT -> float.class;
      case Type.LONG -> long.class;
      case Type.DOUBLE -> double.class;
      case Type.ARRAY -> Class.forName(type.getDescriptor().replace('/', '.'), false, loader());
      default -> Class.forName(type.getClassName(), false, loader());
    };
  }

  private static String disassemble(byte[] bytecode) {
    final var out = new StringWriter();
    new ClassReader(bytecode)
        .accept(new TraceClassVisitor(new PrintWriter(out, true)), ClassReader.SKIP_DEBUG);
    return out.toString();
  }
}
