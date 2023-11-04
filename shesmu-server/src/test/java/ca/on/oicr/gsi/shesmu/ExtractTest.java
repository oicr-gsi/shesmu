package ca.on.oicr.gsi.shesmu;

import ca.on.oicr.gsi.shesmu.core.StandardDefinitions;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import ca.on.oicr.gsi.shesmu.server.ExtractRequest;
import ca.on.oicr.gsi.shesmu.server.Extractor.ExtractVisitor;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ExtractTest {

  @Test
  public void testData() throws IOException {
    System.err.println("Testing extraction code");
    try (var files =
        Files.walk(Paths.get(this.getClass().getResource("/extraction").getPath()), 1)) {
      Assertions.assertTrue(
          files
                  .filter(Files::isRegularFile)
                  .filter(f -> f.getFileName().toString().endsWith(".request"))
                  .sorted(Comparator.comparing(Path::getFileName))
                  .filter(this::testFile)
                  .count()
              == 0,
          "Sample requests failed to run!");
    }
  }

  private boolean testFile(Path file) {
    try {
      final var output = new ByteArrayOutputStream();
      try {
        RuntimeSupport.MAPPER
            .readValue(file.toFile(), ExtractRequest.class)
            .run(
                new StandardDefinitions(),
                RunTest.INPUT_FORMATS::get,
                (format, readStale) -> "test".equals(format) ? Stream.of(RunTest.TEST_DATA) : null,
                new ExtractVisitor() {
                  @Override
                  public OutputStream error() throws IOException {
                    output.write("Error\n".getBytes(StandardCharsets.UTF_8));
                    return output;
                  }

                  @Override
                  public OutputStream success(String mimeType) throws IOException {
                    output.write(mimeType.getBytes(StandardCharsets.UTF_8));
                    output.write('\n');
                    return output;
                  }
                });
      } catch (JsonProcessingException e) {
        output.write(e.getOriginalMessage().getBytes(StandardCharsets.UTF_8));
      }
      final var correct =
          Files.readAllLines(
              file.resolveSibling(file.getFileName().toString().replace(".request", ".response")),
              StandardCharsets.UTF_8);
      final var outputLines = output.toString(StandardCharsets.UTF_8).lines().toList();
      if (correct.equals(outputLines)) {
        System.err.printf("OK %s\n", file.getFileName());
        return false;
      } else {
        System.err.println("EXPECTED:");
        for (final var line : correct) System.err.println(line);
        System.err.println("GOT:");
        for (final var line : outputLines) System.err.println(line);
        System.err.printf("\n\nFAIL %s\n", file.getFileName());
        return true;
      }
    } catch (Exception
        | VerifyError
        | BootstrapMethodError
        | IncompatibleClassChangeError
        | StackOverflowError e) {
      System.err.printf("EXCP %s\n", file.getFileName());
      e.printStackTrace();
      return true;
    }
  }
}
