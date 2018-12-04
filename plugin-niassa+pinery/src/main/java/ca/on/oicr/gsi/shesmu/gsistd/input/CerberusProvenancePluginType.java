package ca.on.oicr.gsi.shesmu.gsistd.input;

import ca.on.oicr.gsi.cerberus.client.CerberusClient;
import ca.on.oicr.gsi.provenance.model.FileProvenance;
import ca.on.oicr.gsi.shesmu.plugin.PluginFileType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.kohsuke.MetaInfServices;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

@MetaInfServices(PluginFileType.class)
public class CerberusProvenancePluginType extends BaseProvenancePluginType<CerberusClient> {

  public static class CerberusConfiguration {
    private String url;

    public String getUrl() {
      return url;
    }

    public void setUrl(String url) {
      this.url = url;
    }
  }

  private static final ObjectMapper MAPPER = new ObjectMapper();

  public CerberusProvenancePluginType() {
    super("cerberus", ".cerberus");
  }

  @Override
  protected CerberusClient createClient(Path fileName) throws Exception {
    final CerberusConfiguration configuration =
        MAPPER.readValue(Files.readAllBytes(fileName), CerberusConfiguration.class);
    return new CerberusClient(new URI(configuration.getUrl()));
  }

  @Override
  protected Stream<? extends FileProvenance> fetch(CerberusClient client) {
    return client.getFileProvenance(PROVENANCE_FILTER).stream();
  }
}
