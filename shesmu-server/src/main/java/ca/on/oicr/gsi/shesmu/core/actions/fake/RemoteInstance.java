package ca.on.oicr.gsi.shesmu.core.actions.fake;

import ca.on.oicr.gsi.shesmu.plugin.Definer;
import ca.on.oicr.gsi.shesmu.plugin.Utils;
import ca.on.oicr.gsi.shesmu.plugin.json.JsonBodyHandler;
import ca.on.oicr.gsi.shesmu.plugin.json.JsonParameter;
import ca.on.oicr.gsi.shesmu.plugin.json.JsonPluginFile;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import ca.on.oicr.gsi.status.SectionRenderer;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Pattern;

public class RemoteInstance extends JsonPluginFile<Configuration> {
  private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

  private String allow = ".*";

  private final Definer<RemoteInstance> definer;

  private String url = "<unknown>";

  public RemoteInstance(Path fileName, String instanceName, Definer<RemoteInstance> definer) {
    super(fileName, instanceName, FakeAction.MAPPER, Configuration.class);
    this.definer = definer;
  }

  public void configuration(SectionRenderer renderer) {
    renderer.line("Allow RegEx", allow);
    renderer.link("URL", url, url);
  }

  @Override
  protected Optional<Integer> update(Configuration configuration) {
    url = configuration.getUrl();
    allow = configuration.getAllow();
    final var allow = Pattern.compile(configuration.getAllow());
    definer.clearActions();
    final var request =
        HttpRequest.newBuilder(URI.create(String.format("%s/actions", url))).GET().build();
    try {
      var response =
          HTTP_CLIENT.send(
              request, new JsonBodyHandler<>(RuntimeSupport.MAPPER, ObjectNode[].class));
      for (final var obj : response.body().get()) {
        var name = obj.get("name").asText();
        if (name.equals("nothing") || !allow.matcher(name).matches()) continue;
        definer.defineAction(
            configuration.getPrefix() + name,
            "Fake version of: " + obj.get("description").asText(),
            FakeAction.class,
            () -> new FakeAction(name),
            Utils.stream(obj.get("parameters").elements())
                .map(
                    p ->
                        new JsonParameter<>(
                            p.get("name").asText(),
                            p.get("required").asBoolean(),
                            Imyhat.parse(p.get("type").asText()))));
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    return Optional.of(10);
  }
}
