package ca.on.oicr.gsi.shesmu.core.constants;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.xml.stream.XMLStreamException;

import org.kohsuke.MetaInfServices;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ca.on.oicr.gsi.shesmu.Constant;
import ca.on.oicr.gsi.shesmu.ConstantSource;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import ca.on.oicr.gsi.shesmu.util.AutoUpdatingDirectory;
import ca.on.oicr.gsi.shesmu.util.AutoUpdatingJsonFile;
import ca.on.oicr.gsi.status.ConfigurationSection;
import ca.on.oicr.gsi.status.SectionRenderer;

/**
 * Read constants from JSON files (and automatically reparse those files if they
 * change)
 */
@MetaInfServices(ConstantSource.class)
public class JsonFileConstants implements ConstantSource {

	private class ConstantsFile extends AutoUpdatingJsonFile<ObjectNode> {
		private List<Constant> constants = Collections.emptyList();

		public ConstantsFile(Path fileName) {
			super(fileName, ObjectNode.class);
		}

		public Stream<Constant> stream() {
			return constants.stream();
		}

		@Override
		protected Optional<Integer> update(ObjectNode node) {
			final String description = String.format("User-defined value specified in %s.", fileName());
			constants = RuntimeSupport.stream(node.fields())//
					.map(e -> convert(e, description))//
					.filter(Objects::nonNull)//
					.collect(Collectors.toList());
			return Optional.empty();
		}
	}

	private static Constant convert(Map.Entry<String, JsonNode> entry, String description) {
		if (entry.getValue().isBoolean()) {
			return Constant.of(entry.getKey(), entry.getValue().asBoolean(), description);
		}
		if (entry.getValue().isIntegralNumber()) {
			return Constant.of(entry.getKey(), entry.getValue().asLong(), description);
		}
		if (entry.getValue().isTextual()) {
			return Constant.of(entry.getKey(), entry.getValue().asText(), description);
		}
		if (entry.getValue().isArray()) {
			if (entry.getValue().size() == 0) {
				return null;
			}
			if (entry.getValue().get(0).isBoolean()) {
				return Constant.ofBooleans(entry.getKey(),
						RuntimeSupport.stream(entry.getValue().elements()).map(JsonNode::asBoolean), description);
			}
			if (entry.getValue().get(0).isIntegralNumber()) {
				return Constant.ofLongs(entry.getKey(),
						RuntimeSupport.stream(entry.getValue().elements()).map(JsonNode::asLong), description);
			}
			if (entry.getValue().get(0).isTextual()) {
				return Constant.ofStrings(entry.getKey(),
						RuntimeSupport.stream(entry.getValue().elements()).map(JsonNode::asText), description);
			}
		}
		return null;
	}

	private final AutoUpdatingDirectory<ConstantsFile> files;

	public JsonFileConstants() {
		files = new AutoUpdatingDirectory<>(".constants", ConstantsFile::new);
	}

	@Override
	public Stream<ConfigurationSection> listConfiguration() {
		return Stream.of(new ConfigurationSection("Constants") {

			@Override
			public void emit(SectionRenderer renderer) throws XMLStreamException {
				files.stream()//
						.sorted(Comparator.comparing(ConstantsFile::fileName))//
						.forEach(f -> renderer.line(f.fileName().toString(), f.constants.size()));
			}
		});
	}

	@Override
	public Stream<? extends Constant> queryConstants() {
		return files.stream().flatMap(ConstantsFile::stream);
	}

}
