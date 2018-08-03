package ca.on.oicr.gsi.shesmu.compiler;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.util.CheckClassAdapter;
import org.objectweb.asm.util.Printer;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceClassVisitor;

import ca.on.oicr.gsi.shesmu.ActionDefinition;
import ca.on.oicr.gsi.shesmu.ConstantSource;
import ca.on.oicr.gsi.shesmu.FileWatcher;
import ca.on.oicr.gsi.shesmu.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.InputFormatDefinition;
import ca.on.oicr.gsi.shesmu.NameLoader;
import ca.on.oicr.gsi.shesmu.RuntimeSupport;
import ca.on.oicr.gsi.shesmu.actions.rest.FileActionRepository;
import ca.on.oicr.gsi.shesmu.function.TableFunctionRepository;

/**
 * The command-line compiler for Shesmu scripts
 */
public final class Build extends Compiler implements AutoCloseable {

	public static void main(String[] args) {
		final Options options = new Options();
		options.addOption("h", "help", false, "This dreck.");
		options.addOption("x", "nocompute", false, "Don't automatically compute MAXS and FRAMES.");
		options.addOption("v", "variables", false, "List all the variables known in the base stream.");
		options.addOption("d", "dump", false, "Dump loaded actions and functions in output.");
		options.addOption("D", "data", true,
				"The directory containing the JSON and TSV files for functions and actions. If not supplied, the path in the environment variable SHESMU_DATA is used.");
		final CommandLineParser parser = new DefaultParser();
		String file;
		boolean dump;
		boolean skipCompute;
		Optional<String> dataDirectory = RuntimeSupport.environmentVariable();
		try {
			final CommandLine cmd = parser.parse(options, args);

			if (cmd.hasOption("h")) {
				final HelpFormatter formatter = new HelpFormatter();
				formatter.printHelp("Shesmu Compiler", options);
				System.exit(0);
				return;
			}
			if (cmd.hasOption('v')) {
				System.out.println("Stream variables:");
				InputFormatDefinition.formats().forEach(format -> {
					System.out.printf("\t%s :: %s\n", format.name(), format.itemClass().getCanonicalName());
					format.baseStreamVariables()//
							.sorted((a, b) -> a.name().compareTo(b.name()))//
							.forEach(variable -> System.out.printf("\t%s :: %s (%s)\n", variable.name(),
									variable.type().name(), variable.type().signature()));
				});

			}
			if (cmd.hasOption('D')) {
				dataDirectory = Optional.ofNullable(cmd.getOptionValue('D'));
			}
			if (cmd.getArgs().length != 1) {
				System.err.println("Exactly one file must be specified to compile.");
				System.exit(1);
				return;
			}
			file = cmd.getArgs()[0];
			dump = cmd.hasOption('d');
			skipCompute = cmd.hasOption('x');
		} catch (final ParseException e) {
			System.err.println(e.getMessage());
			System.exit(1);
			return;
		}
		final FileWatcher watcher = FileWatcher
				.of(dataDirectory.map(RuntimeSupport::parsePaths).orElseGet(() -> Stream.of(Paths.get("."))));
		try (Build compiler = new Build(new NameLoader<>(InputFormatDefinition.formats(), InputFormatDefinition::name),
				new NameLoader<>(new TableFunctionRepository(watcher).queryFunctions(), FunctionDefinition::name),
				new NameLoader<>(new FileActionRepository(watcher).queryActions(), ActionDefinition::name), skipCompute,
				true)) {
			if (dump) {
				compiler.dump();
			}
			final boolean ok = compiler.compile(Files.readAllBytes(Paths.get(file)), "dyn/shesmu/Program", file,
					ConstantSource::all);
			System.exit(ok ? 0 : 1);
		} catch (final Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	private final NameLoader<ActionDefinition> actions;

	private final boolean dataFlowAnalysis;

	private final NameLoader<FunctionDefinition> functions;
	private final NameLoader<InputFormatDefinition> inputFormats;

	private final Printer printer = new Textifier();

	private final boolean skipCompute;

	private final PrintWriter writer = new PrintWriter(System.out);

	private Build(NameLoader<InputFormatDefinition> inputFormats, NameLoader<FunctionDefinition> functions,
			NameLoader<ActionDefinition> actions, boolean skipCompute, boolean dataFlowAnalysis) {
		super(false);
		this.inputFormats = inputFormats;
		this.functions = functions;
		this.actions = actions;
		this.skipCompute = skipCompute;
		this.dataFlowAnalysis = dataFlowAnalysis;
	}

	@Override
	public void close() throws Exception {
		writer.close();
	}

	@Override
	protected ClassVisitor createClassVisitor() {
		final ClassWriter outputWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
		return new TraceClassVisitor(skipCompute ? new ClassVisitor(Opcodes.ASM5) {
		} : new ClassVisitor(Opcodes.ASM5, outputWriter) {

			@Override
			public void visitEnd() {
				super.visitEnd();
				final ClassReader reader = new ClassReader(outputWriter.toByteArray());
				final CheckClassAdapter check = new CheckClassAdapter(new ClassWriter(0), dataFlowAnalysis);
				reader.accept(check, 0);
			}
		}, printer, writer);
	}

	private void dump() {
		functions.all().forEach(function -> {
			System.out.printf("Function: %s(%s) %s\n", function.name(),
					function.parameters().map(p -> p.type().name()).collect(Collectors.joining(", ")),
					function.returnType().name());

			System.out.println();
		});

		actions.all().forEach(actionDefinition -> {
			System.out.printf("Action: %s\n", actionDefinition.name());
			actionDefinition.parameters()
					.forEach(parameter -> System.out.printf("\t%s :: %s\n", parameter.name(), parameter.type().name()));
			System.out.println();
		});
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

	@Override
	protected InputFormatDefinition getInputFormats(String name) {
		return inputFormats.get(name);
	}

}
