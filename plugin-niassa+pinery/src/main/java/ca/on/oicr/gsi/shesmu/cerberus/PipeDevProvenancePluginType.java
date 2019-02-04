package ca.on.oicr.gsi.shesmu.cerberus;

import ca.on.oicr.gsi.provenance.DefaultProvenanceClient;
import ca.on.oicr.gsi.provenance.ProviderLoader;
import ca.on.oicr.gsi.provenance.model.FileProvenance;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import ca.on.oicr.gsi.shesmu.plugin.PluginFileType;
import ca.on.oicr.gsi.shesmu.plugin.Utils;
import org.kohsuke.MetaInfServices;

@MetaInfServices(PluginFileType.class)
public class PipeDevProvenancePluginType extends BaseProvenancePluginType<DefaultProvenanceClient> {

  private static <T> void setProvider(Map<String, T> source, BiConsumer<String, T> consumer) {
    source.entrySet().stream().forEach(entry -> consumer.accept(entry.getKey(), entry.getValue()));
  }

  public PipeDevProvenancePluginType() {
    super("pipedev", ".pipedev");
  }

  @Override
  protected DefaultProvenanceClient createClient(Path fileName) throws Exception {
    final DefaultProvenanceClient client = new DefaultProvenanceClient();
    final ProviderLoader loader = new ProviderLoader(new String(Files.readAllBytes(fileName)));
    setProvider(
        loader.getAnalysisProvenanceProviders(), client::registerAnalysisProvenanceProvider);
    setProvider(loader.getLaneProvenanceProviders(), client::registerLaneProvenanceProvider);
    setProvider(loader.getSampleProvenanceProviders(), client::registerSampleProvenanceProvider);
    return client;
  }

  @Override
  protected Stream<? extends FileProvenance> fetch(DefaultProvenanceClient client) {
    return Utils.stream(client.getFileProvenance(PROVENANCE_FILTER));
  }
}
