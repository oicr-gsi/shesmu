package ca.on.oicr.gsi.shesmu.pinery;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.xml.stream.XMLStreamException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ca.on.oicr.gsi.shesmu.util.AutoUpdatingJsonFile;
import ca.on.oicr.gsi.shesmu.util.Cache;
import ca.on.oicr.gsi.shesmu.util.definitions.FileBackedConfiguration;
import ca.on.oicr.gsi.shesmu.util.definitions.ShesmuMethod;
import ca.on.oicr.gsi.status.ConfigurationSection;
import ca.on.oicr.gsi.status.SectionRenderer;
import ca.on.oicr.pinery.client.HttpResponseException;
import ca.on.oicr.pinery.client.PineryClient;
import ca.on.oicr.ws.dto.SampleProjectDto;

public class PinerySource extends AutoUpdatingJsonFile<ObjectNode> implements FileBackedConfiguration {
	private static final Cache<String, List<SampleProjectDto>> PROJECTS = new Cache<String, List<SampleProjectDto>>(
			"pinery_projects", 3600) {

		@Override
		protected List<SampleProjectDto> fetch(String url) throws IOException {
			try (final PineryClient client = new PineryClient(url)) {
				return client.getSampleProject().all();
			} catch (final HttpResponseException e) {
				e.printStackTrace();
				return null;
			}
		}

	};

	private Optional<String> url = Optional.empty();

	public PinerySource(Path fileName) {
		super(fileName, ObjectNode.class);
	}

	@ShesmuMethod(name = "$_active_projects", type = "as", description = "Projects marked active from in Pinery defined in {file}.")
	public Set<String> activeProjects() {
		return url.flatMap(PROJECTS::get).orElse(Collections.emptyList())//
				.stream()//
				.filter(SampleProjectDto::isActive)//
				.map(SampleProjectDto::getName)//
				.collect(Collectors.toSet());
	}

	@ShesmuMethod(name = "$_projects", type = "as", description = "All projects from in Pinery defined in {file}.")
	public Set<String> allProjects() {
		return url.flatMap(PROJECTS::get).orElse(Collections.emptyList())//
				.stream()//
				.map(SampleProjectDto::getName)//
				.collect(Collectors.toSet());
	}

	@Override
	public ConfigurationSection configuration() {
		return new ConfigurationSection("Pinery Functions: " + fileName()) {

			@Override
			public void emit(SectionRenderer renderer) throws XMLStreamException {
				url.ifPresent(u -> renderer.link("URL", u, u));
			}
		};
	}

	@Override
	protected Optional<Integer> update(ObjectNode value) {
		url = Optional.ofNullable(value.get("url")).map(JsonNode::asText);
		return Optional.empty();
	}
}
