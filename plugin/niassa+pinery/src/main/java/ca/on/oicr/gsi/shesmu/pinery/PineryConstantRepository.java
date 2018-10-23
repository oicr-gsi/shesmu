package ca.on.oicr.gsi.shesmu.pinery;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.xml.stream.XMLStreamException;

import org.kohsuke.MetaInfServices;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ca.on.oicr.gsi.shesmu.Constant;
import ca.on.oicr.gsi.shesmu.ConstantSource;
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeInterop;
import ca.on.oicr.gsi.shesmu.util.AutoUpdatingDirectory;
import ca.on.oicr.gsi.shesmu.util.AutoUpdatingJsonFile;
import ca.on.oicr.gsi.shesmu.util.Cache;
import ca.on.oicr.gsi.shesmu.util.RuntimeBinding;
import ca.on.oicr.gsi.status.ConfigurationSection;
import ca.on.oicr.gsi.status.SectionRenderer;
import ca.on.oicr.pinery.client.HttpResponseException;
import ca.on.oicr.pinery.client.PineryClient;
import ca.on.oicr.ws.dto.SampleProjectDto;

@MetaInfServices
public class PineryConstantRepository implements ConstantSource {
	public class PinerySource extends AutoUpdatingJsonFile<ObjectNode> {
		private final List<Constant> constants;

		private Optional<String> url = Optional.empty();

		public PinerySource(Path fileName) {
			super(fileName, ObjectNode.class);
			constants = RUNTIME_BINDER.bindConstants(this);

		}

		@RuntimeInterop
		public Set<String> activeProjects() {
			return url.flatMap(projects::get).orElse(Collections.emptyList())//
					.stream()//
					.filter(SampleProjectDto::isActive)//
					.map(SampleProjectDto::getName)//
					.collect(Collectors.toSet());
		}

		@RuntimeInterop
		public Set<String> allProjects() {
			return url.flatMap(projects::get).orElse(Collections.emptyList())//
					.stream()//
					.map(SampleProjectDto::getName)//
					.collect(Collectors.toSet());
		}

		ConfigurationSection configuration() {
			return new ConfigurationSection("Pinery Functions: " + fileName()) {

				@Override
				public void emit(SectionRenderer renderer) throws XMLStreamException {
					url.ifPresent(u -> renderer.link("URL", u, u));
				}
			};
		}

		public Stream<? extends Constant> queryConstants() {
			return constants.stream();
		}

		@Override
		protected Optional<Integer> update(ObjectNode value) {
			url = Optional.ofNullable(value.get("url")).map(JsonNode::asText);
			return Optional.empty();
		}
	}

	private static final RuntimeBinding<PinerySource> RUNTIME_BINDER = new RuntimeBinding<>(PinerySource.class,
			RemotePineryIUSRepository.EXTENSION)//
					.constant("%s_active_projects", "activeProjects", Imyhat.STRING.asList(),
							"Projects marked active from in Pinery defined in %2$s.")//
					.constant("%s_projects", "allProjects", Imyhat.STRING.asList(),
							"All projects from in Pinery defined in %2$s.");

	private final Cache<String, List<SampleProjectDto>> projects = new Cache<String, List<SampleProjectDto>>(
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

	private final AutoUpdatingDirectory<PinerySource> sources;

	public PineryConstantRepository() {
		sources = new AutoUpdatingDirectory<>(RemotePineryIUSRepository.EXTENSION, PinerySource::new);
	}

	@Override
	public Stream<ConfigurationSection> listConfiguration() {
		return sources.stream().map(PinerySource::configuration);
	}

	@Override
	public Stream<? extends Constant> queryConstants() {
		return sources.stream().flatMap(PinerySource::queryConstants);
	}

}
