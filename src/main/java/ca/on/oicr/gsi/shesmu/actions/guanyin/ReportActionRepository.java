package ca.on.oicr.gsi.shesmu.actions.guanyin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.kohsuke.MetaInfServices;

import ca.on.oicr.gsi.shesmu.ActionDefinition;
import ca.on.oicr.gsi.shesmu.ActionRepository;
import ca.on.oicr.gsi.shesmu.Pair;
import ca.on.oicr.gsi.shesmu.RuntimeSupport;

@MetaInfServices(ActionRepository.class)
public class ReportActionRepository implements ActionRepository {
	static final CloseableHttpClient HTTP_CLIENT = HttpClients.createDefault();

	private static final Pattern INVALID_CATEGORY = Pattern.compile("^pinery-report.*$");
	private static final String VARIABLE = "GUANYIN_CONFIG";

	private static Stream<ActionDefinition> queryCatalog(Configuration configuration) {
		try (CloseableHttpResponse response = HTTP_CLIENT
				.execute(new HttpGet(configuration.getGuanyin() + "/reportdb/reports"))) {
			return Arrays.stream(RuntimeSupport.MAPPER.readValue(response.getEntity().getContent(), ReportDto[].class))
					.filter(report -> !INVALID_CATEGORY.matcher(report.getCategory()).matches())
					.map(def -> def.toDefinition(configuration.getGuanyin(), configuration.getDrmaa()));
		} catch (final IOException e) {
			e.printStackTrace();
		}
		return Stream.empty();
	}

	private final Optional<Configuration> configuration;

	public ReportActionRepository() {
		configuration = Optional.ofNullable(System.getenv(VARIABLE))//
				.map(Paths::get)//
				.filter(Files::isReadable)//
				.flatMap(path -> {
					try {
						return Optional
								.of(RuntimeSupport.MAPPER.readValue(Files.readAllBytes(path), Configuration.class));
					} catch (final IOException e) {
						e.printStackTrace();
						return Optional.empty();
					}
				});
	}

	@Override
	public Stream<Pair<String, Map<String, String>>> listConfiguration() {
		return configuration.map(configuration -> {
			final Map<String, String> map = new TreeMap<>();
			map.put("drmaa", configuration.getDrmaa());
			map.put("觀音", configuration.getGuanyin());
			return Stream.of(new Pair<>("觀音 Report Repository", map));
		}).orElse(Stream.empty());
	}

	@Override
	public Stream<ActionDefinition> query() {
		return configuration.map(ReportActionRepository::queryCatalog).orElseGet(Stream::empty);
	}
}
