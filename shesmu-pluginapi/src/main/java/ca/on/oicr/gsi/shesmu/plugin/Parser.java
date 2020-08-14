package ca.on.oicr.gsi.shesmu.plugin;

import ca.on.oicr.gsi.Pair;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Parse an input stream */
public abstract class Parser {
  /**
   * Parses a particular section of grammar that emits a known type of result
   *
   * @param <T> the type generated
   */
  public interface Rule<T> {
    Parser parse(Parser parser, Consumer<T> output);
  }

  private static class Broken extends Parser {

    public Broken(MaxParseError consumer, int line, int column, String message) {
      super(consumer, line, column);
      consumer.raise(line(), column(), message);
    }

    @Override
    public <T> Parser dispatch(ParseDispatch<T> dispatch, Consumer<T> consumer) {
      return this;
    }

    @Override
    public Parser integer(LongConsumer output, int base) {
      return this;
    }

    @Override
    public boolean isEmpty() {
      return true;
    }

    @Override
    public boolean isGood() {
      return false;
    }

    @Override
    public Parser keyword(String keyword) {
      return this;
    }

    @Override
    protected Parser lookAhead(Predicate<Character> test, String error) {
      return this;
    }

    @Override
    public Parser regex(Pattern pattern, Consumer<Matcher> output, String errorMessage) {
      return this;
    }

    @Override
    public Parser symbol(String symbol) {
      return this;
    }

    @Override
    public String toString() {
      return "Broken parser";
    }

    @Override
    public Parser whitespace() {
      return this;
    }
  }

  private static class Good extends Parser {
    private final CharSequence input;

    public Good(MaxParseError errorConsumer, CharSequence input, int line, int column) {
      super(errorConsumer, line, column);
      this.input = input;
    }

    @Override
    public <T> Parser dispatch(ParseDispatch<T> dispatch, Consumer<T> consumer) {
      for (final Rule<T> d : dispatch.dispatches) {
        final Parser matched = d.parse(this, consumer);
        if (matched.isGood()) {
          return matched;
        }
      }
      return raise(dispatch.error());
    }

    @Override
    public Parser integer(LongConsumer output, int radix) {
      long accumulator = 0;
      int index;
      for (index = 0; index < input.length() && Character.isDigit(input.charAt(index)); index++) {
        accumulator = accumulator * radix + Character.digit(input.charAt(index), radix);
      }
      if (index == 0) {
        return raise("Expected integer.");
      }
      output.accept(accumulator);
      return new Good(errorConsumer(), consume(input, index), line(), column() + index);
    }

    @Override
    public boolean isEmpty() {
      return input.length() == 0;
    }

    @Override
    public boolean isGood() {
      return true;
    }

    @Override
    public Parser keyword(String keyword) {
      return symbol(keyword)
          .lookAhead(
              c -> !Character.isAlphabetic(c),
              String.format("Expected “%s”, but got junk after.", keyword));
    }

    @Override
    protected Parser lookAhead(Predicate<Character> test, String error) {
      if (input.length() == 0) {
        return this;
      }
      return test.test(input.charAt(0))
          ? this
          : new Broken(errorConsumer(), line(), column(), error);
    }

    @Override
    public Parser regex(Pattern pattern, Consumer<Matcher> output, String errorMessage) {
      final Matcher match = pattern.matcher(input);
      if (match.lookingAt()) {
        output.accept(match);
        return new Good(
            errorConsumer(), consume(input, match.end()), line(), column() + match.end());
      }
      return new Broken(errorConsumer(), line(), column(), errorMessage);
    }

    @Override
    public Parser symbol(String symbol) {
      if (input.length() < symbol.length()) {
        return new Broken(
            errorConsumer(),
            line(),
            column(),
            String.format("Expected “%s”, but got end-of-file.", symbol));
      }
      for (int i = 0; i < symbol.length(); i++) {
        if (symbol.charAt(i) != input.charAt(i)) {
          final CharSequence got;
          if (input.charAt(i) == '\n') {
            got = "\\n";
          } else if (Character.isISOControl(input.charAt(i))) {
            got = String.format("\\U%06X", (int) input.charAt(i));
          } else {
            got = input.subSequence(i, i + 1);
          }

          return new Broken(
              errorConsumer(),
              line(),
              column(),
              String.format(
                  "Expected “%s”, but got %s instead of %c.", symbol, got, symbol.charAt(i)));
        }
      }
      return new Good(
          errorConsumer(), consume(input, symbol.length()), line(), column() + symbol.length());
    }

