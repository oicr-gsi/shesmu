package ca.on.oicr.gsi.shesmu.compiler;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.stream.Collectors;

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
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.Lookup;
import ca.on.oicr.gsi.shesmu.NameLoader;
import ca.on.oicr.gsi.shesmu.actions.rest.FileActionRepository;
import ca.on.oicr.gsi.shesmu.lookup.TsvLookupRepository;

/**
 * The command-line compiler for Shesmu scripts
 */
public final class Build extends Compiler implements AutoCloseable {

	public static void main(String[] args) {
		final Options options = new Options();
		options.addOption("h", "help", false, "This dreck.");
		options.addOption("x", "nocompute", false, "Don't automatically compute MAXS and FRAMES.");
		options.addOption("v", "variables", false, "List all the variables known in the base stream.");
		options.addOption("d", "dump", false, "Dump loaded actions and lookups in output.");
		options.addOption("l", "lookups", true,
				"The directory containing the JSON and TSV files for lookups. If not supplied, the path in the environment variable SHESMU_DATA is used.");
		options.addOption("a", "actions", true,
				"A colon-separated list of directories containing action definition files. If none are found, SHESMU_DATA is used instead.");
		final CommandLineParser parser = new DefaultParser();
		Optional<String> actionPath = FileActionRepository.environmentVariable();
		Optional<String> lookupPath = TsvLookupRepository.environmentVariable();
		String file;
		boolean dump;
		boolean skipCompute;
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
				NameDefinitions.baseStreamVariables()//
						.sorted((a, b) -> a.name().compareTo(b.name()))//
						.forEach(variable -> System.out.printf("\t%s :: %s (%s)\n", variable.name(),
								variable.type().name(), variable.type().signature()));
			}
			if (cmd.hasOption('l')) {
				lookupPath = Optional.ofNullable(cmd.getOptionValue('l'));
			}
			if (cmd.hasOption('a')) {
				actionPath = Optional.ofNullable(cmd.getOptionValue('a'));
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
		try (Build compiler = new Build(new NameLoader<>(TsvLookupRepository.of(lookupPath), Lookup::name),
				new NameLoader<>(FileActionRepository.of(actionPath), ActionDefinition::name), skipCompute, true)) {
			if (dump) {
				compiler.dump();
			}
			final boolean ok = compiler.compile(Files.readAllBytes(Paths.get(file)), "dyn/shesmu/Program", file);
			System.exit(ok ? 0 : 1);
		} catch (final Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	private final NameLoader<ActionDefinition> actions;

	private final boolean dataFlowAnalysis;

	private final NameLoader<Lookup> lookups;

	private final Printer printer = new Textifier();

	private final boolean skipCompute;

	private final PrintWriter writer = new PrintWriter(System.out);

	private Build(NameLoader<Lookup> lookups, NameLoader<ActionDefinition> actions, boolean skipCompute,
			boolean dataFlowAnalysis) {
		this.lookups = lookups;
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
		lookups.all().forEach(lookup -> {
			System.out.printf("Lookup: %s[%s] %s\n", lookup.name(),
					lookup.types().map(Imyhat::name).collect(Collectors.joining(", ")), lookup.returnType().name());

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
	protected Lookup getLookup(String lookup) {
		return lookups.get(lookup);
	}

}
