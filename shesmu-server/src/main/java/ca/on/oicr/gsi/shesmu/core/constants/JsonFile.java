package ca.on.oicr.gsi.shesmu.core.constants;

import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.xml.stream.XMLStreamException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import ca.on.oicr.gsi.shesmu.util.AutoUpdatingJsonFile;
import ca.on.oicr.gsi.shesmu.util.definitions.FileBackedConfiguration;
import ca.on.oicr.gsi.shesmu.util.definitions.UserDefiner;
import ca.on.oicr.gsi.status.ConfigurationSection;
import ca.on.oicr.gsi.status.SectionRenderer;

public class JsonFile extends AutoUpdatingJsonFile<ObjectNode> implements FileBackedConfiguration {

	private static Pair<Imyhat, Object> convert(JsonNode value) {
		if (value.isBoolean()) {
			return new Pair<>(Imyhat.BOOLEAN, value.asBoolean());
		}
		if (value.isIntegralNumber()) {
			return new Pair<>(Imyhat.INTEGER, value.asLong());
		}
		if (value.isTextual()) {
			return new Pair<>(Imyhat.STRING, value.asText());
		}
		if (value.isArray()) {
			if (value.size() == 0) {
				return null;
			}
			Imyhat type;
			Function<JsonNode, Object> converter;
			if (value.get(0).isBoolean()) {
				type = Imyhat.BOOLEAN;
				converter = JsonNode::asBoolean;
			} else if (value.get(0).isIntegralNumber()) {
				type = Imyhat.INTEGER;
				converter = JsonNode::asLong;
			} else if (value.get(0).isTextual()) {
				type = Imyhat.STRING;
				converter = JsonNode::asText;
			} else {
				return null;
			}
			return new Pair<>(type.asList(),
					RuntimeSupport.stream(value.elements()).map(converter).collect(Collectors.toSet()));
		}
		return null;
	}

	private final Set<String> badKeys = new ConcurrentSkipListSet<>();

	private final UserDefiner definer;

	public JsonFile(Path fileName, UserDefiner definer) {
		super(fileName, ObjectNode.class);
		this.definer = definer;
	}

	@Override
	public ConfigurationSection configuration() {
		return new ConfigurationSection(fileName().toString()) {

			@Override
			public void emit(SectionRenderer renderer) throws XMLStreamException {
				if (!badKeys.isEmpty()) {
					renderer.line("Bad keys", badKeys.stream()//
							.collect(Collectors.joining(", ")));
				}
			}
		};
	}

	@Override
	protected Optional<Integer> update(ObjectNode node) {
		final String description = String.format("User-defined value specified in %s.", fileName());
		definer.clearConstants();
		badKeys.clear();
		RuntimeSupport.stream(node.fields())//
				.forEach(e -> {
					final Pair<Imyhat, Object> constant = convert(e.getValue());
					if (constant != null) {
						definer.defineConstant(e.getKey(), description, constant.first(), constant.second());
					} else {
						badKeys.add(e.getKey());
					}
				});
		return Optional.empty();
	}
}