    @Override
    public String toString() {
      return "Parsing: " + input;
    }

    @Override
    public Parser whitespace() {
      CharSequence i = input;
      int l = line();
      int c = column();
      boolean inputConsumed;
      do {
        inputConsumed = false;
        final Matcher spaceMatch = WHITESPACE.matcher(i);
        if (spaceMatch.lookingAt()) {
          c += spaceMatch.end();
          i = consume(i, spaceMatch.end());
          inputConsumed = true;
        }
        final Matcher commentMatch = COMMENT.matcher(i);
        if (commentMatch.lookingAt()) {
          c = 1;
          l++;
          i = consume(i, Math.min(commentMatch.end() + 1, i.length()));
          inputConsumed = true;
        } else if (i.length() > 0 && i.charAt(0) == '\n') {
          c = 1;
          l++;
          i = consume(i, 1);
          inputConsumed = true;
        }
      } while (inputConsumed);

      return new Good(errorConsumer(), i, l, c);
    }
  }

  private static class MaxParseError {
    private final ErrorConsumer backing;
    private int column;
    private int line;
    private String message = "No error.";

    private MaxParseError(ErrorConsumer backing) {
      this.backing = backing;
    }

    public void raise(int line, int column, String errorMessage) {
      if (this.line < line || this.line == line && this.column <= column) {
        this.line = line;
        this.column = column;
        message = errorMessage;
      }
    }

    public void write() {
      backing.raise(line, column, message);
    }
  }

  /**
   * Allow the parser to try several possible branches and return the first one that matches
   *
   * @param <T> the output type of the parsing proccess
   */
  public static final class ParseDispatch<T> implements Rule<T> {
    private final List<Rule<T>> dispatches = new ArrayList<>();
    private final Set<String> identifiers = new TreeSet<>();

    /**
     * Add a rule that will check for a keyword, and if successful, parse the provided expression
     *
     * <p>If the expression fails, the resulting parse will fail.
     */
    public void addKeyword(String symbol, Rule<T> handler) {
      dispatches.add(
          (p, o) -> {
            final Parser result = p.keyword(symbol);
            if (result.isGood()) {
              return handler.parse(result, o);
            }
            return result;
          });
      identifiers.add(symbol);
    }

    /**
     * Add a rule that will attempt to parse the provided rule
     *
     * <p>If the expression fails, the parser will backup and try a different branch.
     */
    public void addRaw(String name, Rule<T> handler) {
      dispatches.add(handler);
      identifiers.add(name);
    }

    /**
     * Add a rule that will check for a symbol, and if successful, parse the provided expression
     *
     * <p>If the expression fails, the resulting parse will fail.
     */
    public void addSymbol(String symbol, Rule<T> handler) {
      dispatches.add(
          (p, o) -> {
            final Parser result = p.symbol(symbol);
            if (result.isGood()) {
              return handler.parse(result, o);
            }
            return result;
          });
      identifiers.add(symbol);
    }

    public String error() {
      return "Could not match any of: " + String.join(", ", identifiers);
    }

    @Override
    public Parser parse(Parser parser, Consumer<T> output) {
      return parser.whitespace().dispatch(this, output).whitespace();
    }
  }

  public static final Pattern ALGEBRAIC_NAME = Pattern.compile("[A-Z][A-Z_0-9]+");

  private static final Pattern COMMENT = Pattern.compile("#[^\\n]*");
  public static final Pattern IDENTIFIER = Pattern.compile("[a-z][a-zA-Z0-9_]*");
  public static final Pattern QUALIFIED_IDENTIFIER =
      Pattern.compile("[a-z][a-zA-Z0-9_]*(::[a-z][a-zA-Z0-9_]*)*");
  public static final String NAMESPACE_SEPARATOR = "::";
  private static final Pattern WHITESPACE = Pattern.compile("[\\t ]+");

  private static CharSequence consume(CharSequence input, int offset) {
    return input.subSequence(offset, input.length());
  }

  public static <T> Rule<T> just(T value) {
    return (p, o) -> {
      o.accept(value);
      return p;
    };
  }

  public static <T> Rule<T> justWhiteSpace(T value) {
    return (p, o) -> {
      o.accept(value);
      return p.whitespace();
    };
  }

