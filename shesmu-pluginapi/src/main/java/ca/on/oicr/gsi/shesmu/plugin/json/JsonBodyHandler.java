package ca.on.oicr.gsi.shesmu.plugin.json;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.http.HttpResponse;
import java.util.function.Supplier;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

/**
 * Read a JSON response from an HTTP connection and decode it via Jackson
 *
 * <p>https://stackoverflow.com/questions/57629401/deserializing-json-using-java-11-httpclient-and-custom-bodyhandler-with-jackson
 */
public final class JsonBodyHandler<W> implements HttpResponse.BodyHandler<Supplier<W>> {
  private static <W> HttpResponse.BodySubscriber<Supplier<W>> asJSON(
      JsonMapper jsonMapper, JavaType targetType) {
    HttpResponse.BodySubscriber<InputStream> upstream =
        HttpResponse.BodySubscribers.ofInputStream();

    return HttpResponse.BodySubscribers.mapping(
        upstream, inputStream -> toSupplierOfType(jsonMapper, inputStream, targetType));
  }

  private static <W> Supplier<W> toSupplierOfType(
      JsonMapper jsonMapper, InputStream inputStream, JavaType targetType) {
    return () -> {
      try (final var stream = inputStream) {
        return jsonMapper.readValue(stream, targetType);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    };
  }

  private final JsonMapper mapper;
  private final JavaType targetType;

  public JsonBodyHandler(JsonMapper mapper, Class<W> targetType) {
    this.mapper = mapper;
    this.targetType = mapper.getTypeFactory().constructType(targetType);
  }

  public JsonBodyHandler(JsonMapper mapper, TypeReference<W> targetType) {
    this.mapper = mapper;
    this.targetType = mapper.getTypeFactory().constructType(targetType);
  }

  @Override
  public HttpResponse.BodySubscriber<Supplier<W>> apply(HttpResponse.ResponseInfo responseInfo) {
    return asJSON(mapper, targetType);
  }
}
