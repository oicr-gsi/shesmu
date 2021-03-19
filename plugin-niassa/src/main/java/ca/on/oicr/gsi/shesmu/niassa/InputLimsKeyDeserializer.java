package ca.on.oicr.gsi.shesmu.niassa;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.Parser;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.plugin.wdl.WdlInputType;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Process the <tt>type</tt> property in the <tt>nissawf</tt> file ({@link WorkflowConfiguration}).
 *
 * <p>Either a string for the predefined workflows or an object of type information for the custom
 * one
 */
public class InputLimsKeyDeserializer extends JsonDeserializer<InputLimsKeyProvider> {
  static final Parser.ParseDispatch<CustomLimsEntryType> DISPATCH = new Parser.ParseDispatch<>();
  private static final Parser.ParseDispatch<Either<Imyhat, CustomLimsEntryType>> FIELD =
      new Parser.ParseDispatch<>();
  private static final Parser.ParseDispatch<CustomLimsEntryType> INNER_DISPATCH =
      new Parser.ParseDispatch<>();
  private static final Parser.ParseDispatch<CustomLimsEntryType> PAIR_DISPATCH =
      new Parser.ParseDispatch<>();
  private static final Pattern ARRAY_SUFFIX = Pattern.compile("([*+])?");
  private static final Pattern QUESTION_MARK = Pattern.compile("\\??");

  static {
    PAIR_DISPATCH.addKeyword("File", (p, o) -> p);
    PAIR_DISPATCH.addKeyword(
        "Array",
        (p, o) ->
            p.whitespace()
                .symbol("[")
                .whitespace()
                .keyword("File")
                .whitespace()
                .symbol("]")
                .whitespace()
                .regex(ARRAY_SUFFIX, m -> {}, "array suffix")
                .whitespace());

    INNER_DISPATCH.addKeyword(
        "File",
        (p, o) -> {
          final Parser result =
              p.whitespace()
                  .regex(QUESTION_MARK, m -> {}, "Missing optional indicator.")
                  .whitespace();
          o.accept(new CustomLimsEntryTypeTerminal());
          return result;
        });
    INNER_DISPATCH.addKeyword("Array", InputLimsKeyDeserializer::fileArray);
    INNER_DISPATCH.addKeyword(
        "Pair",
        (p, o) -> {
          final Parser result =
              p.whitespace()
                  .symbol("[")
                  .whitespace()
                  .dispatch(PAIR_DISPATCH, v -> {})
                  .whitespace()
                  .keyword(",")
                  .whitespace()
                  .keyword("Map")
                  .whitespace()
                  .symbol("[")
                  .whitespace()
                  .keyword("String")
                  .whitespace()
                  .symbol(",")
                  .whitespace()
                  .keyword("String")
                  .whitespace()
                  .symbol("]")
                  .whitespace()
                  .symbol("]")
                  .whitespace()
                  .regex(QUESTION_MARK, m -> {}, "Missing optional indicator.")
                  .whitespace();
          if (result.isGood()) {
            o.accept(new CustomLimsEntryTypeTerminal());
          }
          return result;
        });

    DISPATCH.addKeyword(
        "Array",
        (p, o) -> {
          final Parser fileResult = fileArray(p, o);
          if (fileResult.isGood()) {
            return fileResult;
          }
          final AtomicReference<List<Pair<String, Either<Imyhat, CustomLimsEntryType>>>> fields =
              new AtomicReference<>();
          final Parser result =
              p.whitespace()
                  .symbol("[")
                  .whitespace()
                  .keyword("WomCompositeType")
                  .whitespace()
                  .symbol("{")
                  .list(fields::set, InputLimsKeyDeserializer::structField)
                  .symbol("}")
                  .whitespace()
                  .symbol("]")
                  .whitespace();
          if (result.isGood()) {
            if (fields.get().stream().noneMatch(f -> f.second().apply(i -> true, e -> false))) {
              return result.raise("There are no keys in this struct. You need at least one.");
            }
            o.accept(new CustomLimsEntryTypeStructArray(fields.get()));
          }
          return result;
        });
    DISPATCH.addRaw("inner", (p, o) -> p.dispatch(INNER_DISPATCH, o));

    FIELD.addKeyword(
        "String",
        (p, o) -> {
          o.accept(Either.first(Imyhat.STRING));
          return p;
        });
    FIELD.addKeyword(
        "Int",
        (p, o) -> {
          o.accept(Either.first(Imyhat.INTEGER));
          return p;
        });
    FIELD.addRaw(
        "file output", (p, o) -> p.dispatch(INNER_DISPATCH, v -> o.accept(Either.second(v))));
  }

  private static Parser fileArray(Parser parser, Consumer<CustomLimsEntryType> o) {
    final Parser result =
        parser
            .whitespace()
            .symbol("[")
            .whitespace()
            .keyword("File")
            .whitespace()
            .symbol("]")
            .whitespace()
            .regex(ARRAY_SUFFIX, m -> {}, "array suffix")
            .whitespace()
            .regex(QUESTION_MARK, m -> {}, "Missing optional indicator.")
            .whitespace();
    if (result.isGood()) {
      o.accept(new CustomLimsEntryTypeTerminal());
    }
    return result;
  }

  private static Parser structField(
      Parser parser, Consumer<Pair<String, Either<Imyhat, CustomLimsEntryType>>> output) {
    final AtomicReference<String> name = new AtomicReference<>();
    final AtomicReference<Either<Imyhat, CustomLimsEntryType>> type = new AtomicReference<>();
    final Parser fieldResult =
        parser
            .whitespace()
            .identifier(name::set)
            .whitespace()
            .symbol("->")
            .whitespace()
            .dispatch(FIELD, type::set)
            .whitespace();
    if (fieldResult.isGood()) {
      output.accept(new Pair<>(name.get(), type.get()));
    }
    return fieldResult;
  }

  private InputLimsKeyProvider deserialize(JsonNode node) {
    if (node.isTextual()) {
      return InputLimsKeyType.valueOf(node.asText());
    }
    if (node.isObject()) {
      return new CustomInputLimsKeyProvider(
          WdlInputType.flatToNested(
              (ObjectNode) node,
              (name, outputType) -> {
                final AtomicReference<CustomLimsEntryType> output = new AtomicReference<>();
                final Parser parser =
                    Parser.start(
                            outputType.asText(),
                            (line, column, message) ->
                                System.err.printf("%s:%d:%d: %s\n", name, line, column, message))
                        .whitespace()
                        .dispatch(DISPATCH, output::set)
                        .whitespace();
                return parser.finished() ? Optional.of(output.get()) : Optional.empty();
              }));
    }
    throw new IllegalArgumentException("Unknown output type: " + node);
  }

  @Override
  public InputLimsKeyProvider deserialize(
      JsonParser parser, DeserializationContext deserializationContext) throws IOException {
    final ObjectCodec oc = parser.getCodec();
    final JsonNode node = oc.readTree(parser);
    return deserialize(node);
  }
}