  /**
   * Scan a series of binary operators (e.g., +/-)
   *
   * @param child the parser for the terms
   * @param condensers the rules to coalesce terms
   * @param input the input parser
   * @param output the output term handler
   * @param <T> the type of the nodes
   */
  public static <T> Parser scanBinary(
      Rule<T> child,
      ParseDispatch<BinaryOperator<T>> condensers,
      Parser input,
      Consumer<T> output) {
    final AtomicReference<T> node = new AtomicReference<>();
    Parser parser = child.parse(input, node::set);
    while (parser.isGood()) {
      final AtomicReference<T> right = new AtomicReference<>();
      final AtomicReference<BinaryOperator<T>> condenser = new AtomicReference<>();
      final Parser next =
          child.parse(parser.dispatch(condensers, condenser::set).whitespace(), right::set);
      if (next.isGood()) {
        node.set(condenser.get().apply(node.get(), right.get()));
        parser = next;
      } else {
        output.accept(node.get());
        return parser;
      }
    }
    return parser;
  }

  public static <T> Parser scanPrefixed(
      Rule<T> child, ParseDispatch<UnaryOperator<T>> condensers, Parser input, Consumer<T> output) {
    final AtomicReference<UnaryOperator<T>> modifier = new AtomicReference<>();
    Parser next = input.dispatch(condensers, modifier::set).whitespace();
    if (!next.isGood()) {
      next = input;
      modifier.set(UnaryOperator.identity());
    }
    final AtomicReference<T> node = new AtomicReference<>();
    final Parser result = child.parse(next, node::set).whitespace();
    if (result.isGood()) {
      output.accept(modifier.get().apply(node.get()));
    }
    return result;
  }

  public static <T> Parser scanSuffixed(
      Rule<T> child,
      ParseDispatch<UnaryOperator<T>> condensers,
      boolean repeated,
      Parser input,
      Consumer<T> output) {
    final AtomicReference<T> node = new AtomicReference<>();
    Parser result = child.parse(input, node::set).whitespace();
    boolean again = true;
    while (result.isGood() && again) {
      final AtomicReference<UnaryOperator<T>> modifier = new AtomicReference<>();
      final Parser next = result.dispatch(condensers, modifier::set).whitespace();
      if (next.isGood()) {
        result = next;
        node.updateAndGet(modifier.get());
        again = repeated;
      } else {
        again = false;
      }
    }
    if (result.isGood()) {
      output.accept(node.get());
    }
    return result;
  }

  /** Create a new parser for some input */
  public static Parser start(CharSequence input, ErrorConsumer errorConsumer) {
    return new Good(new MaxParseError(errorConsumer), input, 1, 1);
  }

  private final int column;
  private final MaxParseError errorConsumer;
  private final int line;

  private Parser(MaxParseError errorConsumer, int line, int column) {
    this.line = line;
    this.column = column;
    this.errorConsumer = errorConsumer;
  }

  /** Parse an algebraic name/type tag. */
  public final Parser algebraicIdentifier(Consumer<String> name) {
    return regex(Parser.ALGEBRAIC_NAME, m -> name.accept(m.group()), "algebraic value name");
  }

  /** The current column in the input. */
  public final int column() {
    return column;
  }

  /** Parse using a dispatch table and return the first successful branch. */
  public abstract <T> Parser dispatch(ParseDispatch<T> dispatch, Consumer<T> consumer);

  protected MaxParseError errorConsumer() {
    return errorConsumer;
  }

  /** Checks if the parser is good and there is no more input and emits any errors. */
  public final boolean finished() {
    if (isGood()) {
      if (isEmpty()) {
        return true;
      } else {
        errorConsumer.raise(line(), column(), "Junk at end of file.");
      }
    }
    errorConsumer.write();
    return false;
  }

  /** Parse an identifier */
  public final Parser identifier(Consumer<String> output) {
    return regex(IDENTIFIER, m -> output.accept(m.group(0)), "Expected identifier.");
  }

  /**
   * Parse an unsigned integer
   *
   * @param output the storage location for the result
   * @param base the radix
   */
  public abstract Parser integer(LongConsumer output, int base);

  /** Whether there is more input to be consumed */
  public abstract boolean isEmpty();

  /** Whether the parser is in an error state */
  public abstract boolean isGood();

  /**
   * Parse a keyword
   *
   * <p>A keyword is different from a {@link #symbol(String)} because a keyword cannot be followed
   * by alphabetical characters.
   */
  public abstract Parser keyword(String keyword);

  /**
   * Check for a keyword and parse more if found; if not, remain unchanged
   *
   * @param keyword the keyword to look for
   * @param ifFound the parse action to perform if found
   */
  public final Parser keyword(String keyword, UnaryOperator<Parser> ifFound) {
    final Parser test = keyword(keyword);
    if (test.isGood()) {
      return ifFound.apply(test);
    }
    return this;
  }

