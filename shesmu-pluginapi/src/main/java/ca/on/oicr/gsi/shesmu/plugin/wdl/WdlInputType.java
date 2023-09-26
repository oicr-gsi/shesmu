package ca.on.oicr.gsi.shesmu.plugin.wdl;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.ErrorConsumer;
import ca.on.oicr.gsi.shesmu.plugin.Parser;
import ca.on.oicr.gsi.shesmu.plugin.Parser.ParseDispatch;
import ca.on.oicr.gsi.shesmu.plugin.Parser.Rule;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat.ObjectImyhat;
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
 * <p>Pairs are translated to tuples. Right-nested pairs (<i>e.g.</i>, <code>Pair[X, Pair[Y, Z]]
 * </code>) are flattened into longer tuples (<i>e.g.</i>, <code>{X, Y, Z}</code>).
 *
 * <p>Optional types are parsed, but stripped off.
 *
 * <p>All other types are errors.
 */
public final class WdlInputType {
  /**
   * Take a JSON object of the <code>WORKFLOW.TASK.VARIABLE</code> format and transform it to a
   * nestable one. This processes the values as requested.
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
   * <p>Each womtool record looks like <code>"WORKFLOW.TASK.VARIABLE":"TYPE"</code> and we want to
   * transform it into something that would be <code>workflow = { task = {variable = type}}</code>
   * in Shesmu, collecting all the input variables for a single task into an object.
   */
  public static Stream<Pair<String[], Imyhat>> of(
      ObjectNode inputs, boolean pairsAsObjects, ErrorConsumer errorHandler) {
    return flatToNested(
        inputs,
        (name, node) -> {
          final var result = parseWdlJson(node, pairsAsObjects, errorHandler);
          return result.isBad() ? Optional.empty() : Optional.of(result);
        });
  }

  public static Parser parse(Parser parser, Consumer<Function<Boolean, Imyhat>> output) {
    final var value = new AtomicReference<Function<Boolean, Imyhat>>();
    final var result =
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

  private static Imyhat parseWdlJson(
      JsonNode node, boolean pairsAsObjects, ErrorConsumer errorHandler) {
    if (node.isTextual()) {
      return parseRoot(node.asText(), pairsAsObjects, errorHandler);
    }
    if (node.isObject()) {
      final List<Pair<String, Imyhat>> fields = new ArrayList<>();
      final var entries = node.fields();
      while (entries.hasNext()) {
        final var entry = entries.next();
        fields.add(
            new Pair<>(
                entry.getKey(), parseWdlJson(entry.getValue(), pairsAsObjects, errorHandler)));
      }
      return new ObjectImyhat(fields.stream());
    }
    throw new IllegalArgumentException("Cannot cope with WDL JSON as " + node.getNodeType().name());
  }

  private static final Pattern DEFAULT_PROVIDED = Pattern.compile("(\\([^)]*\\))?");

  public static Imyhat parseString(String input, boolean pairsAsObjects) {
    return parseRoot(input, pairsAsObjects, (line, column, errorMessage) -> {});
  }

  public static Imyhat parseRoot(String input, boolean pairsAsObjects, ErrorConsumer errorHandler) {
    final var type = new AtomicReference<Function<Boolean, Imyhat>>();
    final var result =
        parse(Parser.start(input, errorHandler), type::set)
            .regex(
                DEFAULT_PROVIDED,
                m -> {
                  if (m.group(1) != null) {
                    type.updateAndGet(t -> x -> t.apply(x).asOptional());
                  }
                },
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
      new ImyhatTransformer<>() {

        @Override
        public String algebraic(Stream<AlgebraicTransformer> contents) {
          return "mixed"; // The WDL spec is unclear if this is a real type name or an indication of
          // a hidden generic
        }

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
          final var fields = contents.collect(Collectors.toList());
          if (fields.size() == 2
              && fields.stream()
                  .allMatch(p -> p.first().equals("left") || p.first().equals("right"))) {
            return fields.stream()
                .map(p -> p.second().apply(this))
                .collect(Collectors.joining(", ", "Pair[", "]"));
          }
          return fields.stream()
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
          var types = contents.map(type -> type.apply(this)).collect(Collectors.toList());
          Collections.reverse(types);

          return types.stream()
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
          final var inner = new AtomicReference<Function<Boolean, Imyhat>>();
          final var result =
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
          final var key = new AtomicReference<Function<Boolean, Imyhat>>();
          final var value = new AtomicReference<Function<Boolean, Imyhat>>();
          final var result =
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
          final var result =
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
                            inner.stream()
                                .map(e -> e.apply(pairsAsObjects))
                                .toArray(Imyhat[]::new)));
          }
          return result;
        });
    DISPATCH.addKeyword(
        "WomCompositeType",
        (p, o) -> {
          final var fields = new AtomicReference<List<Pair<String, Function<Boolean, Imyhat>>>>();
          final var result =
              p.whitespace()
                  .symbol("{")
                  .whitespace()
                  .list(
                      fields::set,
                      (fp, fo) -> {
                        final var name = new AtomicReference<String>();
                        final var type = new AtomicReference<Function<Boolean, Imyhat>>();
                        final var fieldResult =
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
                        fields.get().stream()
                            .map(f -> new Pair<>(f.first(), f.second().apply(x)))));
          }
          return result;
        });
  }

  private WdlInputType() {}
}
