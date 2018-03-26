package ca.on.oicr.gsi.shesmu.compiler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ca.on.oicr.gsi.shesmu.Imyhat;

/**
 * Parse an input stream
 */
public abstract class Parser {
	private static class Broken extends Parser {

		public Broken(ErrorConsumer consumer, int line, int column, String message) {
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
		public Parser whitespace() {
			return this;
		}

	}

	private static class Good extends Parser {
		private final CharSequence input;

		public Good(ErrorConsumer errorConsumer, CharSequence input, int line, int column) {
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
			return symbol(keyword).lookAhead(c -> !Character.isAlphabetic(c),
					String.format("Expected “%s”, but got junk after.", keyword));
		}

		@Override
		protected Parser lookAhead(Predicate<Character> test, String error) {
			if (input.length() == 0) {
				return this;
			}
			return test.test(input.charAt(0)) ? this : new Broken(errorConsumer(), line(), column(), error);
		}

		@Override
		public Parser regex(Pattern pattern, Consumer<Matcher> output, String errorMessage) {
			final Matcher match = pattern.matcher(input);
			if (match.lookingAt()) {
				output.accept(match);
				return new Good(errorConsumer(), consume(input, match.end()), line(), column() + match.end());
			}
			return new Broken(errorConsumer(), line(), column(), errorMessage);
		}

		@Override
		public Parser symbol(String symbol) {
			if (input.length() < symbol.length()) {
				return new Broken(errorConsumer(), line(), column(),
						String.format("Expected “%s”, but got end-of-file.", symbol));
			}
			for (int i = 0; i < symbol.length(); i++) {
				if (symbol.charAt(i) != input.charAt(i)) {
					return new Broken(errorConsumer(), line(), column(), String.format(
							"Expected “%s”, but got %c instead of %c.", symbol, input.charAt(i), symbol.charAt(i)));
				}
			}
			return new Good(errorConsumer(), consume(input, symbol.length()), line(), column() + symbol.length());
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
					i = consume(i, commentMatch.end());
					inputConsumed = true;
				}
			} while (inputConsumed);

