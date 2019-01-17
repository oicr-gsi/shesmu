package ca.on.oicr.gsi.shesmu.core.linker;

import ca.on.oicr.gsi.shesmu.SourceLocation;
import ca.on.oicr.gsi.shesmu.SourceLocationLinker;
import ca.on.oicr.gsi.shesmu.util.AutoUpdatingDirectory;
import ca.on.oicr.gsi.shesmu.util.AutoUpdatingJsonFile;
import ca.on.oicr.gsi.status.ConfigurationSection;
import ca.on.oicr.gsi.status.SectionRenderer;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;
import javax.xml.stream.XMLStreamException;
import org.kohsuke.MetaInfServices;

@MetaInfServices
public class GitSourceLinker implements SourceLocationLinker {
  private class GitLinkerFile extends AutoUpdatingJsonFile<GitConfiguration> {
    Optional<GitConfiguration> config = Optional.empty();

    public GitLinkerFile(Path fileName) {
      super(fileName, GitConfiguration.class);
    }

    ConfigurationSection configuration() {

      return new ConfigurationSection("Git Web Link: " + fileName()) {

        @Override
        public void emit(SectionRenderer renderer) throws XMLStreamException {
          config.ifPresent(
              c -> {
                renderer.line("Prefix", c.getPrefix());
                renderer.link("URL", c.getUrl(), c.getUrl());
                renderer.line("Type", c.getType().name());
              });
        }
      };
    }

    @Override
    protected Optional<Integer> update(GitConfiguration value) {
      config = Optional.of(value);
      return Optional.empty();
    }

    public String url(SourceLocation location) {
      return config
          .map(
              c -> {
                final String prefix = c.getPrefix() + (c.getPrefix().endsWith("/") ? "" : "/");
                if (location.fileName().startsWith(prefix)) {
                  return c.getType()
                      .format(
                          c.getUrl(),
                          location.fileName().substring(prefix.length()),
                          location.line());
                }
                return null;
              })
          .orElse(null);
    }
  }

  private AutoUpdatingDirectory<GitLinkerFile> configurations =
      new AutoUpdatingDirectory<>(".gitlink", GitLinkerFile::new);

  @Override
  public Stream<ConfigurationSection> listConfiguration() {
    return configurations.stream().map(GitLinkerFile::configuration);
  }

  @Override
  public Stream<String> url(SourceLocation location) {
    return configurations.stream().map(x -> x.url(location));
  }
}
