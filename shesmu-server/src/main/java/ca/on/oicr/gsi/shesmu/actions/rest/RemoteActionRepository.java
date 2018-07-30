package ca.on.oicr.gsi.shesmu.actions.rest;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.kohsuke.MetaInfServices;

import ca.on.oicr.gsi.shesmu.ActionDefinition;
import ca.on.oicr.gsi.shesmu.ActionRepository;
import ca.on.oicr.gsi.shesmu.AutoUpdatingDirectory;
import ca.on.oicr.gsi.shesmu.AutoUpdatingJsonFile;
import ca.on.oicr.gsi.shesmu.Pair;
import ca.on.oicr.gsi.shesmu.RuntimeSupport;

@MetaInfServices
public final class RemoteActionRepository implements ActionRepository {

	private class Remote extends AutoUpdatingJsonFile<Configuration> {

		private String url = null;

		public Remote(Path fileName) {
			super(fileName, Configuration.class);
		}

		private Pair<String, Map<String, String>> configuration() {
			final Map<String, String> map = new TreeMap<>();
			map.put("url", url);
			map.put("file", fileName().toString());
			return new Pair<>("Remote Action Repository", map);
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
	public Stream<Pair<String, Map<String, String>>> listConfiguration() {
		return configurations.stream().map(Remote::configuration);
	}

	@Override
	public Stream<ActionDefinition> queryActions() {
		return configurations.stream().flatMap(Remote::queryActionsCatalog);
	}

	@Override
	public void writeJavaScriptRenderer(PrintStream writer) {
		writer.print("actionRender.set('remote-action', a => [title(`${a.name} on ${a.target}`)].concat(jsonParameters(a)));");
	}

}
