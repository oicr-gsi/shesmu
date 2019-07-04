package ca.on.oicr.gsi.shesmu.plugin.wdl;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.ErrorConsumer;
import ca.on.oicr.gsi.shesmu.plugin.Parser;
import ca.on.oicr.gsi.shesmu.plugin.Parser.ParseDispatch;
import ca.on.oicr.gsi.shesmu.plugin.Parser.Rule;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.plugin.types.ImyhatTransformer;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators;
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
  private static final Pattern IDENTIFIER = Pattern.compile("([a-z]_]*)\\.([a-z]_]*)\\.([a-z]_]*)");

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
        public String list(Imyhat inner) {
          return "Array[" + inner.apply(this) + "]";
        }

        @Override
        public String object(Stream<Pair<String, Imyhat>> contents) {
          contents.close();
          return "Object";
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
    DISPATCH.addRaw(
        "Pair",
        (p, o) -> {
          final List<Imyhat> inner = new ArrayList<>();
          final Parser result = pair(p, inner::add);
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
   * transform it into something that would be <tt>task = {variable = type}</tt> in Shesmu,
   * collecting all the input variables for a single task into an object.
   */
  public static Stream<Pair<String, Imyhat>> of(ObjectNode inputs, ErrorConsumer errors) {
    return StreamSupport.stream(
            Spliterators.spliteratorUnknownSize(inputs.fields(), Spliterator.ORDERED), false)
        .map(
            entry -> {
              final AtomicReference<Imyhat> type = new AtomicReference<>(Imyhat.BAD);
              final Parser parser =
                  Parser.start(entry.getValue().asText(), errors)
                      .then(WdlInputType::parse, type::set);
              if (parser.isGood() && parser.isEmpty() && !type.get().isBad()) {
                final Matcher m = IDENTIFIER.matcher(entry.getKey());
                return new Pair<>( // Slice the WF.TASK.VAR = TYPE name into [TASK, [VAR, TYPE]]
                    m.group(1), new Pair<>(m.group(2), type.get()));
              } else {
                return null;
              }
            })
        .filter(Objects::nonNull)
        .collect(
            Collectors.groupingBy( // Group into 1 parameter per task
                Pair::first,
                Collectors.collectingAndThen( // Take all the variables for the same task
                    Collectors.toList(), // and pack them into an object
                    l -> new Imyhat.ObjectImyhat(l.stream().map(Pair::second)))))
        .entrySet()
        .stream()
        .map(e -> new Pair<>(e.getKey(), e.getValue()));
  }

  private static Parser pair(Parser parser, Consumer<Imyhat> output) {
    return parser
        .keyword("Pair")
        .whitespace()
        .symbol("[")
        .whitespace()
        .then(WdlInputType::parse, output)
        .symbol(",")
        .then(WdlInputType::pair, output)
        .symbol("]")
        .regex(OPTIONAL, IGNORE, "Optional or nothing.")
        .whitespace();
  }

  public static Parser parse(Parser parser, Consumer<Imyhat> output) {
    return parser
        .whitespace()
        .dispatch(DISPATCH, output)
        .regex(OPTIONAL, IGNORE, "Optional or nothing.")
        .whitespace();
  }

  private WdlInputType() {}
}
