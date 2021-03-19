package ca.on.oicr.gsi.shesmu.compiler;

import static ca.on.oicr.gsi.shesmu.compiler.BaseOliveBuilder.A_STREAM_TYPE;

import ca.on.oicr.gsi.shesmu.compiler.definitions.ActionDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.ConstantDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.InputFormatDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.SignatureDefinition;
import ca.on.oicr.gsi.shesmu.compiler.description.FileTable;
import ca.on.oicr.gsi.shesmu.plugin.Utils;
import ca.on.oicr.gsi.shesmu.runtime.ActionGenerator;
import ca.on.oicr.gsi.shesmu.runtime.OliveServices;
import ca.on.oicr.gsi.shesmu.server.plugins.PluginManager;
import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceClassVisitor;

/** A shell of a compiler that can output bytecode */
public abstract class Compiler {

  private static final Type A_ARRAYS_TYPE = Type.getType(Arrays.class);

  private static final Type A_OLIVE_SERVICES = Type.getType(OliveServices.class);
  private static final Method METHOD_ARRAYS__STREAM =
      new Method("stream", A_STREAM_TYPE, new Type[] {Type.getType(Object[].class)});
  private static final Method METHOD_OLIVE_SERVICES__IS_OVERLOADED_ARRAY =
      new Method("isOverloaded", Type.BOOLEAN_TYPE, new Type[] {Type.getType(String[].class)});
  private static final Method METHOD_SERVICES_REQUIRED =
      new Method("services", Type.getType(Stream.class), new Type[0]);

  private static final Handle SERVICES_REQURED_BSM =
      new Handle(
          Opcodes.H_INVOKESTATIC,
          Type.getInternalName(PluginManager.class),
          "bootstrapServices",
          Type.getMethodDescriptor(
              Type.getType(CallSite.class),
              Type.getType(MethodHandles.Lookup.class),
              Type.getType(String.class),
              Type.getType(MethodType.class),
              Type.getType(String[].class)),
          false);

  private final boolean skipRender;

  /**
   * Create a new instance of this compiler
   *
   * @param skipRender if true, no bytecode will be generated when compiler is called; only parsing
   *     and checking
   */
  public Compiler(boolean skipRender) {
    super();
    this.skipRender = skipRender;
  }

