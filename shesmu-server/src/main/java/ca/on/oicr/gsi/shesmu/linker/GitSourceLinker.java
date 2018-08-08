package ca.on.oicr.gsi.shesmu.linker;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;

import org.kohsuke.MetaInfServices;

import ca.on.oicr.gsi.shesmu.AutoUpdatingDirectory;
import ca.on.oicr.gsi.shesmu.AutoUpdatingJsonFile;
import ca.on.oicr.gsi.shesmu.Pair;
import ca.on.oicr.gsi.shesmu.SourceLocation;
import ca.on.oicr.gsi.shesmu.SourceLocationLinker;

@MetaInfServices
public class GitSourceLinker implements SourceLocationLinker {
	private class GitLinkerFile extends AutoUpdatingJsonFile<GitConfiguration> {
		Optional<GitConfiguration> config = Optional.empty();

		public GitLinkerFile(Path fileName) {
			super(fileName, GitConfiguration.class);
		}

		Pair<String, Map<String, String>> configuration() {
			Map<String, String> properties = new TreeMap<>();
			config.ifPresent(c -> {
				properties.put("prefix", c.getPrefix());
				properties.put("url", c.getUrl());
				properties.put("type", c.getType().name());

			});
			return new Pair<>("Git Web Link: " + fileName(), properties);
		}

		@Override
		protected Optional<Integer> update(GitConfiguration value) {
			config = Optional.of(value);
			return Optional.empty();
		}

		public String url(SourceLocation location) {
			return config.map(c -> {
				final String prefix = c.getPrefix() + (c.getPrefix().endsWith("/") ? "" : "/");
				if (location.fileName().startsWith(prefix)) {
					return c.getType().format(c.getUrl(), location.fileName().substring(prefix.length()),
							location.line());
				}
				return null;
			}).orElse(null);
		}
	}

	private AutoUpdatingDirectory<GitLinkerFile> configurations = new AutoUpdatingDirectory<>(".gitlink",
			GitLinkerFile::new);

	@Override
	public Stream<Pair<String, Map<String, String>>> listConfiguration() {
		return configurations.stream().map(GitLinkerFile::configuration);
	}

	@Override
	public Stream<String> url(SourceLocation location) {
		return configurations.stream().map(x -> x.url(location));
	}

}
