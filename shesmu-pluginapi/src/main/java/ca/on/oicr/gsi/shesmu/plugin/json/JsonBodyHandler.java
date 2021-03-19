package ca.on.oicr.gsi.shesmu.plugin.json;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.http.HttpResponse;
import java.util.function.Supplier;

/**
 * Read a JSON response from an HTTP connection and decode it via Jackson
 *
 * <p>https://stackoverflow.com/questions/57629401/deserializing-json-using-java-11-httpclient-and-custom-bodyhandler-with-jackson
 */
public final class JsonBodyHandler<W> implements HttpResponse.BodyHandler<Supplier<W>> {
  private static <W> HttpResponse.BodySubscriber<Supplier<W>> asJSON(
      ObjectMapper objectMapper, JavaType targetType) {
    HttpResponse.BodySubscriber<InputStream> upstream =
        HttpResponse.BodySubscribers.ofInputStream();

    return HttpResponse.BodySubscribers.mapping(
        upstream, inputStream -> toSupplierOfType(objectMapper, inputStream, targetType));
  }

  private static <W> Supplier<W> toSupplierOfType(
      ObjectMapper objectMapper, InputStream inputStream, JavaType targetType) {
    return () -> {
      try (final var stream = inputStream) {
        return objectMapper.readValue(stream, targetType);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    };
  }

  private final ObjectMapper mapper;
  private final JavaType targetType;

  public JsonBodyHandler(ObjectMapper mapper, Class<W> targetType) {
    this.mapper = mapper;
    this.targetType = mapper.getTypeFactory().constructType(targetType);
  }

  public JsonBodyHandler(ObjectMapper mapper, TypeReference<W> targetType) {
    this.mapper = mapper;
    this.targetType = mapper.getTypeFactory().constructType(targetType);
  }

  @Override
  public HttpResponse.BodySubscriber<Supplier<W>> apply(HttpResponse.ResponseInfo responseInfo) {
    return asJSON(mapper, targetType);
  }
}