  /**
   * Compile a program
   *
   * @param input the bytes in the script
   * @param name the internal name of the class to generate; it will extend {@link ActionGenerator}
   * @param path the source file's path for debugging information
   * @param exportConsumer a callback to handle the exported functions from this program
   * @return whether compilation was successful
   */
  public final boolean compile(
      byte[] input,
      String name,
      String path,
      Supplier<Stream<ConstantDefinition>> constants,
      Supplier<Stream<SignatureDefinition>> signatures,
      ExportConsumer exportConsumer,
      Consumer<FileTable> dashboardOutput) {
    return compile(
        new String(input, StandardCharsets.UTF_8),
        name,
        path,
        constants,
        signatures,
        exportConsumer,
        dashboardOutput,
        false,
        false);
  }
  /**
   * Compile a program
   *
   * @param input the bytes in the script
   * @param name the internal name of the class to generate; it will extend {@link ActionGenerator}
   * @param path the source file's path for debugging information
   * @param exportConsumer a callback to handle the exported functions from this program
   * @param allowDuplicates allow redefinition of known functions (useful during checking)
   * @return whether compilation was successful
   */
  public final boolean compile(
      String input,
      String name,
      String path,
      Supplier<Stream<ConstantDefinition>> constants,
      Supplier<Stream<SignatureDefinition>> signatures,
      ExportConsumer exportConsumer,
      Consumer<FileTable> dashboardOutput,
      boolean allowDuplicates,
      boolean allowUnused) {
    final var program = new AtomicReference<ProgramNode>();
    final String hash;
    try {
      final var digest = MessageDigest.getInstance("SHA-1");
      hash = Utils.bytesToHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
    final var parseOk =
        ProgramNode.parseFile(
            input,
            program::set,
            (line, column, message) ->
                errorHandler(String.format("%d:%d: %s", line, column, message)));
    if (parseOk
        && program
            .get()
            .validate(
                this::getInputFormats,
                this::getFunction,
                this::getAction,
                this::getRefiller,
                this::getOliveDefinition,
                this::errorHandler,
                constants,
                signatures,
                allowDuplicates,
                allowUnused)) {
      final var compileTime = Instant.now().truncatedTo(ChronoUnit.MILLIS);
      if (dashboardOutput != null && skipRender) {
        dashboardOutput.accept(program.get().dashboard(path, hash, "Bytecode not available."));
      }
      if (skipRender) {
        return true;
      }
      final List<Textifier> bytecode = new ArrayList<>();
      final var builder =
          new RootBuilder(
              hash,
              name,
              path,
              program.get().inputFormatDefinition(),
              program.get().timeout(),
              constants,
              signatures) {
            @Override
            protected ClassVisitor createClassVisitor() {
              final var outputVisitor = Compiler.this.createClassVisitor();
              if (dashboardOutput == null) {
                return outputVisitor;
              }
              final var writer = new Textifier();
              bytecode.add(writer);
              return new TraceClassVisitor(outputVisitor, writer, null);
            }
          };
      final Set<Path> pluginFilenames = new TreeSet<>();
      program.get().collectPlugins(pluginFilenames);
      pluginFilenames.remove(Paths.get(path));
      builder.addGuard(
          methodGen -> {
            methodGen.loadArg(0);
            methodGen.invokeDynamic(
                "services",
                Type.getMethodDescriptor(Type.getType(String[].class)),
                SERVICES_REQURED_BSM,
                pluginFilenames.stream().map(Path::toString).toArray());
            methodGen.invokeInterface(A_OLIVE_SERVICES, METHOD_OLIVE_SERVICES__IS_OVERLOADED_ARRAY);
          });
      final var servicesMethodGen =
          new GeneratorAdapter(
              Opcodes.ACC_PUBLIC, METHOD_SERVICES_REQUIRED, null, null, builder.classVisitor);
      servicesMethodGen.visitCode();
      servicesMethodGen.invokeDynamic(
          "services",
          Type.getMethodDescriptor(Type.getType(String[].class)),
          SERVICES_REQURED_BSM,
          pluginFilenames.stream().map(Path::toString).toArray());
      servicesMethodGen.invokeStatic(A_ARRAYS_TYPE, METHOD_ARRAYS__STREAM);
      servicesMethodGen.returnValue();
      servicesMethodGen.visitMaxs(0, 0);
      servicesMethodGen.visitEnd();
      program.get().render(builder, this::getOliveDefinitionRenderer);
      builder.finish();
      if (exportConsumer != null) {
        program.get().processExports(exportConsumer);
      }
      if (dashboardOutput != null) {
        dashboardOutput.accept(
            program
                .get()
                .dashboard(
                    path,
                    hash,
                    bytecode.stream()
                        .flatMap(t -> t.getText().stream())
                        .flatMap(
                            new Function<Object, Stream<String>>() {

                              @Override
                              public Stream<String> apply(Object object) {
                                if (object instanceof List) {
                                  return ((List<?>) object).stream().flatMap(this);
                                }
                                return Stream.of(object.toString());
                              }
                            })
                        .collect(Collectors.joining())));
      }
      return true;
    }
    return false;
  }

  /** Create a new class visitor for bytecode generation. */
  protected abstract ClassVisitor createClassVisitor();

  /** Report an error to the user. */
  protected abstract void errorHandler(String message);

  /**
   * Get an action by name.
   *
   * @param name the name of the action
   * @return the action definition, or null if no action is available
   */
  protected abstract ActionDefinition getAction(String name);

  /**
   * Get a function by name.
   *
   * @param name the name of the function
   * @return the function or null if no function is available
   */
  protected abstract FunctionDefinition getFunction(String name);

  /**
   * Get a format by name as specified by the “Input” statement at the start of the source file.
   *
   * @param name the name of the input format
   * @return the format definition, or null if no format is available
   */
  protected abstract InputFormatDefinition getInputFormats(String name);

  /**
   * Get a Define olive by name.
   *
   * @param name the name of the olive
   * @return the olive or null if no function is available
   */
  protected abstract CallableDefinition getOliveDefinition(String name);

  protected abstract CallableDefinitionRenderer getOliveDefinitionRenderer(String name);

  /**
   * Get a refiller by name.
   *
   * @param name the name of the refiller
   * @return the refiller definition, or null if no refiller is available
   */
  protected abstract RefillerDefinition getRefiller(String name);
}
