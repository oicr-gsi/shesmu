package ca.on.oicr.gsi.shesmu.gitlink;

import ca.on.oicr.gsi.shesmu.plugin.Definer;
import ca.on.oicr.gsi.shesmu.plugin.PluginFileType;
import ca.on.oicr.gsi.shesmu.plugin.json.JsonPluginFile;
import ca.on.oicr.gsi.status.SectionRenderer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;
import javax.xml.stream.XMLStreamException;
import org.kohsuke.MetaInfServices;

@MetaInfServices
public class GitSourceLinker extends PluginFileType<GitSourceLinker.GitLinkerFile> {
  public GitSourceLinker() {
    super(MethodHandles.lookup(), GitLinkerFile.class, ".gitlink", "git");
  }

  private static final ObjectMapper MAPPER = new ObjectMapper();

  class GitLinkerFile extends JsonPluginFile<GitConfiguration> {
    Optional<GitConfiguration> config = Optional.empty();

    public GitLinkerFile(Path fileName, String instanceName) {
      super(fileName, instanceName, MAPPER, GitConfiguration.class);
    }

    public void configuration(SectionRenderer renderer) throws XMLStreamException {
      config.ifPresent(
          c -> {
            renderer.line("Prefix", c.getPrefix());
            renderer.link("URL", c.getUrl(), c.getUrl());
            renderer.line("Type", c.getType().name());
          });
    }

    @Override
    protected Optional<Integer> update(GitConfiguration value) {
      config = Optional.of(value);
      return Optional.empty();
    }

    @Override
    public Stream<String> sourceUrl(String localFilePath, int line, int column, String hash) {
      return config
          .<Stream<String>>map(
              c -> {
                final String prefix = c.getPrefix() + (c.getPrefix().endsWith("/") ? "" : "/");
                if (localFilePath.startsWith(prefix)) {
                  return Stream.of(
                      c.getType()
                          .format(c.getUrl(), localFilePath.substring(prefix.length()), line));
                }
                return Stream.empty();
              })
          .orElseGet(Stream::empty);
    }
  }

  @Override
  public GitLinkerFile create(Path filePath, String instanceName, Definer definer) {
    return new GitLinkerFile(filePath, instanceName);
  }
}
