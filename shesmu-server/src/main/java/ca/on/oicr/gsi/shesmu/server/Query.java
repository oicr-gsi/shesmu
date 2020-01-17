package ca.on.oicr.gsi.shesmu.server;

import ca.on.oicr.gsi.shesmu.plugin.SourceLocation.SourceLoctionLinker;
import ca.on.oicr.gsi.shesmu.plugin.filter.*;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import ca.on.oicr.gsi.shesmu.server.ActionProcessor.Filter;
import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Objects;

/** Translate JSON-formatted queries into Java objects and perform the query */
public class Query {

  ActionFilter[] filters;

  long limit = 100;

  long skip = 0;

  public ActionFilter[] getFilters() {
    return filters;
  }

  public long getLimit() {
    return limit;
  }

  public long getSkip() {
    return skip;
  }

  public void perform(OutputStream output, SourceLoctionLinker linker, ActionProcessor processor)
      throws IOException {
    final Filter[] filters =
        Arrays.stream(getFilters())
            .filter(Objects::nonNull)
            .map(filterJson -> filterJson.convert(processor))
            .toArray(Filter[]::new);
    final JsonGenerator jsonOutput = new JsonFactory().createGenerator(output, JsonEncoding.UTF8);
    jsonOutput.setCodec(RuntimeSupport.MAPPER);
    jsonOutput.writeStartObject();
    jsonOutput.writeNumberField("total", processor.size(filters));
    jsonOutput.writeArrayFieldStart("results");
    processor
        .stream(linker, filters)
        .skip(Math.max(0, getSkip()))
        .limit(limit)
        .forEach(
            action -> {
              try {
                jsonOutput.writeTree(action);
              } catch (IOException e) {
                throw new RuntimeException(e);
              }
            });
    jsonOutput.writeEndArray();
    jsonOutput.writeEndObject();
    jsonOutput.close();
  }

  public void setFilters(ActionFilter[] filters) {
    this.filters = filters;
  }

  public void setLimit(long limit) {
    this.limit = limit;
  }

  public void setSkip(long skip) {
    this.skip = skip;
  }
}
