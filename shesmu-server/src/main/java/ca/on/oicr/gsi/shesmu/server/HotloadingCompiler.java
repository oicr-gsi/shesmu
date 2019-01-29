package ca.on.oicr.gsi.shesmu.server;

import ca.on.oicr.gsi.shesmu.compiler.Compiler;
import ca.on.oicr.gsi.shesmu.compiler.definitions.ActionDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.DefinitionRepository;
import ca.on.oicr.gsi.shesmu.compiler.definitions.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.InputFormatDefinition;
import ca.on.oicr.gsi.shesmu.compiler.description.FileTable;
import ca.on.oicr.gsi.shesmu.runtime.ActionGenerator;
import ca.on.oicr.gsi.shesmu.util.NameLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.objectweb.asm.ClassVisitor;

/** Compiles a user-specified file into a usable program and updates it as necessary */
public final class HotloadingCompiler extends BaseHotloadingCompiler {

  private final List<String> errors = new ArrayList<>();

  private final Function<String, InputFormatDefinition> inputFormats;

  private final DefinitionRepository definitionRepository;

  public HotloadingCompiler(
      Function<String, InputFormatDefinition> inputFormats,
      DefinitionRepository definitionRepository) {
    this.inputFormats = inputFormats;
    this.definitionRepository = definitionRepository;
  }

  public Optional<ActionGenerator> compile(Path fileName, Consumer<FileTable> dashboardConsumer) {
    try {
      errors.clear();
      final Compiler compiler =
          new Compiler(false) {
            private final NameLoader<ActionDefinition> actionCache =
                new NameLoader<>(definitionRepository.actions(), ActionDefinition::name);
            private final NameLoader<FunctionDefinition> functionCache =
                new NameLoader<>(definitionRepository.functions(), FunctionDefinition::name);

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
          };

      if (compiler.compile(
          Files.readAllBytes(fileName),
          "dyn/shesmu/Program",
          fileName.toString(),
          definitionRepository.constants().collect(Collectors.toList())::stream,
          definitionRepository.signatures().collect(Collectors.toList())::stream,
          dashboardConsumer)) {
        return Optional.of(load(ActionGenerator.class, "dyn.shesmu.Program"));
      }
    } catch (final Exception e) {
      e.printStackTrace();
    }
    return Optional.empty();
  }

  public Stream<String> errors() {
    return errors.stream();
  }
}
