package ca.on.oicr.gsi.shesmu.pinery;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.xml.stream.XMLStreamException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ca.on.oicr.gsi.shesmu.util.AutoUpdatingJsonFile;
import ca.on.oicr.gsi.shesmu.util.cache.MergingRecord;
import ca.on.oicr.gsi.shesmu.util.cache.ValueCache;
import ca.on.oicr.gsi.shesmu.util.definitions.FileBackedConfiguration;
import ca.on.oicr.gsi.shesmu.util.definitions.ShesmuMethod;
import ca.on.oicr.gsi.status.ConfigurationSection;
import ca.on.oicr.gsi.status.SectionRenderer;
import ca.on.oicr.pinery.client.PineryClient;
import ca.on.oicr.ws.dto.SampleProjectDto;

public class PinerySource extends AutoUpdatingJsonFile<ObjectNode> implements FileBackedConfiguration {
	private class ProjectCache extends ValueCache<Stream<SampleProjectDto>> {

		public ProjectCache(Path fileName) {
			super("pinery_projects " + fileName.toString(), 3600, MergingRecord.by(SampleProjectDto::getName));
		}

		@Override
		protected Stream<SampleProjectDto> fetch(Instant lastUpdated) throws Exception {
			if (!url.isPresent())
				return Stream.empty();
			try (final PineryClient client = new PineryClient(url.get())) {
				return client.getSampleProject().all().stream();
			}
		}

	};

	private Optional<String> url = Optional.empty();
	private final ProjectCache projects;

	public PinerySource(Path fileName) {
		super(fileName, ObjectNode.class);
		projects = new ProjectCache(fileName);
	}

	@ShesmuMethod(name = "$_active_projects", type = "as", description = "Projects marked active from in Pinery defined in {file}.")
	public Set<String> activeProjects() {
		return projects.get()//
				.filter(SampleProjectDto::isActive)//
				.map(SampleProjectDto::getName)//
				.collect(Collectors.toSet());
	}

	@ShesmuMethod(name = "$_projects", type = "as", description = "All projects from in Pinery defined in {file}.")
	public Set<String> allProjects() {
		return projects.get()//
				.map(SampleProjectDto::getName)//
				.collect(Collectors.toSet());
	}

	@Override
	public ConfigurationSection configuration() {
		return new ConfigurationSection(fileName().toString()) {

			@Override
			public void emit(SectionRenderer renderer) throws XMLStreamException {
				url.ifPresent(u -> renderer.link("URL", u, u));
			}
		};
	}

	@Override
	protected Optional<Integer> update(ObjectNode value) {
		url = Optional.ofNullable(value.get("url")).map(JsonNode::asText);
		projects.invalidate();
		return Optional.empty();
	}
}
