package ca.on.oicr.gsi.shesmu.compiler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ca.on.oicr.gsi.shesmu.ActionDefinition;
import ca.on.oicr.gsi.shesmu.Constant;
import ca.on.oicr.gsi.shesmu.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.FunctionParameter;
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.InputFormatDefinition;
import ca.on.oicr.gsi.shesmu.LoadedConfiguration;
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
		return fetch(remote, slug, ObjectNode[].class).map(Stream::of).orElse(Stream.empty());
	}

	public static <T> Optional<T> fetch(String remote, String slug, Class<T> clazz) {
		final HttpGet request = new HttpGet(String.format("%s/%s", remote, slug));
		try (CloseableHttpResponse response = HTTP_CLIENT.execute(request)) {
			return Optional.of(RuntimeSupport.MAPPER.readValue(response.getEntity().getContent(), clazz));
		} catch (final Exception e) {
			e.printStackTrace();
			return Optional.empty();
		}
	}

	public static void main(String[] args) {
		final Options options = new Options();
		options.addOption("h", "help", false, "This dreck.");
		options.addOption("r", "remote", true, "The remote instance with all the actions/functions/etc.");
		final CommandLineParser parser = new DefaultParser();
		String[] files;
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
			if (cmd.getArgs().length == 0) {
				System.err.println("At least one file must be specified to compile.");
				System.exit(1);
				return;
			}
			files = cmd.getArgs();
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
		final NameLoader<InputFormatDefinition> inputFormats = new NameLoader<>(
				fetch(remote, "variables", ObjectNode.class).map(Check::makeInputFormat).orElse(Stream.empty()),
				InputFormatDefinition::name);
		final NameLoader<FunctionDefinition> functions = new NameLoader<>(
				fetch(remote, "functions").map(Check::makeFunction), FunctionDefinition::name);
		final NameLoader<ActionDefinition> actions = new NameLoader<>(fetch(remote, "actions").map(Check::makeAction),
				ActionDefinition::name);

		final boolean ok = Stream.of(files).allMatch(file -> {
			boolean fileOk;
			try {
				fileOk = new Check(file, inputFormats, functions, actions).compile(Files.readAllBytes(Paths.get(file)),
						"dyn/shesmu/Program", file, constants::stream, null);
			} catch (final IOException e) {
				e.printStackTrace();
				fileOk = false;
			}
			System.err.printf("%s\033[0m\t%s\n", fileOk ? "\033[1;36mOK" : "\033[1;31mFAIL", file);
			return fileOk;
		});
		System.exit(ok ? 0 : 1);
	}

	private static ActionDefinition makeAction(ObjectNode node) {
		return new ActionDefinition(node.get("name").asText(), null, node.get("description").asText(),
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
		final FunctionParameter[] parameters = RuntimeSupport.stream(node.get("parameters").elements())
				.map(p -> new FunctionParameter(p.get("name").asText(), Imyhat.parse(p.get("type").asText())))
				.toArray(FunctionParameter[]::new);
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
			public Stream<FunctionParameter> parameters() {
				return Arrays.stream(parameters);
			}

			@Override
			public void render(GeneratorAdapter methodGen) {
				throw new UnsupportedOperationException();
			}

			@Override
			public final void renderStart(GeneratorAdapter methodGen) {
				// None required.
			}

			@Override
			public Imyhat returnType() {
				return returnType;
			}
		};
	}

	private static Stream<InputFormatDefinition> makeInputFormat(ObjectNode node) {
		return RuntimeSupport.stream(node.fields()).map(pair -> {
			final List<Target> targets = RuntimeSupport.stream(pair.getValue().fields()).map(field -> {
				final String name = field.getKey();
				final Imyhat type = Imyhat.parse(field.getValue().asText());
				return new Target() {

					@Override
					public Flavour flavour() {
						return Flavour.STREAM;
					}

					@Override
					public String name() {
						return name;
					}

					@Override
					public Imyhat type() {
						return type;
					}
				};
			}).collect(Collectors.toList());

			return new InputFormatDefinition(pair.getKey()) {

				@Override
				public Stream<Target> baseStreamVariables() {
					return targets.stream();
				}

				@Override
				public Stream<LoadedConfiguration> configuration() {
					return Stream.empty();
				}

				@Override
				public <T> Stream<T> input(Class<T> clazz) {
					return Stream.empty();
				}

				@Override
				public Class<?> itemClass() {
					return Object.class;
				}

				@Override
				public void write(JsonGenerator generator) throws IOException {
					throw new UnsupportedOperationException();
				}
			};
		});

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

	private final String fileName;
	private final NameLoader<FunctionDefinition> functions;

	private final NameLoader<InputFormatDefinition> inputFormats;

	private Check(String fileName, NameLoader<InputFormatDefinition> inputFormats,
			NameLoader<FunctionDefinition> functions, NameLoader<ActionDefinition> actions) {
		super(true);
		this.fileName = fileName;
		this.inputFormats = inputFormats;
		this.functions = functions;
		this.actions = actions;
	}

	@Override
	protected ClassVisitor createClassVisitor() {
		throw new UnsupportedOperationException();
	}

	@Override
	protected void errorHandler(String message) {
		System.out.printf("%s:%s\n", fileName, message);
	}

	@Override
	protected ActionDefinition getAction(String name) {
		return actions.get(name);
	}

	@Override
	protected FunctionDefinition getFunction(String function) {
		return functions.get(function);
	}

	@Override
	protected InputFormatDefinition getInputFormats(String name) {
		return inputFormats.get(name);
	}

}
