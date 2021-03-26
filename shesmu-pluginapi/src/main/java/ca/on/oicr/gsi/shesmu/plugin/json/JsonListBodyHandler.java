package ca.on.oicr.gsi.shesmu.plugin.json;

import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.http.HttpResponse;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/** Read a JSON array response from an HTTP connection and decode it via Jackson into a stream */
public final class JsonListBodyHandler<W> implements HttpResponse.BodyHandler<Supplier<Stream<W>>> {
  private static <W> HttpResponse.BodySubscriber<Supplier<Stream<W>>> asJSON(
      ObjectMapper objectMapper, JavaType targetType) {
    HttpResponse.BodySubscriber<InputStream> upstream =
        HttpResponse.BodySubscribers.ofInputStream();

    return HttpResponse.BodySubscribers.mapping(
        upstream, inputStream -> toSupplierOfType(objectMapper, inputStream, targetType));
  }

  private static <W> Supplier<Stream<W>> toSupplierOfType(
      ObjectMapper objectMapper, InputStream inputStream, JavaType targetType) {
    return () -> {
      try {
        final var parser = objectMapper.createParser(inputStream);
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
                    try {
                      if (parser.nextToken() == JsonToken.END_ARRAY) {
                        parser.close();
                        return false;
                      } else {
                        consumer.accept(objectMapper.readValue(parser, targetType));
                        return true;
                      }
                    } catch (IOException e) {
                      throw new UncheckedIOException(e);
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
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    };
  }

  private final ObjectMapper mapper;
  private final JavaType targetType;

  public JsonListBodyHandler(ObjectMapper mapper, Class<W> targetType) {
    this.mapper = mapper;
    this.targetType = mapper.getTypeFactory().constructType(targetType);
  }

  public JsonListBodyHandler(ObjectMapper mapper, TypeReference<W> targetType) {
    this.mapper = mapper;
    this.targetType = mapper.getTypeFactory().constructType(targetType);
  }

  @Override
  public HttpResponse.BodySubscriber<Supplier<Stream<W>>> apply(
      HttpResponse.ResponseInfo responseInfo) {
    return asJSON(mapper, targetType);
  }
}
