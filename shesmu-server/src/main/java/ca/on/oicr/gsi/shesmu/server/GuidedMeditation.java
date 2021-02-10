package ca.on.oicr.gsi.shesmu.server;

import ca.on.oicr.gsi.shesmu.compiler.EcmaScriptRenderer;
import ca.on.oicr.gsi.shesmu.compiler.ExpressionCompilerServices;
import ca.on.oicr.gsi.shesmu.compiler.NameDefinitions;
import ca.on.oicr.gsi.shesmu.compiler.WizardDefineNode;
import ca.on.oicr.gsi.shesmu.compiler.WizardNode;
import ca.on.oicr.gsi.shesmu.compiler.definitions.DefinitionRepository;
import ca.on.oicr.gsi.shesmu.compiler.definitions.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.InputFormatDefinition;
import ca.on.oicr.gsi.shesmu.core.StandardDefinitions;
import ca.on.oicr.gsi.shesmu.plugin.Parser;
import ca.on.oicr.gsi.shesmu.plugin.Utils;
import ca.on.oicr.gsi.shesmu.plugin.files.WatchedFileListener;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import ca.on.oicr.gsi.shesmu.server.plugins.AnnotatedInputFormatDefinition;
import ca.on.oicr.gsi.shesmu.util.NameLoader;
import ca.on.oicr.gsi.status.ConfigurationSection;
import ca.on.oicr.gsi.status.SectionRenderer;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.prometheus.client.Gauge;
import io.prometheus.client.Gauge.Timer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class GuidedMeditation implements WatchedFileListener {
  public static final String EXTENSION = ".meditation";
  private static final Gauge compileTime =
      Gauge.build(
              "shesmu_meditation_compile_time",
              "The number of seconds the last meditation compilation took to perform.")
          .labelNames("filename")
          .register();
  private static final Gauge sourceValid =
      Gauge.build(
              "shesmu_meditation_valid",
              "Whether the meditation source file has been successfully compiled.")
          .labelNames("filename")
          .register();

  public static void compile(
      Path sourcePath,
      DefinitionRepository definitionRepository,
      String script,
      Consumer<List<String>> errorHandler,
      Consumer<String> outputHandler)
      throws JsonProcessingException, NoSuchAlgorithmException {
    final AtomicReference<List<WizardDefineNode>> definitions = new AtomicReference<>();
    final AtomicReference<WizardNode> wizard = new AtomicReference<>();
    if (Parser.start(
            script,
            ((line, column, errorMessage) ->
                errorHandler.accept(
                    Collections.singletonList(
                        String.format("%d:%d: %s", line, column, errorMessage)))))
        .whitespace()
        .list(definitions::set, WizardDefineNode::parse)
        .keyword("Start")
        .then(WizardNode::parse, wizard::set)
        .symbol(";")
        .whitespace()
        .finished()) {
      final List<String> errors = new ArrayList<>();
      final Map<String, WizardDefineNode> crossReferences = new TreeMap<>();
      final ExpressionCompilerServices expressionCompilerServices =
          new ExpressionCompilerServices() {
            private final NameLoader<AnnotatedInputFormatDefinition> formats =
                new NameLoader<>(
                    AnnotatedInputFormatDefinition.formats(), InputFormatDefinition::name);

            @Override
            public FunctionDefinition function(String name) {
              return null;
            }

            @Override
            public Imyhat imyhat(String name) {
              return Imyhat.BAD;
            }

            @Override
            public InputFormatDefinition inputFormat() {
              return InputFormatDefinition.DUMMY;
            }

            @Override
            public InputFormatDefinition inputFormat(String format) {
              return formats.get(format);
            }
          };
      if (definitions.get().stream()
              .allMatch(def -> def.resolveCrossReferences(crossReferences, errors::add))
          && wizard.get().resolveCrossReferences(crossReferences, errors::add)
          && definitions.get().stream()
                  .filter(
                      def ->
                          def.check(expressionCompilerServices, definitionRepository, errors::add))
                  .count()
              == definitions.get().size()
          && wizard
              .get()
              .resolveDefinitions(expressionCompilerServices, definitionRepository, errors::add)
          && wizard
              .get()
              .resolve(
                  NameDefinitions.root(
                      InputFormatDefinition.DUMMY,
                      new StandardDefinitions().constants(),
                      Stream.empty()),
                  errors::add)
          && wizard.get().typeCheck(errors::add)) {
        outputHandler.accept(
            EcmaScriptRenderer.root(
                sourcePath.toString(),
                Utils.bytesToHex(
                    MessageDigest.getInstance("SHA-1")
                        .digest(script.getBytes(StandardCharsets.UTF_8))),
                renderer -> {
                  for (final WizardDefineNode definition : definitions.get()) {
                    definition.render(renderer);
                  }
                  renderer.statement(
                      String.format(
                          "return %s",
                          wizard.get().renderEcma(renderer, baseLoader -> Stream.empty())));
                }));
      } else {
        errorHandler.accept(errors);
      }
    }
  }

  private final DefinitionRepository definitionRepository;
  private List<String> errors = Collections.emptyList();
  private final Path fileName;
  private Optional<String> script = Optional.empty();

  public GuidedMeditation(Path fileName, DefinitionRepository definitionRepository) {
    this.fileName = fileName;
    this.definitionRepository = definitionRepository;
  }

  public ConfigurationSection configuration() {
    return new ConfigurationSection(fileName.toString()) {
      @Override
      public void emit(SectionRenderer sectionRenderer) {
        sectionRenderer.line("Is Good", script.isPresent() ? "Yes" : "No");
        for (final String error : errors) {
          sectionRenderer.line("Error", error);
        }
      }
    };
  }

  public Path filename() {
    return fileName;
  }

  @Override
  public void start() {
    update();
  }

  @Override
  public void stop() {
    script = Optional.empty();
  }

  public Stream<String> stream() {
    return script.map(Stream::of).orElseGet(Stream::empty);
  }

  @Override
  public Optional<Integer> update() {
    try (Timer ignored = compileTime.labels(fileName.toString()).startTimer()) {
      final String name =
          RuntimeSupport.MAPPER.writeValueAsString(
              RuntimeSupport.removeExtension(fileName, GuidedMeditation.EXTENSION));
      compile(
          fileName,
          definitionRepository,
          new String(Files.readAllBytes(fileName), StandardCharsets.UTF_8),
          e -> errors = e,
          o ->
              script =
                  Optional.of(
                      String.format(
                          "register(%s, function($runtime) {%s}(runtime));\n\n", name, o)));
    } catch (IOException | NoSuchAlgorithmException e) {
      sourceValid.labels(fileName.toString()).set(0);
      errors = Collections.singletonList(e.getMessage());
    }

    return Optional.empty();
  }
}
