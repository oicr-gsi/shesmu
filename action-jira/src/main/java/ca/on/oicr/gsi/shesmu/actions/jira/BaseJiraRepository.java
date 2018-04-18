package ca.on.oicr.gsi.shesmu.actions.jira;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import ca.on.oicr.gsi.shesmu.AutoUpdatingJsonFile;
import ca.on.oicr.gsi.shesmu.LoadedConfiguration;
import ca.on.oicr.gsi.shesmu.Pair;
import ca.on.oicr.gsi.shesmu.RuntimeSupport;

public abstract class BaseJiraRepository<T> implements LoadedConfiguration {

	private class JiraConfig extends AutoUpdatingJsonFile<Configuration> {

		private final Map<String, String> properties = new TreeMap<>();

		private final Pair<String, Map<String, String>> status;

		private List<T> value;

		public JiraConfig(Path fileName) {
			super(fileName, Configuration.class);
			properties.put("filename", fileName.toString());
			status = new Pair<>(name, properties);
		}

		public Pair<String, Map<String, String>> status() {
			return status;
		}

		public Stream<T> stream() {
			return value.stream();
		}

		@Override
		protected void update(Configuration config) {
			value = create(config).collect(Collectors.toList());
			properties.put("instance", config.getName());
			properties.put("project", config.getProjectKey());
			properties.put("url", config.getUrl());
		}
	}

	private final List<JiraConfig> configurations;

	private final String name;

	public BaseJiraRepository(String name) {
		this.name = name;
		configurations = RuntimeSupport.dataFiles(".jira").map(JiraConfig::new).peek(JiraConfig::start)
				.collect(Collectors.toList());
	}

	protected abstract Stream<T> create(Configuration config);

	@Override
	public final Stream<Pair<String, Map<String, String>>> listConfiguration() {
		return configurations.stream().map(JiraConfig::status);
	}

	protected final Stream<T> stream() {
		return configurations.stream().flatMap(JiraConfig::stream);
	}

}