package ca.on.oicr.gsi.shesmu.plugin.json;

import java.io.InputStream;
import java.net.http.HttpResponse;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import tools.jackson.core.JsonToken;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

/** Read a JSON array response from an HTTP connection and decode it via Jackson into a stream */
public final class JsonListBodyHandler<W> implements HttpResponse.BodyHandler<Supplier<Stream<W>>> {
  private static <W> HttpResponse.BodySubscriber<Supplier<Stream<W>>> asJSON(
      JsonMapper jsonMapper, JavaType targetType) {
    HttpResponse.BodySubscriber<InputStream> upstream =
        HttpResponse.BodySubscribers.ofInputStream();

    return HttpResponse.BodySubscribers.mapping(
        upstream, inputStream -> toSupplierOfType(jsonMapper, inputStream, targetType));
  }

  private static <W> Supplier<Stream<W>> toSupplierOfType(
      JsonMapper jsonMapper, InputStream inputStream, JavaType targetType) {
    // disable the FAIL_ON_TRAILING_TOKENS feature here, not on the global mapper
    final var itemReader =
        jsonMapper.readerFor(targetType).without(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    return () -> {
      final var parser = jsonMapper.createParser(inputStream);
      switch (parser.nextToken()) {
        case START_ARRAY:
          return StreamSupport.stream(
              new Spliterator<W>() {
                @Override
                public int characteristics() {
                  return Spliterator.ORDERED;
                }

                @Override
                public long estimateSize() {
                  return Long.MAX_VALUE;
                }

                @Override
                public boolean tryAdvance(Consumer<? super W> consumer) {
                  if (parser.isClosed()) {
                    return false;
                  }
                  if (parser.nextToken() == JsonToken.END_ARRAY) {
                    parser.close();
                    return false;
                  } else {
                    consumer.accept(itemReader.readValue(parser));
                    return true;
                  }
                }

                @Override
                public Spliterator<W> trySplit() {
                  return null;
                }
              },
              false);
        case VALUE_NULL:
          parser.close();
          return Stream.empty();
        default:
          final var error = "Unexpected JSON token: " + parser.nextToken();
          parser.close();
          throw new IllegalArgumentException(error);
      }
    };
  }

  private final JsonMapper mapper;
  private final JavaType targetType;

  public JsonListBodyHandler(JsonMapper mapper, Class<W> targetType) {
    this.mapper = mapper;
    this.targetType = mapper.getTypeFactory().constructType(targetType);
  }

  public JsonListBodyHandler(JsonMapper mapper, TypeReference<W> targetType) {
    this.mapper = mapper;
    this.targetType = mapper.getTypeFactory().constructType(targetType);
  }

  @Override
  public HttpResponse.BodySubscriber<Supplier<Stream<W>>> apply(
      HttpResponse.ResponseInfo responseInfo) {
    return asJSON(mapper, targetType);
  }
}
