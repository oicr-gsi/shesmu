package ca.on.oicr.gsi.shesmu;

import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.prometheus.client.Gauge;

/**
 * Compiles a user-specified file into a usable program and updates it as
 * necessary
 */
public class CompiledGenerator extends AutoUpdatingFile {

	private static final Gauge compileTime = Gauge
			.build("shesmu_source_compile_time", "The number of seconds the last compilation took to perform.")
			.labelNames("filename").register();

	private static final Gauge sourceValid = Gauge
			.build("shesmu_source_valid", "Whether the source file has been successfully compiled.")
			.labelNames("filename").register();
	private final Supplier<Stream<ActionDefinition>> actions;

	private final Supplier<Stream<Constant>> constants;

	private String errors = "Not yet compiled or exception during compilation.";

	private ActionGenerator generator = ActionGenerator.NULL;

	private final Supplier<Stream<Lookup>> lookups;

	public CompiledGenerator(Path fileName, Supplier<Stream<Lookup>> lookups,
			Supplier<Stream<ActionDefinition>> actions, Supplier<Stream<Constant>> constants) {
		super(fileName);
		this.lookups = lookups;
		this.actions = actions;
		this.constants = constants;
	}

	private void compile() {
		final HotloadingCompiler compiler = new HotloadingCompiler(lookups, actions, constants);
		final Optional<ActionGenerator> result = compiler.compile(fileName());
		sourceValid.labels(fileName().toString()).set(result.isPresent() ? 1 : 0);
		result.ifPresent(x -> {
			if (generator != x) {
				generator.unregister();
				x.register();
				generator = x;
			}
		});
		errors = compiler.errors().collect(Collectors.joining("<br/>"));
	}

	public String errorHtml() {
		return errors;
	}

	/**
	 * Get the last successfully compiled action generator.
	 */
	public ActionGenerator generator() {
		return generator;
	}

	@Override
	protected void update() {
		compileTime.labels(fileName().toString()).setToTime(this::compile);
	}

}