			return new Good(errorConsumer(), i, l, c);
		}
	}

	/**
	 * Allow the parser to try several possible branches and return the first one
	 * that matches
	 *
	 * @param <T>
	 *            the output type of the parsing proccess
	 */
	public static final class ParseDispatch<T> {
		private final List<Rule<T>> dispatches = new ArrayList<>();
		private final Set<String> identifiers = new TreeSet<>();

		/**
		 * Add a rule that will check for a keyword, and if successful, parse the
		 * provided expression
		 *
		 * If the expression fails, the resulting parse will fail.
		 */
		public void addKeyword(String symbol, Rule<T> handler) {
			dispatches.add((p, o) -> {
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
		 * If the expression fails, the parser will backup and try a different branch.
		 */
		public void addRaw(String name, Rule<T> handler) {
			dispatches.add(handler);
			identifiers.add(name);
		}

		/**
		 * Add a rule that will check for a symbol, and if successful, parse the
		 * provided expression
		 *
		 * If the expression fails, the resulting parse will fail.
		 */
		public void addSymbol(String symbol, Rule<T> handler) {
			dispatches.add((p, o) -> {
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
	}

	public interface Rule<T> {
		Parser parse(Parser parser, Consumer<T> output);
	}

	private static final Imyhat[] BASE_TYPES = new Imyhat[] { Imyhat.BOOLEAN, Imyhat.DATE, Imyhat.INTEGER,
			Imyhat.STRING };

	private static Pattern COMMENT = Pattern.compile("(#[^\\n]*)?\\n");

	public static Pattern IDENTIFIER = Pattern.compile("[a-z][a-z0-9_]*");

	private static Pattern WHITESPACE = Pattern.compile("[\\t ]+");

	private static CharSequence consume(CharSequence input, int offset) {
		return input.subSequence(offset, input.length());
	}

	/**
	 * Parse a type.
	 */
	public static Parser parseImyhat(Parser input, Consumer<Imyhat> output) {
		final Parser listParser = input.symbol("[");
		if (listParser.isGood()) {
			final AtomicReference<Imyhat> inner = new AtomicReference<>(Imyhat.BAD);
			final Parser result = parseImyhat(listParser.whitespace(), inner::set).whitespace().symbol("]");
			output.accept(inner.get().asList());
			return result;
		}

		final Parser tupleParser = input.symbol("<");
		if (tupleParser.isGood()) {
			final AtomicReference<List<Imyhat>> inner = new AtomicReference<>(Collections.emptyList());
			final Parser result = listParser.whitespace()
					.list(inner::set, (p, o) -> parseImyhat(p, o).whitespace(), ',').symbol(")");
			output.accept(Imyhat.tuple(inner.get().stream().toArray(Imyhat[]::new)));
			return result;
		}

		for (final Imyhat base : BASE_TYPES) {
			final Parser result = input.keyword(base.name());
			if (result.isGood()) {
				output.accept(base);
				return result;
			}
		}
		return input.raise("Expected a type.");
	}

	/**
	 * Create a new parser for some input
	 */
	public static Parser start(CharSequence input, ErrorConsumer errorConsumer) {
		return new Good(errorConsumer, input, 1, 1);
	}

	private final int column;

	private final ErrorConsumer errorConsumer;

	private final int line;

	private Parser(ErrorConsumer errorConsumer, int line, int column) {
		this.line = line;
		this.column = column;
		this.errorConsumer = errorConsumer;
	}

	/**
	 * The current column in the input.
	 */
	public final int column() {
		return column;
	}

	/**
	 * Parse using a dispatch table and return the first successful branch.
	 */
	public abstract <T> Parser dispatch(ParseDispatch<T> dispatch, Consumer<T> consumer);

	protected ErrorConsumer errorConsumer() {
		return errorConsumer;
	}

	/**
	 * Parse an identifier
	 */
	public final Parser identifier(Consumer<String> output) {
		return regex(IDENTIFIER, m -> output.accept(m.group(0)), "Expected identifier.");
	}

	/**
	 * Parse an unsigned integer
	 *
	 * @param output
	 *            the storage location for the result
	 * @param base
	 *            the radix
	 */
	public abstract Parser integer(LongConsumer output, int base);

	/**
	 * Whether there is more input to be consumed
	 */
	public abstract boolean isEmpty();

	/**
	 * Whether the parser is in an error state
	 */
	public abstract boolean isGood();

	/**
	 * Parse a keyword
	 *
	 * A keyword is different from a {@link #symbol(String)} because a keyword
	 * cannot be followed by alphabetical characters.
	 */
	public abstract Parser keyword(String keyword);

	/**
	 * Get the current line in the input
	 */
	public final int line() {
		return line;
	}

	/**
	 * Parse a list of items with no delimiters
	 *
	 * The list may be empty.
	 */
	public final <T> Parser list(Consumer<List<T>> output, Rule<T> childParser) {
		Parser last = this;
		final List<T> list = new ArrayList<>();
		for (Parser current = whitespace(); current
				.isGood(); current = childParser.parse(current.whitespace(), list::add)) {
			last = current;
		}
		output.accept(list);
		return last;
	}

	/**
	 * Parse a character-delimited list
	 *
	 * The list must have at least one item.
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
	 * The list may have no items.
	 */
	public final <T> Parser listEmpty(Consumer<List<T>> output, Rule<T> childParser, char separator) {
		final String separatorString = Character.toString(separator);
		Parser last = this;
		final List<T> list = new ArrayList<>();
		for (Parser current = childParser.parse(whitespace(), list::add); current
				.isGood(); current = childParser.parse(current.whitespace(), list::add)) {
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

	/**
	 * Check if the next character in the input, if any, is the one provided.
	 */
	public final boolean lookAhead(char c) {
		return lookAhead(x -> x.charValue() == c, "Lookahead").isGood();
	}

	/**
	 * Check if the next character in the input, if any, satisfies a condition.
	 */
	protected abstract Parser lookAhead(Predicate<Character> test, String error);

	/**
	 * Return a parser which flags an error at the current position.
	 */
	public final Parser raise(String message) {
		return new Broken(errorConsumer, line, column, message);
	}

	/**
	 * Match a regular expression at the current position
	 */
	public abstract Parser regex(Pattern pattern, Consumer<Matcher> output, String errorMessage);

	/**
	 * Check if two parsers are at the same position in the input
	 */
	public final boolean same(Parser other) {
		return other.line() == line() && other.column() == column();
	}

	/**
	 * Parse a symbol
	 *
	 * The next character after the matched symbol can be any character.
	 */
	public abstract Parser symbol(String symbol);

	/**
	 * Parse a rule
	 *
	 * This is to make long parse blocks read left-to-right.
	 */
	public final <T> Parser then(Rule<T> rule, Consumer<T> output) {
		return rule.parse(this, output);
	}

	/**
	 * Consume and comments or white space in the input.
	 */
	public abstract Parser whitespace();
}
