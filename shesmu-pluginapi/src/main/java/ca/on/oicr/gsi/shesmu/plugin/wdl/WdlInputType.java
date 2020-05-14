package ca.on.oicr.gsi.shesmu.plugin.wdl;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.ErrorConsumer;
import ca.on.oicr.gsi.shesmu.plugin.Parser;
import ca.on.oicr.gsi.shesmu.plugin.Parser.ParseDispatch;
import ca.on.oicr.gsi.shesmu.plugin.Parser.Rule;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.plugin.types.ImyhatTransformer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Translate WDL types to their Shesmu equivalents
 *
 * <p>The base types (Boolean, String, Int, File) are trivially matched.
 *
 * <p>WDL arrays are mapped to Shesmu lists. WDL arrays can be marked as non-empty; this information
 * is parsed, but discarded.
 *
 * <p>Pairs are translated to tuples. Right-nested pairs (<i>e.g.</i>, <tt>Pair[X, Pair[Y, Z]]</tt>)
 * are flattened into longer tuples (<i>e.g.</i>, <tt>{X, Y, Z}</tt>).
 *
 * <p>Optional types are parsed, but stripped off.
 *
 * <p>All other types are errors.
 */
public final class WdlInputType {
  /**
   * Take a JSON object of the <tt>WORKFLOW.TASK.VARIABLE</tt> format and transform it to a nestable
   * one. This processes the values as requested.
   *
   * @param inputs the input JSON object
   * @param process the method to transform the values
   * @param <T> the result type of the values
   */
  public static <T> Stream<Pair<String[], T>> flatToNested(
      ObjectNode inputs, BiFunction<String, JsonNode, Optional<T>> process) {
    return StreamSupport.stream(
            Spliterators.spliteratorUnknownSize(inputs.fields(), Spliterator.ORDERED), false)
        .map(
            entry ->
                process
                    .apply(entry.getKey(), entry.getValue())
                    .map(value -> new Pair<>(PERIOD.split(entry.getKey()), value))
                    .orElse(null))
        .filter(Objects::nonNull);
  }

  private static Rule<Function<Boolean, Imyhat>> just(Imyhat type) {
    return (p, o) -> {
      o.accept(x -> type);
      return p.whitespace();
    };
  }

  /**
   * Take a womtool inputs JSON object and convert it into a list of workflow configurations
   *
   * <p>Each womtool record looks like <tt>"WORKFLOW.TASK.VARIABLE":"TYPE"</tt> and we want to
   * transform it into something that would be <tt>workflow = { task = {variable = type}}</tt> in
   * Shesmu, collecting all the input variables for a single task into an object.
   */
  public static Stream<Pair<String[], Imyhat>> of(
      ObjectNode inputs, boolean pairsAsObjects, ErrorConsumer errorHandler) {
    return flatToNested(
        inputs,
        (name, node) -> {
          final Imyhat result = parseRoot(node.asText(), pairsAsObjects, errorHandler);
          return result.isBad() ? Optional.empty() : Optional.of(result);
        });
  }

  public static Parser parse(Parser parser, Consumer<Function<Boolean, Imyhat>> output) {
    final AtomicReference<Function<Boolean, Imyhat>> value = new AtomicReference<>();
    final Parser result =
        parser
            .whitespace()
            .dispatch(DISPATCH, value::set)
            .regex(
                OPTIONAL,
                q -> {
                  if (q.group(0).equals("?")) value.updateAndGet(o -> x -> o.apply(x).asOptional());
                },
                "Optional or nothing.")
            .whitespace();
    if (result.isGood()) {
      output.accept(value.get());
    }
    return result;
  }

  public static Imyhat parseString(String input) {
    return parseString(input, false);
  }

  private static final Pattern DEFAULT_PROVIDED = Pattern.compile("(\\([^)]*\\))?");

  public static Imyhat parseString(String input, boolean pairsAsObjects) {
    return parseRoot(input, pairsAsObjects, (line, column, errorMessage) -> {});
  }

  public static Imyhat parseRoot(String input, boolean pairsAsObjects, ErrorConsumer errorHandler) {
    final AtomicReference<Function<Boolean, Imyhat>> type = new AtomicReference<>();
    final Parser result =
        parse(Parser.start(input, errorHandler), type::set)
            .regex(
                DEFAULT_PROVIDED,
                m -> type.updateAndGet(t -> x -> t.apply(x).asOptional()),
                "Shouldn't reach here");
    if (result.isGood()) {
      return type.get().apply(pairsAsObjects);
    }
    return Imyhat.BAD;
  }

