package ca.on.oicr.gsi.shesmu.server;

import ca.on.oicr.gsi.shesmu.compiler.CallableDefinition;
import ca.on.oicr.gsi.shesmu.compiler.CallableDefinitionRenderer;
import ca.on.oicr.gsi.shesmu.compiler.Compiler;
import ca.on.oicr.gsi.shesmu.compiler.ExportConsumer;
import ca.on.oicr.gsi.shesmu.compiler.LiveExportConsumer;
import ca.on.oicr.gsi.shesmu.compiler.LiveExportConsumer.DefineVariableExport;
import ca.on.oicr.gsi.shesmu.compiler.RefillerDefinition;
import ca.on.oicr.gsi.shesmu.compiler.Target;
import ca.on.oicr.gsi.shesmu.compiler.definitions.ActionDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.ConstantDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.DefinitionRepository;
import ca.on.oicr.gsi.shesmu.compiler.definitions.DefinitionRepository.CallableOliveDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.InputFormatDefinition;
import ca.on.oicr.gsi.shesmu.compiler.description.FileTable;
import ca.on.oicr.gsi.shesmu.plugin.functions.FunctionParameter;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.runtime.ActionGenerator;
import ca.on.oicr.gsi.shesmu.runtime.InputProvider;
import ca.on.oicr.gsi.shesmu.runtime.OliveServices;
import ca.on.oicr.gsi.shesmu.runtime.SignatureAccessor;
import ca.on.oicr.gsi.shesmu.server.ImportVerifier.ActionVerifier;
import ca.on.oicr.gsi.shesmu.server.ImportVerifier.ConstantVerifier;
import ca.on.oicr.gsi.shesmu.server.ImportVerifier.FunctionVerifier;
import ca.on.oicr.gsi.shesmu.server.ImportVerifier.OliveDefinitionVerifier;
import ca.on.oicr.gsi.shesmu.server.ImportVerifier.RefillerVerifier;
import ca.on.oicr.gsi.shesmu.util.NameLoader;
import java.io.IOException;
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
import org.objectweb.asm.commons.GeneratorAdapter;

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
      Path fileName,
      LiveExportConsumer exportConsumer,
      Consumer<FileTable> dashboardConsumer,
      Consumer<ImportVerifier> registerImport) {
    try {
      return compile(
          fileName.toString(),
          Files.readString(fileName),
          exportConsumer,
          dashboardConsumer,
          registerImport,
          false);
    } catch (IOException e) {
      e.printStackTrace();
    }
    return Optional.empty();
  }

  public Optional<ActionGenerator> compile(
      String fileName,
      String contents,
      LiveExportConsumer exportConsumer,
      Consumer<FileTable> dashboardConsumer,
      Consumer<ImportVerifier> registerImport,
      boolean allowUnused) {
    try {
      errors.clear();
      final var compiler =
          new Compiler(false) {
            private final NameLoader<ActionDefinition> actionCache =
                new NameLoader<>(definitionRepository.actions(), ActionDefinition::name);
            private final NameLoader<CallableOliveDefinition> definitionCache =
                new NameLoader<>(
                    definitionRepository.oliveDefinitions(), CallableOliveDefinition::name);
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
              final var definition = actionCache.get(name);
              if (definition != null) {
                registerImport.accept(new ActionVerifier(definition));
              }
              return definition;
            }

            @Override
            protected FunctionDefinition getFunction(String function) {
              final var definition = functionCache.get(function);
              if (definition != null) {
                registerImport.accept(new FunctionVerifier(definition));
              }
              return definition;
            }

            @Override
            protected InputFormatDefinition getInputFormats(String name) {
              return inputFormats.apply(name);
            }

            @Override
            protected CallableDefinition getOliveDefinition(String name) {
              final var definition = definitionCache.get(name);
              if (definition != null) {
                registerImport.accept(new OliveDefinitionVerifier(definition));
              }
              return definition;
            }

            @Override
            protected CallableDefinitionRenderer getOliveDefinitionRenderer(String name) {
              return definitionCache.get(name);
            }

            @Override
            protected RefillerDefinition getRefiller(String name) {
              final var definition = refillerCache.get(name);
              if (definition != null) {
                registerImport.accept(new RefillerVerifier(definition));
              }
              return definition;
            }
          };

      final List<Consumer<ActionGenerator>> exports = new ArrayList<>();
      if (compiler.compile(
          contents,
          BaseHotloadingCompiler.TARGET_INTERNAL,
          fileName,
          definitionRepository
                  .constants()
                  .<ConstantDefinition>map(
                      c ->
                          new ConstantDefinition(
                              c.name(), c.type(), c.description(), c.filename()) {
                            @Override
                            public void load(GeneratorAdapter methodGen) {
                              c.load(methodGen);
                              registerImport.accept(new ConstantVerifier(c));
                            }

                            @Override
                            public String load() {
                              return c.load();
                            }
                          })
                  .toList()
              ::stream,
          definitionRepository.signatures().toList()::stream,
          new ExportConsumer() {
            @Override
            public void constant(String name, Imyhat type) {
              exports.add(
                  instance -> {
                    try {
                      exportConsumer.constant(
                          instance
                              .privateLookup()
                              .unreflectGetter(instance.getClass().getField(name + "$constant"))
                              .bindTo(instance),
                          name,
                          type);
                    } catch (NoSuchFieldException | IllegalAccessException e) {
                      e.printStackTrace();
                    }
                  });
            }

            @Override
            public void definition(
                String name,
                String inputFormat,
                boolean root,
                List<Imyhat> parameters,
                List<Target> outputVariables) {
              exports.add(
                  instance -> {
                    try {
                      exportConsumer.defineOlive(
                          instance
                              .privateLookup()
                              .unreflect(
                                  instance
                                      .getClass()
                                      .getMethod(
                                          String.format("Define %s", name),
                                          Stream.concat(
                                                  Stream.of(
                                                      Stream.class,
                                                      OliveServices.class,
                                                      InputProvider.class,
                                                      Optional.class,
                                                      String.class,
                                                      int.class,
                                                      int.class,
                                                      String.class,
                                                      SignatureAccessor.class),
                                                  parameters.stream().map(Imyhat::javaType))
                                              .toArray(Class[]::new)))
                              .bindTo(instance),
                          name,
                          inputFormat,
                          root,
                          parameters,
                          outputVariables.stream()
                              .map(
                                  v -> {
                                    try {
                                      return new DefineVariableExport(
                                          v.name(),
                                          v.flavour(),
                                          v.type(),
                                          instance
                                              .privateLookup()
                                              .unreflect(
                                                  instance
                                                      .getClass()
                                                      .getMethod(
                                                          String.format(
                                                              "Define %s %s", name, v.name()),
                                                          Object.class)));
                                    } catch (IllegalAccessException | NoSuchMethodException e) {
                                      throw new RuntimeException(e);
                                    }
                                  })
                              .collect(Collectors.toList()),
                          root
                              ? inputFormats
                                  .apply(inputFormat)
                                  .baseStreamVariables()
                                  .map(
                                      v -> {
                                        try {
                                          return new DefineVariableExport(
                                              v.name(),
                                              v.flavour(),
                                              v.type(),
                                              instance
                                                  .privateLookup()
                                                  .unreflect(
                                                      instance
                                                          .getClass()
                                                          .getMethod(
                                                              String.format(
                                                                  "Define %s %s Signer Check",
                                                                  name, v.name()))));
                                        } catch (IllegalAccessException | NoSuchMethodException e) {
                                          throw new RuntimeException(e);
                                        }
                                      })
                                  .collect(Collectors.toList())
                              : List.of());

                    } catch (NoSuchMethodException | IllegalAccessException e) {
                      e.printStackTrace();
                    }
                  });
            }

            @Override
            public void function(
                String name, Imyhat returnType, Supplier<Stream<FunctionParameter>> parameters) {
              exports.add(
                  instance -> {
                    try {
                      exportConsumer.function(
                          instance
                              .privateLookup()
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
          false,
          allowUnused)) {
        final var generator = load(ActionGenerator.class, BaseHotloadingCompiler.TARGET);
        for (final var export : exports) {
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
