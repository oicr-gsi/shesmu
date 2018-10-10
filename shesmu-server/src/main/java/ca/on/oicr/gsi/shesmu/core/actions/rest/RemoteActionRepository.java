package ca.on.oicr.gsi.shesmu.core.actions.rest;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Stream;

import javax.xml.stream.XMLStreamException;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.kohsuke.MetaInfServices;

import ca.on.oicr.gsi.shesmu.ActionDefinition;
import ca.on.oicr.gsi.shesmu.ActionRepository;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import ca.on.oicr.gsi.shesmu.util.AutoUpdatingDirectory;
import ca.on.oicr.gsi.shesmu.util.AutoUpdatingJsonFile;
import ca.on.oicr.gsi.status.ConfigurationSection;
import ca.on.oicr.gsi.status.SectionRenderer;

@MetaInfServices
public final class RemoteActionRepository implements ActionRepository {

	private class Remote extends AutoUpdatingJsonFile<Configuration> {

		private String url = null;

		public Remote(Path fileName) {
			super(fileName, Configuration.class);
		}

		private ConfigurationSection configuration() {
			return new ConfigurationSection("Remote Action Repository: " + fileName()) {

				@Override
				public void emit(SectionRenderer renderer) throws XMLStreamException {
					if (url != null) {
						renderer.link("URL", url, url);
					}
				}
			};
		}

		private Stream<ActionDefinition> queryActionsCatalog() {
			if (url == null) {
				return Stream.empty();
			}
			try (CloseableHttpResponse response = HTTP_CLIENT.execute(new HttpGet(url + "/actioncatalog"))) {
				return Arrays
						.stream(RuntimeSupport.MAPPER.readValue(response.getEntity().getContent(), Definition[].class))
						.map(def -> def.toDefinition(url));
			} catch (final ClientProtocolException e) {
				e.printStackTrace();
			} catch (final IOException e) {
				e.printStackTrace();
			}
			return Stream.empty();
		}

		@Override
		protected Optional<Integer> update(Configuration value) {
			url = value.getUrl();
			return Optional.empty();
		}

	}

	private static final CloseableHttpClient HTTP_CLIENT = HttpClients.createDefault();

	private final AutoUpdatingDirectory<Remote> configurations = new AutoUpdatingDirectory<>(".remote", Remote::new);

	@Override
	public Stream<ConfigurationSection> listConfiguration() {
		return configurations.stream().map(Remote::configuration);
	}

	@Override
	public Stream<ActionDefinition> queryActions() {
		return configurations.stream().flatMap(Remote::queryActionsCatalog);
	}

	@Override
	public void writeJavaScriptRenderer(PrintStream writer) {
		writer.print(
				"actionRender.set('remote-action', a => [title(a, `${a.name} on ${a.target}`)].concat(jsonParameters(a)));");
	}

}
