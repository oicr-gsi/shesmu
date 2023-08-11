package ca.on.oicr.gsi.shesmu.github;

import ca.on.oicr.gsi.shesmu.plugin.Definer;
import ca.on.oicr.gsi.shesmu.plugin.PluginFileType;
import ca.on.oicr.gsi.shesmu.plugin.cache.ReplacingRecord;
import ca.on.oicr.gsi.shesmu.plugin.cache.ValueCache;
import ca.on.oicr.gsi.shesmu.plugin.input.ShesmuInputSource;
import ca.on.oicr.gsi.shesmu.plugin.json.JsonBodyHandler;
import ca.on.oicr.gsi.shesmu.plugin.json.JsonPluginFile;
import ca.on.oicr.gsi.status.SectionRenderer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.stream.Stream;
import org.kohsuke.MetaInfServices;

@MetaInfServices
public class GitHubBranchesApiPluginType
    extends PluginFileType<GitHubBranchesApiPluginType.GitHubRemote> {
  static class GitHubRemote extends JsonPluginFile<Configuration> {
    private class BranchCache
        extends ValueCache<Stream<GithubBranchValue>, Stream<GithubBranchValue>> {

      public BranchCache(Path fileName) {
        super("github-branches " + fileName.toString(), 10, ReplacingRecord::new);
      }

      @Override
      protected Stream<GithubBranchValue> fetch(Instant lastUpdated) throws Exception {
        if (configuration.isEmpty()) return Stream.empty();
        final var c = configuration.get();
        var response =
            HTTP_CLIENT.send(
                HttpRequest.newBuilder(
                        URI.create(
                            String.format(
                                "https://api.github.com/repos/%s/%s/branches",
                                c.getOwner(), c.getRepo())))
                    .GET()
                    .build(),
                new JsonBodyHandler<>(MAPPER, BranchResponse[].class));
        // TODO: If this input format comes into use, convert to use ErrorableStream
        return Stream.of(response.body().get()) //
            .map(
                r ->
                    new GithubBranchValue() {

                      @Override
                      public String branch() {
                        return r.getName();
                      }

                      @Override
                      public String commit() {
                        return r.getCommit().getSha();
                      }

                      @Override
                      public String owner() {
                        return c.getOwner();
                      }

                      @Override
                      public String repository() {
                        return c.getRepo();
                      }
                    });
      }
    }

    public void configuration(SectionRenderer renderer) {
      configuration.ifPresent(
          c -> {
            renderer.line("Owner", c.getOwner());
            renderer.line("Repository", c.getRepo());
          });
    }

    @ShesmuInputSource
    public Stream<GithubBranchValue> stream(boolean readStale) {
      return readStale ? cache.getStale() : cache.get();
    }

    @Override
    public Optional<Integer> update(Configuration value) {
      configuration = Optional.of(value);
      cache.invalidate();
      return Optional.empty();
    }

    private final BranchCache cache;
    private Optional<Configuration> configuration = Optional.empty();

    public GitHubRemote(Path fileName, String instanceName) {
      super(fileName, instanceName, MAPPER, Configuration.class);
      cache = new BranchCache(fileName);
    }
  }

  public static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Override
  public GitHubRemote create(Path filePath, String instanceName, Definer<GitHubRemote> definer) {
    return new GitHubRemote(filePath, instanceName);
  }

  public GitHubBranchesApiPluginType() {
    super(MethodHandles.lookup(), GitHubRemote.class, ".github", "github");
  }
}
