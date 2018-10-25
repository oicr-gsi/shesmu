package ca.on.oicr.gsi.shesmu.core.tsv;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.xml.stream.XMLStreamException;

import ca.on.oicr.gsi.shesmu.FunctionParameter;
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.Imyhat.BaseImyhat;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import ca.on.oicr.gsi.shesmu.util.definitions.FileBackedConfiguration;
import ca.on.oicr.gsi.shesmu.util.definitions.UserDefiner;
import ca.on.oicr.gsi.shesmu.util.definitions.VariadicFunction;
import ca.on.oicr.gsi.status.ConfigurationSection;
import ca.on.oicr.gsi.status.SectionRenderer;
import io.prometheus.client.Gauge;

class TableFunctionFile implements FileBackedConfiguration {

	static final class Table implements VariadicFunction {
		private final List<Object[]> attempts;
		private final Object defaultValue;

		public Table(List<Object[]> attempts, Object defaultValue) {
			super();
			this.attempts = attempts;
			this.defaultValue = defaultValue;
		}

		@Override
		public Object apply(Object... parameters) {
			return attempts.stream()//
					.map(attempt -> IntStream.range(0, parameters.length)//
							.allMatch(i -> attempt[i] == null || attempt[i].equals(parameters[i])) //
									? attempt[attempt.length - 1]
									: null)//
					.filter(Objects::nonNull)//
					.findFirst()//
					.orElse(defaultValue);
		}
	}

	private static final Pattern TAB = Pattern.compile("\t");

	private static final Gauge tableBad = Gauge.build("shesmu_tsv_lookup_bad", "A TSV lookup table is badly formed.")
			.labelNames("fileName").register();

	private final UserDefiner definer;

	private final Path fileName;

	private boolean good;
	private final String name;

	public TableFunctionFile(Path fileName, UserDefiner definer) {
		this.fileName = fileName;
		this.definer = definer;
		name = RuntimeSupport.removeExtension(fileName, TableFunctionRepository.EXTENSION);
	}

	@Override
	public ConfigurationSection configuration() {
		return new ConfigurationSection(fileName.toString()) {

			@Override
			public void emit(SectionRenderer renderer) throws XMLStreamException {
				renderer.line("Is valid?", good ? "Yes" : "No");
			}
		};
	}

	@Override
	public Path fileName() {
		return fileName;
	}

	@Override
	public void start() {
		update();
	}

	@Override
	public void stop() {

	}

	@Override
	public Optional<Integer> update() {
		good = false;
		try {
			final List<String> lines = Files.readAllLines(fileName);

			if (lines.size() < 2) {
				tableBad.labels(fileName.toString()).set(1);
				return Optional.empty();
			}

			final List<BaseImyhat> types = TAB.splitAsStream(lines.get(0)).map(Imyhat::forName)
					.collect(Collectors.toList());
			if (types.size() < 2) {
				tableBad.labels(fileName.toString()).set(1);
				return Optional.empty();
			}

			final List<String[]> grid = lines.stream().skip(1).map(TAB::split).collect(Collectors.toList());

			if (grid.stream().anyMatch(columns -> columns.length != types.size())) {
				tableBad.labels(fileName.toString()).set(1);
				return Optional.empty();
			}

			final List<Object[]> attempts = grid.stream()//
					.<Object[]>map(columns -> {
						final Object[] attempt = new Object[types.size()];
						for (int index = 0; index < columns.length; index++) {
							if (index == columns.length - 1 || !columns[index].equals("*")) {
								attempt[index] = types.get(index).parse(columns[index]);
							}
						}
						return attempt;
					}).collect(Collectors.toList());

			definer.defineFunction(name, String.format("Table-defined lookup from %s.", fileName), types.get(types.size() - 1),
					new Table(attempts, types.get(types.size() - 1).defaultValue()),
					types.stream().limit(types.size() - 1).map(new Function<Imyhat, FunctionParameter>() {
						int index;

						@Override
						public FunctionParameter apply(Imyhat type) {
							return new FunctionParameter(String.format("arg%d", ++index), type);
						}
					})//
							.toArray(FunctionParameter[]::new));
			tableBad.labels(fileName.toString()).set(0);
			good = true;
		} catch (final IOException e) {
			e.printStackTrace();
			return Optional.empty();
		}

		return Optional.empty();

	}
}