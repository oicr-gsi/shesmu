package ca.on.oicr.gsi.shesmu.server;

import ca.on.oicr.gsi.shesmu.compiler.Compiler;
import ca.on.oicr.gsi.shesmu.compiler.ExportConsumer;
import ca.on.oicr.gsi.shesmu.compiler.LiveExportConsumer;
import ca.on.oicr.gsi.shesmu.compiler.RefillerDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.ActionDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.DefinitionRepository;
import ca.on.oicr.gsi.shesmu.compiler.definitions.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.InputFormatDefinition;
import ca.on.oicr.gsi.shesmu.compiler.description.FileTable;
import ca.on.oicr.gsi.shesmu.plugin.functions.FunctionParameter;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.runtime.ActionGenerator;
import ca.on.oicr.gsi.shesmu.util.NameLoader;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.objectweb.asm.ClassVisitor;

/** Compiles a user-specified file into a usable program and updates it as necessary */
public final class HotloadingCompiler extends BaseHotloadingCompiler {

  private final DefinitionRepository definitionRepository;
  private final List<String> errors = new ArrayList<>();
  private final Function<String, InputFormatDefinition> inputFormats;

  public HotloadingCompiler(
      Function<String, InputFormatDefinition> inputFormats,
      DefinitionRepository definitionRepository) {
    this.inputFormats = inputFormats;
    this.definitionRepository = definitionRepository;
  }

  public Optional<ActionGenerator> compile(
      Path fileName, LiveExportConsumer exportConsumer, Consumer<FileTable> dashboardConsumer) {
    try {
      return compile(
          fileName.toString(),
          new String(Files.readAllBytes(fileName), StandardCharsets.UTF_8),
          exportConsumer,
          dashboardConsumer);
    } catch (IOException e) {
      e.printStackTrace();
    }
    return Optional.empty();
  }

  public Optional<ActionGenerator> compile(
      String fileName,
      String contents,
      LiveExportConsumer exportConsumer,
      Consumer<FileTable> dashboardConsumer) {
    try {
      errors.clear();
      final Compiler compiler =
          new Compiler(false) {
            private final NameLoader<ActionDefinition> actionCache =
                new NameLoader<>(definitionRepository.actions(), ActionDefinition::name);
            private final NameLoader<FunctionDefinition> functionCache =
                new NameLoader<>(definitionRepository.functions(), FunctionDefinition::name);
            private final NameLoader<RefillerDefinition> refillerCache =
                new NameLoader<>(definitionRepository.refillers(), RefillerDefinition::name);

            @Override
            protected ClassVisitor createClassVisitor() {
              return HotloadingCompiler.this.createClassVisitor();
            }

            @Override
            protected void errorHandler(String message) {
              errors.add(message);
            }

            @Override
            protected ActionDefinition getAction(String name) {
              return actionCache.get(name);
            }

            @Override
            protected FunctionDefinition getFunction(String function) {
              return functionCache.get(function);
            }

            @Override
            protected InputFormatDefinition getInputFormats(String name) {
              return inputFormats.apply(name);
            }

            @Override
            protected RefillerDefinition getRefiller(String name) {
              return refillerCache.get(name);
            }
          };

      final List<Consumer<ActionGenerator>> exports = new ArrayList<>();
      final MethodHandles.Lookup lookup = MethodHandles.lookup();
      if (compiler.compile(
          contents,
          "dyn/shesmu/Program",
          fileName,
          definitionRepository.constants().collect(Collectors.toList())::stream,
          definitionRepository.signatures().collect(Collectors.toList())::stream,
          new ExportConsumer() {
            @Override
            public void function(
                String name, Imyhat returnType, Supplier<Stream<FunctionParameter>> parameters) {
              exports.add(
                  instance -> {
                    try {
                      exportConsumer.function(
                          lookup
                              .unreflect(
                                  instance
                                      .getClass()
                                      .getMethod(
                                          name,
                                          parameters
                                              .get()
                                              .map(p -> p.type().javaType())
                                              .toArray(Class[]::new)))
                              .bindTo(instance),
                          name,
                          returnType,
                          parameters);
                    } catch (NoSuchMethodException | IllegalAccessException e) {
                      e.printStackTrace();
                    }
                  });
            }
          },
          dashboardConsumer,
          false)) {
        final ActionGenerator generator = load(ActionGenerator.class, "dyn.shesmu.Program");
        for (final Consumer<ActionGenerator> export : exports) {
          export.accept(generator);
        }
        return Optional.of(generator);
      }
    } catch (final Exception | VerifyError | BootstrapMethodError e) {
      e.printStackTrace();
    }
    return Optional.empty();
  }

  public Stream<String> errors() {
    return errors.stream();
  }
}
