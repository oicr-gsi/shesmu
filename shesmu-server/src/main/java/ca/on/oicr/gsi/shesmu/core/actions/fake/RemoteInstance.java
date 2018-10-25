package ca.on.oicr.gsi.shesmu.core.actions.fake;

import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Pattern;

import javax.xml.stream.XMLStreamException;

import ca.on.oicr.gsi.shesmu.ActionParameterDefinition;
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.compiler.Check;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import ca.on.oicr.gsi.shesmu.util.AutoUpdatingJsonFile;
import ca.on.oicr.gsi.shesmu.util.definitions.FileBackedConfiguration;
import ca.on.oicr.gsi.shesmu.util.definitions.JsonParameter;
import ca.on.oicr.gsi.shesmu.util.definitions.UserDefiner;
import ca.on.oicr.gsi.status.ConfigurationSection;
import ca.on.oicr.gsi.status.SectionRenderer;

public class RemoteInstance extends AutoUpdatingJsonFile<Configuration> implements FileBackedConfiguration {

	private String allow = ".*";

	private String url = "<unknown>";

	private final UserDefiner definer;

	public RemoteInstance(Path fileName, UserDefiner definer) {
		super(fileName, Configuration.class);
		this.definer = definer;
	}

	public ConfigurationSection configuration() {
		return new ConfigurationSection(fileName().toString()) {

			@Override
			public void emit(SectionRenderer renderer) throws XMLStreamException {
				renderer.line("Allow RegEx", allow);
				renderer.link("URL", url, url);
			}
		};
	}

	@Override
	protected Optional<Integer> update(Configuration configuration) {
		url = configuration.getUrl();
		allow = configuration.getAllow();
		final Pattern allow = Pattern.compile(configuration.getAllow());
		definer.clearActions();
		Check.fetch(configuration.getUrl(), "actions")//
				.filter(obj -> !obj.get("name").asText().equals("nothing")
						&& allow.matcher(obj.get("name").asText()).matches())//
				.forEach(obj -> {
					String name = obj.get("name").asText();

					definer.defineAction(name, //
							"Fake version of: " + obj.get("description").asText(), //
							FakeAction.class, //
							() -> new FakeAction(name), //
							RuntimeSupport.stream(obj.get("parameters").elements())//
									.<ActionParameterDefinition>map(p -> new JsonParameter(//
											p.get("name").asText(), //
											Imyhat.parse(p.get("type").asText()), //
											p.get("required").asBoolean())));
				});
		return Optional.of(60);
	}
}