  private static final ParseDispatch<Function<Boolean, Imyhat>> DISPATCH = new ParseDispatch<>();
  private static final Consumer<Matcher> IGNORE = m -> {};
  private static final Pattern OPTIONAL = Pattern.compile("\\??");
  private static final Pattern OPTIONAL_PLUS = Pattern.compile("\\+?");
  private static final Pattern PERIOD = Pattern.compile("\\.");
  /** Convert a Shesmu type into its equivalent WDL type */
  public static final ImyhatTransformer<String> TO_WDL_TYPE =
      new ImyhatTransformer<String>() {

        @Override
        public String bool() {
          return "Boolean";
        }

        @Override
        public String date() {
          return "String";
        }

        @Override
        public String floating() {
          return "Float";
        }

        @Override
        public String integer() {
          return "Int";
        }

        @Override
        public String json() {
          return "mixed"; // The WDL spec is unclear if this is a real type name or an indication of
          // a hidden generic
        }

        @Override
        public String list(Imyhat inner) {
          return "Array[" + inner.apply(this) + "]";
        }

        @Override
        public String map(Imyhat key, Imyhat value) {
          return "Map[" + key.apply(this) + "," + value.apply(this) + "]";
        }

        @Override
        public String object(Stream<Pair<String, Imyhat>> contents) {
          final List<Pair<String, Imyhat>> fields = contents.collect(Collectors.toList());
          if (fields.size() == 2
              && fields
                  .stream()
                  .allMatch(p -> p.first().equals("left") || p.first().equals("right"))) {
            return fields
                .stream()
                .map(p -> p.second().apply(this))
                .collect(Collectors.joining(", ", "Pair[", "]"));
          }
          return fields
              .stream()
              .map(field -> field.first() + " -> " + field.second().apply(this))
              .collect(Collectors.joining("\n", "WomCompositeType {\n", "}"));
        }

        @Override
        public String optional(Imyhat inner) {
          return inner == null ? "Object?" : (inner.apply(this) + "?");
        }

        @Override
        public String path() {
          return "File";
        }

        @Override
        public String string() {
          return "String";
        }

        @Override
        public String tuple(Stream<Imyhat> contents) {
          List<String> types = contents.map(type -> type.apply(this)).collect(Collectors.toList());
          Collections.reverse(types);

          return types
              .stream()
              .reduce((a, b) -> "Pair[" + a + "," + b + "]")
              .orElseThrow(() -> new IllegalArgumentException("Cannot cope with empty tuple."));
        }
      };

  static {
    DISPATCH.addKeyword("Boolean", just(Imyhat.BOOLEAN));
    DISPATCH.addKeyword("String", just(Imyhat.STRING));
    DISPATCH.addKeyword("Int", just(Imyhat.INTEGER));
    DISPATCH.addKeyword("File", just(Imyhat.PATH));
    DISPATCH.addKeyword("Float", just(Imyhat.FLOAT));
    DISPATCH.addKeyword(
        "Array",
        (p, o) -> {
          final AtomicReference<Function<Boolean, Imyhat>> inner = new AtomicReference<>();
          final Parser result =
              p.whitespace()
                  .symbol("[")
                  .whitespace()
                  .then(WdlInputType::parse, inner::set)
                  .symbol("]")
                  .regex(OPTIONAL_PLUS, IGNORE, "Plus or nothing.")
                  .whitespace();
          if (result.isGood()) {
            o.accept(x -> inner.get().apply(x).asList());
          }
          return result;
        });
    DISPATCH.addKeyword(
        "Map",
        (p, o) -> {
          final AtomicReference<Function<Boolean, Imyhat>> key = new AtomicReference<>();
          final AtomicReference<Function<Boolean, Imyhat>> value = new AtomicReference<>();
          final Parser result =
              p.whitespace()
                  .symbol("[")
                  .whitespace()
                  .then(WdlInputType::parse, key::set)
                  .symbol(",")
                  .then(WdlInputType::parse, value::set)
                  .symbol("]");
          if (result.isGood()) {
            o.accept(x -> Imyhat.dictionary(key.get().apply(x), value.get().apply(x)));
          }
          return result;
        });
    DISPATCH.addKeyword(
        "Pair",
        (p, o) -> {
          final List<Function<Boolean, Imyhat>> inner = new ArrayList<>();
          final Parser result =
              p.whitespace()
                  .symbol("[")
                  .whitespace()
                  .then(WdlInputType::parse, inner::add)
                  .symbol(",")
                  .then(WdlInputType::parse, inner::add)
                  .symbol("]");
          if (result.isGood()) {
            o.accept(
                pairsAsObjects ->
                    pairsAsObjects
                        ? new Imyhat.ObjectImyhat(
                            Stream.of(
                                new Pair<>("left", inner.get(0).apply(pairsAsObjects)),
                                new Pair<>("right", inner.get(1).apply(pairsAsObjects))))
                        : Imyhat.tuple(
                            inner
                                .stream()
                                .map(e -> e.apply(pairsAsObjects))
                                .toArray(Imyhat[]::new)));
          }
          return result;
        });
    DISPATCH.addKeyword(
        "WomCompositeType",
        (p, o) -> {
          final AtomicReference<List<Pair<String, Function<Boolean, Imyhat>>>> fields =
              new AtomicReference<>();
          final Parser result =
              p.whitespace()
                  .symbol("{")
                  .whitespace()
                  .list(
                      fields::set,
                      (fp, fo) -> {
                        final AtomicReference<String> name = new AtomicReference<>();
                        final AtomicReference<Function<Boolean, Imyhat>> type =
                            new AtomicReference<>();
                        final Parser fieldResult =
                            fp.whitespace()
                                .identifier(name::set)
                                .whitespace()
                                .symbol("->")
                                .whitespace()
                                .then(WdlInputType::parse, type::set)
                                .whitespace();
                        if (fieldResult.isGood()) {
                          fo.accept(new Pair<>(name.get(), type.get()));
                        }
                        return fieldResult;
                      })
                  .symbol("}");
          if (result.isGood()) {
            o.accept(
                x ->
                    new Imyhat.ObjectImyhat(
                        fields
                            .get()
                            .stream()
                            .map(f -> new Pair<>(f.first(), f.second().apply(x)))));
          }
          return result;
        });
  }

  private WdlInputType() {}
}
