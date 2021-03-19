package ca.on.oicr.gsi.shesmu.core.actions.fake;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.Definer;
import ca.on.oicr.gsi.shesmu.plugin.SupplementaryInformation;
import ca.on.oicr.gsi.shesmu.plugin.Utils;
import ca.on.oicr.gsi.shesmu.plugin.json.JsonParameter;
import ca.on.oicr.gsi.shesmu.plugin.json.JsonPluginFile;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import ca.on.oicr.gsi.status.SectionRenderer;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

public class RemoteInstance extends JsonPluginFile<Configuration> {
  private static final CloseableHttpClient HTTP_CLIENT = HttpClients.createDefault();

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
    final var request = new HttpGet(String.format("%s/actions", url));
    try (var response = HTTP_CLIENT.execute(request)) {
      for (final var obj :
          RuntimeSupport.MAPPER.readValue(response.getEntity().getContent(), ObjectNode[].class)) {
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
                            Imyhat.parse(p.get("type").asText()))),
            new SupplementaryInformation() {
              final String country = response.getLocale().getDisplayCountry();

              @Override
              public Stream<Pair<DisplayElement, DisplayElement>> generate() {
                return Stream.of(
                    new Pair<>(
                        SupplementaryInformation.text("Remote Country"),
                        SupplementaryInformation.text(country)));
              }
            });
      }
    } catch (final Exception e) {
      e.printStackTrace();
    }
    return Optional.of(10);
  }
}
