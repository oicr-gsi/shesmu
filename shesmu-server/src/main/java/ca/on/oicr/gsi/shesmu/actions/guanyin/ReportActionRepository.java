package ca.on.oicr.gsi.shesmu.actions.guanyin;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

/**
 * Converts Guanyin reports into actions
 */
@MetaInfServices(ActionRepository.class)
public class ReportActionRepository implements ActionRepository {
	private class GuanyinFile extends AutoUpdatingJsonFile<Configuration> {
		private List<ActionDefinition> actions = Collections.emptyList();

		private final Map<String, String> map = new TreeMap<>();

		public GuanyinFile(Path fileName) {
			super(fileName, Configuration.class);
		}

		public Pair<String, Map<String, String>> configuration() {
			return new Pair<>("观音 Report Repository: " + fileName(), map);

		}

		public Stream<ActionDefinition> stream() {
			return actions.stream();
		}

		@Override
		protected Optional<Integer> update(Configuration configuration) {
			map.put("drmaa", configuration.getDrmaa());
			map.put("观音", configuration.getGuanyin());
			map.put("script", configuration.getScript());
			try (CloseableHttpResponse response = HTTP_CLIENT
					.execute(new HttpGet(configuration.getGuanyin() + "/reportdb/reports"))) {
				actions = Stream
						.of(RuntimeSupport.MAPPER.readValue(response.getEntity().getContent(), ReportDto[].class))//
						.filter(ReportDto::isValid)//
						.<ActionDefinition>map(def -> def.toDefinition(configuration.getGuanyin(),
								configuration.getDrmaa(), configuration.getDrmaaPsk(), configuration.getScript()))//
						.collect(Collectors.toList());
				return Optional.empty();
			} catch (final IOException e) {
				e.printStackTrace();
				return Optional.of(5);
			}
		}

	}

	static final CloseableHttpClient HTTP_CLIENT = HttpClients.createDefault();

	private final AutoUpdatingDirectory<GuanyinFile> configurations;

	public ReportActionRepository() {
		configurations = new AutoUpdatingDirectory<>(".guanyin", GuanyinFile::new);
	}

	@Override
	public Stream<Pair<String, Map<String, String>>> listConfiguration() {
		return configurations.stream().map(GuanyinFile::configuration);
	}

	@Override
	public Stream<ActionDefinition> queryActions() {
		return configurations.stream().flatMap(GuanyinFile::stream);
	}

	@Override
	public void writeJavaScriptRenderer(PrintStream writer) {
		writer.print("actionRender.set('guanyin-report', a => [title(a, `Run Report ${a.reportId}`)].concat(jsonParameters(a)));");
	}
}
