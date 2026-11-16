package ca.on.oicr.gsi.shesmu;

import ca.on.oicr.gsi.shesmu.compiler.LiveExportConsumer;
import ca.on.oicr.gsi.shesmu.compiler.description.FileTable;
import ca.on.oicr.gsi.shesmu.compiler.description.OliveTable;
import ca.on.oicr.gsi.shesmu.core.StandardDefinitions;
import ca.on.oicr.gsi.shesmu.plugin.functions.FunctionParameter;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.server.HotloadingCompiler;
import ca.on.oicr.gsi.shesmu.server.MetroDiagram;
import java.io.IOException;
import java.io.StringWriter;
import java.lang.invoke.MethodHandle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** Compile each script in the metro directory and check that its olives' diagrams can be drawn. */
public class MetroDiagramTest {

  private static final LiveExportConsumer IGNORE_EXPORTS =
      new LiveExportConsumer() {
        @Override
        public void constant(MethodHandle method, String name, Imyhat type) {
          // Do nothing
        }

        @Override
        public void defineOlive(
            MethodHandle inputsHandle,
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
      };

  @Test
  public void testDiagrams() throws IOException {
    final List<Path> scripts;
    try (Stream<Path> files =
        Files.walk(Paths.get(this.getClass().getResource("/metro").getPath()), 1)) {
      scripts =
          files
              .filter(Files::isRegularFile)
              .filter(f -> f.getFileName().toString().endsWith(".shesmu"))
              .sorted(Comparator.comparing(Path::getFileName))
              .collect(Collectors.toList());
    }
    Assertions.assertFalse(scripts.isEmpty(), "No metro diagram test scripts found");
    for (final Path script : scripts) {
      checkFile(script);
    }
  }

  private void checkFile(Path script) {
    final AtomicReference<FileTable> dashboard = new AtomicReference<>();
    final HotloadingCompiler compiler =
        new HotloadingCompiler(RunTest.INPUT_FORMATS::get, new StandardDefinitions());
    compiler.compile(script, IGNORE_EXPORTS, dashboard::set, importVerifier -> {});
    final List<String> errors = compiler.errors().collect(Collectors.toList());
    Assertions.assertEquals(List.of(), errors, "Compilation errors in " + script.getFileName());
    final FileTable table = dashboard.get();
    Assertions.assertNotNull(table, "No dashboard produced for " + script.getFileName());
    final List<OliveTable> olives = table.olives().collect(Collectors.toList());
    Assertions.assertFalse(olives.isEmpty(), "No olives in " + script.getFileName());
    for (final OliveTable olive : olives) {
      Assertions.assertDoesNotThrow(
          () -> {
            final XMLStreamWriter writer =
                XMLOutputFactory.newFactory().createXMLStreamWriter(new StringWriter());
            writer.writeStartDocument("utf-8", "1.0");
            MetroDiagram.draw(
                writer,
                (localFilePath, line, column, hash) -> Stream.empty(),
                table.filename(),
                table.hash(),
                olive,
                null,
                table.format(),
                (filename, line, column, hash, oliveFilename, oliveLine, oliveColumn, oliveHash) ->
                    null);
            writer.writeEndDocument();
          },
          String.format(
              "Drawing metro diagram for %s:%d:%d",
              script.getFileName(), olive.line(), olive.column()));
    }
  }
}
