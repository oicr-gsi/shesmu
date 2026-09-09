package ca.on.oicr.gsi.shesmu.plugin.json;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpResponse;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectReader;
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

  /** Read the offset of a parser, which is only ever wanted for an error message */
  private static long byteOffsetOf(JsonParser parser) {
    try {
      return parser.currentLocation().getByteOffset();
    } catch (RuntimeException e) {
      return -1;
    }
  }

  /**
   * Prepare to decode a JSON array from a stream of bytes
   *
   * <p>Nothing is read until the supplier is asked for the stream: the mapping function this is
   * called from runs on the HTTP client's executor, which must not be blocked reading a body.
   *
   * <p>The stream the supplier returns must be closed; doing so closes <code>inputStream</code>.
   */
  static <W> Supplier<Stream<W>> toSupplierOfType(
      JsonMapper jsonMapper, InputStream inputStream, JavaType targetType) {
    return () -> {
      // disable the FAIL_ON_TRAILING_TOKENS feature here, not on the global mapper
      final ObjectReader itemReader =
          jsonMapper.readerFor(targetType).without(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
      // Creating the parser reads from the body to determine the encoding, so it fails outright on
      // a response that has already been cut short. Nothing owns the body at that point, so it has
      // to be released here or the connection is leaked.
      final JsonParser parser;
      try {
        parser = jsonMapper.createParser(inputStream);
      } catch (RuntimeException | Error e) {
        try {
          inputStream.close();
        } catch (IOException | RuntimeException suppressed) {
          e.addSuppressed(suppressed);
        }
        throw e;
      }
      // Closing the parser closes the HTTP response body, and therefore releases the connection, so
      // ownership has to be handed to the stream if one is returned and the parser closed here
      // otherwise
      boolean releaseParser = true;
      try {
        final JsonToken firstToken;
        try {
          firstToken = parser.nextToken();
        } catch (StreamReadException e) {
          // An error page from a proxy is a common enough response that "this was not JSON at all"
          // is worth saying plainly, rather than leaving a complaint about a stray '<' to be read
          // as if the server had sent broken JSON. Only a parse failure is treated this way; a
          // body that dies mid-read throws JacksonIOException and reports itself as such.
          throw new IllegalArgumentException(
              String.format(
                  "Expected a JSON array of %s but the response was not JSON",
                  targetType.getRawClass().getSimpleName()),
              e);
        }
        if (firstToken == JsonToken.START_ARRAY) {
          releaseParser = false;
          return StreamSupport.stream(
                  new Spliterator<W>() {
                    private long records;

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
                      final W item;
                      try {
                        if (parser.nextToken() == JsonToken.END_ARRAY) {
                          parser.close();
                          return false;
                        }
                        item = itemReader.readValue(parser);
                      } catch (RuntimeException e) {
                        // Only RuntimeException; wrapping an Error would turn it into something the
                        // cache machinery catches and reports as a mere failed refresh. An Error
                        // still releases the body, since the stream's close handler does that.
                        // Report how far the array got before dying, since a response that is cut
                        // short otherwise gives no indication of where, or of how much data arrived
                        final JsonListReadException failure =
                            new JsonListReadException(targetType, records, byteOffsetOf(parser), e);
                        try {
                          parser.close();
                        } catch (RuntimeException suppressed) {
                          failure.addSuppressed(suppressed);
                        }
                        throw failure;
                      }
                      records++;
                      // Deliberately outside the try; an exception from downstream in the stream
                      // pipeline has nothing to do with reading JSON
                      consumer.accept(item);
                      return true;
                    }

                    @Override
                    public Spliterator<W> trySplit() {
                      return null;
                    }
                  },
                  false)
              .onClose(parser::close);
        }
        if (firstToken == JsonToken.VALUE_NULL) {
          return Stream.empty();
        }
        throw new IllegalArgumentException(
            String.format(
                "Expected a JSON array of %s but found %s",
                targetType.getRawClass().getSimpleName(),
                firstToken == null ? "an empty response body" : firstToken.toString()));
      } finally {
        if (releaseParser) {
          parser.close();
        }
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