  /** Get the current line in the input */
  public final int line() {
    return line;
  }

  /**
   * Parse a list of items with no delimiters
   *
   * <p>The list may be empty.
   */
  public final <T> Parser list(Consumer<List<T>> output, Rule<T> childParser) {
    Parser last = this;
    final List<T> list = new ArrayList<>();
    for (Parser current = this; current.isGood(); current = childParser.parse(current, list::add)) {
      last = current;
    }
    output.accept(list);
    return last;
  }

  /**
   * Parse a character-delimited list
   *
   * <p>The list must have at least one item.
   */
  public final <T> Parser list(Consumer<List<T>> output, Rule<T> childParser, char separator) {
    final String separatorString = Character.toString(separator);
    Parser current = this;
    final List<T> list = new ArrayList<>();
    while (current.isGood()) {
      current = childParser.parse(current, list::add);
      if (current.lookAhead(separator)) {
        current = current.symbol(separatorString);
      } else {
        break;
      }
    }
    output.accept(list);
    return current;
  }

  /**
   * Parse a character-delimited list
   *
   * <p>The list may have no items.
   */
  public final <T> Parser listEmpty(Consumer<List<T>> output, Rule<T> childParser, char separator) {
    final String separatorString = Character.toString(separator);
    Parser last = this;
    final List<T> list = new ArrayList<>();
    for (Parser current = childParser.parse(whitespace(), list::add);
        current.isGood();
        current = childParser.parse(current.whitespace(), list::add)) {
      last = current;
      if (current.lookAhead(separator)) {
        current = current.symbol(separatorString);
      } else {
        break;
      }
    }
    output.accept(list);
    return last;
  }

  /** Emit current line and column */
  public final Parser location(Consumer<Pair<Integer, Integer>> output) {
    output.accept(new Pair<>(line(), column()));
    return this;
  }

  /** Check if the next character in the input, if any, is the one provided. */
  public final boolean lookAhead(char c) {
    return lookAhead(x -> x.charValue() == c, "Lookahead").isGood();
  }

  /** Check if the next character in the input, if any, satisfies a condition. */
  protected abstract Parser lookAhead(Predicate<Character> test, String error);

  /**
   * Parse a colon separated name
   *
   * @see #identifier(Consumer)
   */
  public final Parser qualifiedIdentifier(Consumer<String> name) {
    final AtomicReference<List<String>> namespaces = new AtomicReference<>();
    final AtomicReference<String> tail = new AtomicReference<>();
    Parser result =
        this.whitespace()
            .list(
                namespaces::set,
                (partParser, partOutput) -> {
                  final AtomicReference<String> part = new AtomicReference<>();
                  final Parser partResult =
                      partParser
                          .whitespace()
                          .identifier(part::set)
                          .whitespace()
                          .symbol(NAMESPACE_SEPARATOR)
                          .whitespace();
                  if (partResult.isGood()) {
                    partOutput.accept(part.get());
                  }
                  return partResult;
                })
            .identifier(tail::set)
            .whitespace();
    if (result.isGood()) {
      name.accept(
          Stream.concat(namespaces.get().stream(), Stream.of(tail.get()))
              .collect(Collectors.joining(NAMESPACE_SEPARATOR)));
    }
    return result;
  }

  /** Return a parser which flags an error at the current position. */
  public final Parser raise(String message) {
    return new Broken(errorConsumer, line, column, message);
  }

  /** Match a regular expression at the current position */
  public abstract Parser regex(Pattern pattern, Consumer<Matcher> output, String errorMessage);

  /** Check if two parsers are at the same position in the input */
  public final boolean same(Parser other) {
    return other.line() == line() && other.column() == column();
  }

  /**
   * Check for a symbol and parse more if found; if not, remain unchanged
   *
   * @param symbol the symbol to look for
   * @param ifFound the parse action to perform if found
   */
  public final Parser symbol(String symbol, UnaryOperator<Parser> ifFound) {
    final Parser test = symbol(symbol);
    if (test.isGood()) {
      return ifFound.apply(test);
    }
    return this;
  }

  /**
   * Parse a symbol
   *
   * <p>The next character after the matched symbol can be any character.
   */
  public abstract Parser symbol(String symbol);

  /**
   * Parse a rule
   *
   * <p>This is to make long parse blocks read left-to-right.
   */
  public final <T> Parser then(Rule<T> rule, Consumer<T> output) {
    return rule.parse(this, output);
  }

  /** Consume and comments or white space in the input. */
  public abstract Parser whitespace();
}
