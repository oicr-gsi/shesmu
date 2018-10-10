package ca.on.oicr.gsi.shesmu.core.actions.fake;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.xml.stream.XMLStreamException;

import org.kohsuke.MetaInfServices;

import ca.on.oicr.gsi.shesmu.ActionDefinition;
import ca.on.oicr.gsi.shesmu.ActionRepository;
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.ParameterDefinition;
import ca.on.oicr.gsi.shesmu.compiler.Check;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import ca.on.oicr.gsi.shesmu.util.AutoUpdatingDirectory;
import ca.on.oicr.gsi.shesmu.util.AutoUpdatingJsonFile;
import ca.on.oicr.gsi.shesmu.util.actions.JsonParameter;
import ca.on.oicr.gsi.status.ConfigurationSection;
import ca.on.oicr.gsi.status.SectionRenderer;

/**
 * Create actions that mirror the actions of an existing Shesmu instance, but do
 * nothing when executed
 *
 * This is for preparation of development servers
 */
@MetaInfServices
public class FakeActionSource implements ActionRepository {

	private class RemoteInstance extends AutoUpdatingJsonFile<Configuration> {

		private String allow = ".*";

		private List<ActionDefinition> items = Collections.emptyList();
		private String url = "<unknown>";

		public RemoteInstance(Path fileName) {
			super(fileName, Configuration.class);
		}

		public ConfigurationSection configuration() {
			return new ConfigurationSection("Fake Actions from Remote Server: " + fileName()) {

				@Override
				public void emit(SectionRenderer renderer) throws XMLStreamException {
					renderer.line("Allow RegEx", allow);
					renderer.link("URL", url, url);
				}
			};
		}

		public Stream<ActionDefinition> stream() {
			return items.stream();
		}

		@Override
		protected Optional<Integer> update(Configuration configuration) {
			url = configuration.getUrl();
			allow = configuration.getAllow();
			final Pattern allow = Pattern.compile(configuration.getAllow());
			items = Check.fetch(configuration.getUrl(), "actions")//
					.filter(obj -> !obj.get("name").asText().equals("nothing")
							&& allow.matcher(obj.get("name").asText()).matches())//
					.map(obj -> new FakeActionDefinition(obj.get("name").asText(), obj.get("description").asText(),
							RuntimeSupport.stream(obj.get("parameters").elements())
									.<ParameterDefinition>map(p -> new JsonParameter(p.get("name").asText(), //
											Imyhat.parse(p.get("type").asText()), //
											p.get("required").asBoolean()))))
					.collect(Collectors.toList());
			return Optional.empty();
		}
	}

	private final AutoUpdatingDirectory<RemoteInstance> instances;

	public FakeActionSource() {
		instances = new AutoUpdatingDirectory<>(".fakeactions", RemoteInstance::new);
	}

	@Override
	public Stream<ConfigurationSection> listConfiguration() {
		return instances.stream().map(RemoteInstance::configuration);
	}

	@Override
	public Stream<ActionDefinition> queryActions() {
		return instances.stream().flatMap(RemoteInstance::stream);
	}

	@Override
	public void writeJavaScriptRenderer(PrintStream writer) {
		writer.print("actionRender.set('fake', a => [title(a, `Fake ${a.name}`)].concat(jsonParameters(a)));");
	}

}
