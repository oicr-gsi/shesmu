package ca.on.oicr.gsi.shesmu.compiler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.commons.GeneratorAdapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ca.on.oicr.gsi.shesmu.ActionDefinition;
import ca.on.oicr.gsi.shesmu.Constant;
import ca.on.oicr.gsi.shesmu.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.NameLoader;
import ca.on.oicr.gsi.shesmu.ParameterDefinition;
import ca.on.oicr.gsi.shesmu.RuntimeSupport;

/**
 * The command-line checker for Shesmu scripts
 *
 * This talks to a running Shesmu server and uses the actions, functions, and
 * constants it knows to validate a script. This cannot compile the script, so
 * no bytecode generation is attempted.
 */
public final class Check extends Compiler {
	static final CloseableHttpClient HTTP_CLIENT = HttpClients.createDefault();

	public static Stream<ObjectNode> fetch(String remote, String slug) {
		final HttpGet request = new HttpGet(String.format("%s/%s", remote, slug));
		try (CloseableHttpResponse response = HTTP_CLIENT.execute(request)) {
			return Arrays
					.stream(RuntimeSupport.MAPPER.readValue(response.getEntity().getContent(), ObjectNode[].class));
		} catch (final Exception e) {
			e.printStackTrace();
			return Stream.empty();
		}
	}

	public static void main(String[] args) {
		final Options options = new Options();
		options.addOption("h", "help", false, "This dreck.");
		options.addOption("r", "remote", true, "The remote instance with all the actions/functions/etc.");
		final CommandLineParser parser = new DefaultParser();
		String file;
		String remote = "http://localhost:8081/";
		try {
			final CommandLine cmd = parser.parse(options, args);

			if (cmd.hasOption("h")) {
				final HelpFormatter formatter = new HelpFormatter();
				formatter.printHelp("Shesmu Compiler", options);
				System.exit(0);
				return;
			}
			if (cmd.hasOption('r')) {
				remote = cmd.getOptionValue('r');
			}
			if (cmd.getArgs().length != 1) {
				System.err.println("Exactly one file must be specified to compile.");
				System.exit(1);
				return;
			}
			file = cmd.getArgs()[0];
		} catch (final ParseException e) {
			System.err.println(e.getMessage());
			System.exit(1);
			return;
		}
		final List<Constant> constants = fetch(remote, "constants")//
				.map(o -> new Constant(o.get("name").asText(), Imyhat.parse(o.get("type").asText()),
						o.get("description").asText()) {

					@Override
					protected void load(GeneratorAdapter methodGen) {
						throw new UnsupportedOperationException();
					}
				})//
				.collect(Collectors.toList());
		final NameLoader<FunctionDefinition> functions = new NameLoader<>(
				fetch(remote, "functions").map(Check::makeFunction), FunctionDefinition::name);
		final NameLoader<ActionDefinition> actions = new NameLoader<>(fetch(remote, "actions").map(Check::makeAction),
				ActionDefinition::name);

		try {
			final boolean ok = new Check(functions, actions).compile(Files.readAllBytes(Paths.get(file)),
					"dyn/shesmu/Program", file, () -> constants.stream());
			System.exit(ok ? 0 : 1);
		} catch (final IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	private static ActionDefinition makeAction(ObjectNode node) {
		return new ActionDefinition(node.get("name").asText(), null,
				RuntimeSupport.stream(node.get("parameters").elements()).map(Check::makeParameter)) {

			@Override
			public void initialize(GeneratorAdapter methodGen) {
				throw new UnsupportedOperationException();
			}
		};

	}

	private static FunctionDefinition makeFunction(ObjectNode node) {
		final String name = node.get("name").asText();
		final String description = node.get("description").asText();
		final Imyhat returnType = Imyhat.parse(node.get("return").asText());
		final Imyhat[] types = RuntimeSupport.stream(node.get("types").elements()).map(JsonNode::asText)
				.map(Imyhat::parse).toArray(Imyhat[]::new);
		return new FunctionDefinition() {

			@Override
			public String description() {
				return description;
			}

			@Override
			public String name() {
				return name;
			}

			@Override
			public void render(GeneratorAdapter methodGen) {
				throw new UnsupportedOperationException();
			}

			@Override
			public Imyhat returnType() {
				return returnType;
			}

			@Override
			public Stream<Imyhat> types() {
				return Arrays.stream(types);
			}
		};
	}

	private static ParameterDefinition makeParameter(JsonNode node) {
		final String name = node.get("name").asText();
		final Imyhat type = Imyhat.parse(node.get("type").asText());
		final boolean required = node.get("required").asBoolean();
		return new ParameterDefinition() {

			@Override
			public String name() {
				return name;
			}

			@Override
			public boolean required() {
				return required;
			}

			@Override
			public void store(Renderer renderer, int actionLocal, Consumer<Renderer> loadParameter) {
				throw new UnsupportedOperationException();
			}

			@Override
			public Imyhat type() {
				return type;
			}
		};
	}

	private final NameLoader<ActionDefinition> actions;

	private final NameLoader<FunctionDefinition> functions;

	private Check(NameLoader<FunctionDefinition> functions, NameLoader<ActionDefinition> actions) {
		super(true);
		this.functions = functions;
		this.actions = actions;
	}

	@Override
	protected ClassVisitor createClassVisitor() {
		throw new UnsupportedOperationException();
	}

	@Override
	protected void errorHandler(String message) {
		System.out.println(message);
	}

	@Override
	protected ActionDefinition getAction(String name) {
		return actions.get(name);
	}

	@Override
	protected FunctionDefinition getFunction(String function) {
		return functions.get(function);
	}

}
