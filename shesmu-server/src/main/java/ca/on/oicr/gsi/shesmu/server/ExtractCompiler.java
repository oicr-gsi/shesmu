package ca.on.oicr.gsi.shesmu.server;

import ca.on.oicr.gsi.shesmu.compiler.ExtractBuilder;
import ca.on.oicr.gsi.shesmu.compiler.ExtractionNode;
import ca.on.oicr.gsi.shesmu.compiler.ExtractorScriptNode;
import ca.on.oicr.gsi.shesmu.compiler.definitions.DefinitionRepository;
import ca.on.oicr.gsi.shesmu.compiler.definitions.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.InputFormatDefinition;
import ca.on.oicr.gsi.shesmu.plugin.Parser;
import ca.on.oicr.gsi.shesmu.runtime.InputProvider;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.objectweb.asm.ClassVisitor;

/** Compiles a user-specified file into a usable program and updates it as necessary */
final class ExtractCompiler extends BaseHotloadingCompiler {

  private final DefinitionRepository definitionRepository;
  private final Function<String, InputFormatDefinition> inputFormats;

  public ExtractCompiler(
      Function<String, InputFormatDefinition> inputFormats,
      DefinitionRepository definitionRepository) {
    this.inputFormats = inputFormats;
    this.definitionRepository = definitionRepository;
  }

  public Extractor compile(
      String query,
      String inputFormat,
      OutputFormat outputFormat,
      Map<String, String> preparedColumns) {
    final var inputFormatDefinition = inputFormats.apply(inputFormat);
    if (inputFormatDefinition == null) {
      return new Extractor() {
        @Override
        public void run(InputProvider input, ExtractVisitor visitor) throws IOException {
          try (final var os = new PrintStream(visitor.error())) {
            os.print("Unknown input format “");
            os.print(inputFormat);
            os.println("”.");
          }
        }
      };
    }

    final var errors = new ArrayList<String>();
    final var parsedPreparedColumns = new TreeMap<String, List<ExtractionNode>>();
    var preparedOk = true;
    for (final var entry : preparedColumns.entrySet()) {
      preparedOk =
          Parser.start(
                      entry.getValue(),
                      (line, column, errorMessage) ->
                          errors.add(
                              String.format(
                                  "[%s]%d:%d: %s", entry.getKey(), line, column, errorMessage)))
                  .whitespace()
                  .list(
                      l -> parsedPreparedColumns.put(entry.getKey(), l), ExtractionNode::parse, ',')
                  .finished()
              & preparedOk;
    }

    final var node = new AtomicReference<ExtractorScriptNode>();
    if (preparedOk
        && Parser.start(
                query,
                (line, column, errorMessage) ->
                    errors.add(String.format("%d:%d: %s", line, column, errorMessage)))
            .then((i, o) -> ExtractorScriptNode.parse(outputFormat, i, o), node::set)
            .finished()) {
      if (node.get()
          .validate(
              inputFormatDefinition,
              definitionRepository
                      .functions()
                      .collect(Collectors.toMap(FunctionDefinition::name, Function.identity()))
                  ::get,
              errors::add,
              definitionRepository::constants,
              parsedPreparedColumns)) {
        final var builder =
            new ExtractBuilder(HotloadingCompiler.TARGET_INTERNAL) {
              @Override
              protected ClassVisitor createClassVisitor() {
                return ExtractCompiler.this.createClassVisitor();
              }
            };
        node.get().render(builder);
        builder.finish();
        try {
          return load(Extractor.class, BaseHotloadingCompiler.TARGET);
        } catch (InstantiationException
            | IllegalAccessException
            | ClassNotFoundException
            | NoSuchMethodException
            | InvocationTargetException
            | VerifyError
            | BootstrapMethodError e) {
          return new Extractor() {
            @Override
            public void run(InputProvider input, ExtractVisitor visitor) throws IOException {
              try (final var os = new PrintStream(visitor.error())) {
                e.printStackTrace(os);
              }
            }
          };
        }
      }
    }

    return new Extractor() {
      @Override
      public void run(InputProvider input, ExtractVisitor visitor) throws IOException {
        try (final var os = visitor.error()) {
          for (final var error : errors) {
            os.write(error.getBytes(StandardCharsets.UTF_8));
            os.write('\n');
          }
        }
      }
    };
  }
}
