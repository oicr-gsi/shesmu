package ca.on.oicr.gsi.shesmu.compiler.wdl;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.node.ObjectNode;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.compiler.ErrorConsumer;
import ca.on.oicr.gsi.shesmu.compiler.Parser;
import ca.on.oicr.gsi.shesmu.compiler.Parser.ParseDispatch;
import ca.on.oicr.gsi.shesmu.compiler.Parser.Rule;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;

/**
 * Translate WDL types to their Shesmu equivalents
 * 
 * The base types (Boolean, String, Int, File) are trivially matched.
 * 
 * WDL arrays are mapped to Shesmu lists. WDL arrays can be marked as non-empty;
 * this information is parsed, but discarded.
 * 
 * Pairs are translated to tuples. Right-nested pairs (<i>e.g.</i>,
 * <tt>Pair[X, Pair[Y, Z]]</tt>) are flattened into longer tuples (<i>e.g.</i>,
 * <tt>{X, Y, Z}</tt>).
 * 
 * Optional types are parsed, but stripped off.
 * 
 * All other types are errors.
 */
public final class WdlInputType {
	private static final ParseDispatch<Imyhat> DISPATCH = new ParseDispatch<>();

	private static final Pattern IDENTIFIER = Pattern.compile("([a-z]_]*)\\.([a-z]_]*)\\.([a-z]_]*)");
	private static final Pattern OPTIONAL = Pattern.compile("?\\?");
	private static final Pattern OPTIONAL_PLUS = Pattern.compile("+\\?");
	private static final Consumer<Matcher> IGNORE = m -> {
	};

	static {
		DISPATCH.addKeyword("Boolean", just(Imyhat.BOOLEAN));
		DISPATCH.addKeyword("String", just(Imyhat.STRING));
		DISPATCH.addKeyword("Int", just(Imyhat.INTEGER));
		DISPATCH.addKeyword("File", just(Imyhat.PATH));
		DISPATCH.addKeyword("Array", (p, o) -> {
			final AtomicReference<Imyhat> inner = new AtomicReference<>();
			final Parser result = p//
					.whitespace()//
					.symbol("[")//
					.whitespace()//
					.then(WdlInputType::parse, inner::set)//
					.symbol("]")//
					.regex(OPTIONAL_PLUS, IGNORE, "Plus or nothing.")//
					.whitespace();
			if (result.isGood()) {
				o.accept(inner.get().asList());
			}
			return result;
		});
		DISPATCH.addRaw("Pair", (p, o) -> {
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
	 * Take a womtool inputs JSON object and convert it into a list of workflow
	 * configurations
	 * 
	 * Each womtool record looks like <tt>"WORKFLOW.TASK.VARIABLE":"TYPE"</tt> and
	 * we want to transform it into something that would be
	 * <tt>task = {variable = type}</tt> in Shesmu, collecting all the input
	 * variables for a single task into an object.
	 */
	public static Stream<Pair<String, Imyhat>> of(ObjectNode inputs, ErrorConsumer errors) {
		return RuntimeSupport.stream(inputs.fields())//
				.map(entry -> {
					final AtomicReference<Imyhat> type = new AtomicReference<>(Imyhat.BAD);
					final Parser parser = Parser.start(entry.getValue().asText(), errors).then(WdlInputType::parse,
							type::set);
					if (parser.isGood() && parser.isEmpty() && !type.get().isBad()) {
						final Matcher m = IDENTIFIER.matcher(entry.getKey());
						return new Pair<>(// Slice the WF.TASK.VAR = TYPE name into [TASK, [VAR, TYPE]]
								m.group(1), //
								new Pair<>(m.group(2), type.get()));
					} else {
						return null;
					}
				})//
				.filter(Objects::nonNull)//
				.collect(Collectors.groupingBy(// Group into 1 parameter per task
						Pair::first, Collectors.collectingAndThen(// Take all the variables for the same task
								Collectors.toList(), // and pack them into an object
								l -> new Imyhat.ObjectImyhat(l.stream().map(Pair::second)))))
				.entrySet().stream()//
				.map(e -> new Pair<>(e.getKey(), e.getValue()));
	}

	private static Parser pair(Parser parser, Consumer<Imyhat> output) {
		return parser//
				.keyword("Pair")//
				.whitespace()//
				.symbol("[")//
				.whitespace()//
				.then(WdlInputType::parse, output)//
				.symbol(",") //
				.then(WdlInputType::pair, output)//
				.symbol("]")//
				.regex(OPTIONAL, IGNORE, "Optional or nothing.")//
				.whitespace();
	}

	public static Parser parse(Parser parser, Consumer<Imyhat> output) {
		return parser//
				.whitespace()//
				.dispatch(DISPATCH, output)//
				.regex(OPTIONAL, IGNORE, "Optional or nothing.")//
				.whitespace();
	}

	private WdlInputType() {

	}
}
