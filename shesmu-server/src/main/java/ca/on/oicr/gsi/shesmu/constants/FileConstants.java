package ca.on.oicr.gsi.shesmu.constants;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.kohsuke.MetaInfServices;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ca.on.oicr.gsi.shesmu.AutoUpdatingFile;
import ca.on.oicr.gsi.shesmu.Constant;
import ca.on.oicr.gsi.shesmu.ConstantSource;
import ca.on.oicr.gsi.shesmu.Pair;
import ca.on.oicr.gsi.shesmu.RuntimeSupport;

@MetaInfServices(ConstantSource.class)
public class FileConstants implements ConstantSource {

	private class ConstantsFile extends AutoUpdatingFile {
		private List<Constant> constants = Collections.emptyList();

		public ConstantsFile(Path fileName) {
			super(fileName);
		}

		public Stream<Pair<String, String>> pairs() {
			return constants.stream().map(constant -> new Pair<>(constant.name(), fileName().toString()));
		}

		public Stream<Constant> stream() {
			return constants.stream();
		}

		@Override
		protected void update() {
			try {
				constants = StreamSupport
						.stream(Spliterators
								.spliteratorUnknownSize(
										RuntimeSupport.MAPPER
												.readValue(Files.readAllBytes(fileName()), ObjectNode.class).fields(),
										Spliterator.DISTINCT),
								false)
						.map(FileConstants::convert)//
						.filter(Objects::nonNull)//
						.collect(Collectors.toList());
			} catch (final Exception e) {
				constants = Collections.emptyList();
			}

		}
	}

	private static Constant convert(Map.Entry<String, JsonNode> entry) {
		if (entry.getValue().isBoolean()) {
			return Constant.of(entry.getKey(), entry.getValue().asBoolean());
		}
		if (entry.getValue().isIntegralNumber()) {
			return Constant.of(entry.getKey(), entry.getValue().asLong());
		}
		if (entry.getValue().isTextual()) {
			return Constant.of(entry.getKey(), entry.getValue().asText());
		}
		return null;
	}

	private final List<ConstantsFile> files;

	public FileConstants() {
		files = RuntimeSupport.dataFiles(".constants").map(ConstantsFile::new).collect(Collectors.toList());
	}

	@Override
	public Stream<Pair<String, Map<String, String>>> listConfiguration() {
		return Stream.of(new Pair<>("Constants", files.stream().flatMap(ConstantsFile::pairs)
				.collect(Collectors.toMap(Pair::first, Pair::second, (a, b) -> a))));
	}

	@Override
	public Stream<Constant> stream() {
		return files.stream().flatMap(ConstantsFile::stream);
	}

}
