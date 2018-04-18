package ca.on.oicr.gsi.shesmu.actions.fake;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.kohsuke.MetaInfServices;

import ca.on.oicr.gsi.shesmu.ActionDefinition;
import ca.on.oicr.gsi.shesmu.ActionRepository;
import ca.on.oicr.gsi.shesmu.AutoUpdatingFile;
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.Pair;
import ca.on.oicr.gsi.shesmu.ParameterDefinition;
import ca.on.oicr.gsi.shesmu.RuntimeSupport;
import ca.on.oicr.gsi.shesmu.actions.util.JsonParameter;
import ca.on.oicr.gsi.shesmu.compiler.Check;
import io.prometheus.client.Gauge;

@MetaInfServices
public class FakeActionSource implements ActionRepository {

	private class RemoteInstance extends AutoUpdatingFile {

		private String allow = ".*";

		private List<ActionDefinition> items = Collections.emptyList();
		private String url = "<unknown>";

		public RemoteInstance(Path fileName) {
			super(fileName);
		}

		public Pair<String, Map<String, String>> configuration() {
			final Map<String, String> properties = new TreeMap<>();
			properties.put("allow regex", allow);
			properties.put("url", url);
			properties.put("filename", fileName().toString());
			return new Pair<>("Fake Actions from Remote Server", properties);
		}

		public Stream<ActionDefinition> stream() {
			return items.stream();
		}

		@Override
		protected void update() {
			try {
				final Configuration configuration = RuntimeSupport.MAPPER.readValue(Files.readAllBytes(fileName()),
						Configuration.class);
				url = configuration.getUrl();
				allow = configuration.getAllow();
				final Pattern allow = Pattern.compile(configuration.getAllow());
				items = Check.fetch(configuration.getUrl(), "actions")//
						.filter(obj -> allow.matcher(obj.get("name").asText()).matches())//
						.map(obj -> new FakeActionDefinition(obj.get("name").asText(),
								RuntimeSupport.stream(obj.get("parameters").elements())
										.<ParameterDefinition>map(p -> new JsonParameter(p.get("name").asText(), //
												Imyhat.parse(p.get("type").asText()), //
												p.get("required").asBoolean()))))
						.collect(Collectors.toList());
				badConfig.labels(fileName().toString()).set(1);
			} catch (final Exception e) {
				e.printStackTrace();
				badConfig.labels(fileName().toString()).set(0);
			}
		}
	}

	private static final Gauge badConfig = Gauge
			.build("shesmu_fake_actions_good_config",
					"Whether a configuration file that mirrors fake actions from a remote server is valid.")
			.labelNames("filename").register();

	private final List<RemoteInstance> instances;

	public FakeActionSource() {
		instances = RuntimeSupport.dataFiles(".fakeactions").map(RemoteInstance::new).collect(Collectors.toList());
	}

	@Override
	public Stream<Pair<String, Map<String, String>>> listConfiguration() {
		return instances.stream().map(RemoteInstance::configuration);
	}

	@Override
	public Stream<ActionDefinition> query() {
		return instances.stream().flatMap(RemoteInstance::stream);
	}

}
