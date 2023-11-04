package ca.on.oicr.gsi.shesmu.server;

import ca.on.oicr.gsi.shesmu.compiler.definitions.DefinitionRepository;
import ca.on.oicr.gsi.shesmu.compiler.definitions.InputFormatDefinition;
import ca.on.oicr.gsi.shesmu.runtime.CompiledGenerator;
import ca.on.oicr.gsi.shesmu.runtime.InputProvider;
import ca.on.oicr.gsi.shesmu.server.Extractor.ExtractVisitor;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Stream;

public final class ExtractRequest {
  private String inputFormat;
  private OutputFormat outputFormat;
  private Map<String, String> preparedColumns;
  private String query;
  private boolean readStale;

  public String getInputFormat() {
    return inputFormat;
  }

  public OutputFormat getOutputFormat() {
    return outputFormat;
  }

  public Map<String, String> getPreparedColumns() {
    return preparedColumns;
  }

  public String getQuery() {
    return query;
  }

  public void run(
      DefinitionRepository definitionRepository, InputSource inputSource, HttpExchange http)
      throws IOException {
    run(
        definitionRepository,
        CompiledGenerator.SOURCES::get,
        inputSource,
        new ExtractVisitor() {
          @Override
          public OutputStream error() throws IOException {
            http.getResponseHeaders().set("Content-type", "text/plain");
            http.sendResponseHeaders(500, 0);
            return http.getResponseBody();
          }

          @Override
          public OutputStream success(String mimeType) throws IOException {
            http.getResponseHeaders().set("Content-type", mimeType);
            http.sendResponseHeaders(200, 0);
            return http.getResponseBody();
          }
        });
  }

  public void run(
      DefinitionRepository definitionRepository,
      Function<String, InputFormatDefinition> inputFormats,
      InputSource inputSource,
      ExtractVisitor visitor)
      throws IOException {
    final var compiler = new ExtractCompiler(inputFormats, definitionRepository);
    compiler
        .compile(
            query, inputFormat, outputFormat, preparedColumns == null ? Map.of() : preparedColumns)
        .run(
            new InputProvider() {
              private final Map<String, List<Object>> cache = new TreeMap<>();

              @Override
              public Stream<Object> fetch(String format) {
                if (cache.containsKey(format)) {
                  return cache.get(format).stream();
                } else {
                  final var result = inputSource.fetch(format, readStale).toList();
                  cache.put(format, result);
                  return result.stream();
                }
              }
            },
            visitor);
  }

  public void setInputFormat(String inputFormat) {
    this.inputFormat = inputFormat;
  }

  public void setOutputFormat(OutputFormat outputFormat) {
    this.outputFormat = outputFormat;
  }

  public void setPreparedColumns(Map<String, String> preparedColumns) {
    this.preparedColumns = preparedColumns;
  }

  public void setQuery(String query) {
    this.query = query;
  }

  public void setReadStale(boolean readStale) {
    this.readStale = readStale;
  }
}
