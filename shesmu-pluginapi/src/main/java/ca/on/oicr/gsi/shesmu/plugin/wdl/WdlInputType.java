package ca.on.oicr.gsi.shesmu.plugin.wdl;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.ErrorConsumer;
import ca.on.oicr.gsi.shesmu.plugin.Parser;
import ca.on.oicr.gsi.shesmu.plugin.Parser.ParseDispatch;
import ca.on.oicr.gsi.shesmu.plugin.Parser.Rule;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.plugin.types.ImyhatTransformer;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
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
  private static final ParseDispatch<Imyhat> DISPATCH = new ParseDispatch<>();
  private static final Pattern PERIOD = Pattern.compile("\\.");

  private static final Consumer<Matcher> IGNORE = m -> {};
  private static final Pattern OPTIONAL = Pattern.compile("\\??");
  private static final Pattern OPTIONAL_PLUS = Pattern.compile("\\+?");
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
        public String object(Stream<Pair<String, Imyhat>> contents) {
          contents.close();
          return "Object";
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
          final AtomicReference<Imyhat> inner = new AtomicReference<>();
          final Parser result =
              p.whitespace()
                  .symbol("[")
                  .whitespace()
                  .then(WdlInputType::parse, inner::set)
                  .symbol("]")
                  .regex(OPTIONAL_PLUS, IGNORE, "Plus or nothing.")
                  .whitespace();
          if (result.isGood()) {
            o.accept(inner.get().asList());
          }
          return result;
        });
    DISPATCH.addKeyword(
        "Pair",
        (p, o) -> {
          final List<Imyhat> inner = new ArrayList<>();
          final Parser result =
              p.whitespace()
                  .symbol("[")
                  .whitespace()
                  .then(WdlInputType::parse, inner::add)
                  .symbol(",")
                  .then(WdlInputType::parse, inner::add)
                  .symbol("]");
          if (result.isGood()) {
            o.accept(Imyhat.tuple(inner.stream().toArray(Imyhat[]::new)));
          }
          return result;
        });
  }

  private static Rule<Imyhat> just(Imyhat type) {
    return (p, o) -> {
      o.accept(type);
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
  public static Stream<Pair<String[], Imyhat>> of(ObjectNode inputs, ErrorConsumer errorHandler) {
    return StreamSupport.stream(
            Spliterators.spliteratorUnknownSize(inputs.fields(), Spliterator.ORDERED), false)
        .map(
            entry -> {
              final AtomicReference<Imyhat> type = new AtomicReference<>(Imyhat.BAD);
              final AtomicReference<Pair<Integer, String>> error = new AtomicReference<>();
              final Parser parser =
                  Parser.start(
                          entry.getValue().asText(),
                          (line, column, errorMessage) -> {
                            if (error.get() == null || error.get().first() < column) {
                              error.set(new Pair<>(column, errorMessage));
                            }
                          })
                      .then(WdlInputType::parse, type::set);
              if (parser.isGood() && parser.isEmpty() && !type.get().isBad()) {
                return new Pair<>(
                    PERIOD.split(entry.getKey()), type.get()); // Slice the WF.TASK.VAR = TYPE
              } else {
                errorHandler.raise(1, error.get().first(), error.get().second());
                return null;
              }
            })
        .filter(Objects::nonNull);
  }

  public static Parser parse(Parser parser, Consumer<Imyhat> output) {
    final AtomicReference<Imyhat> value = new AtomicReference<>();
    final Parser result =
        parser
            .whitespace()
            .dispatch(DISPATCH, value::set)
            .regex(
                OPTIONAL,
                q -> {
                  if (q.group(0).equals("?")) value.updateAndGet(Imyhat::asOptional);
                },
                "Optional or nothing.")
            .whitespace();
    if (result.isGood()) {
      output.accept(value.get());
    }
    return result;
  }

  private WdlInputType() {}
}
