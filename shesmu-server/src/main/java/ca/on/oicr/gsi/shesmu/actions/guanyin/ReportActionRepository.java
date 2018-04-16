package ca.on.oicr.gsi.shesmu.actions.guanyin;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
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
import ca.on.oicr.gsi.shesmu.Pair;
import ca.on.oicr.gsi.shesmu.RuntimeSupport;

@MetaInfServices(ActionRepository.class)
public class ReportActionRepository implements ActionRepository {
	static final CloseableHttpClient HTTP_CLIENT = HttpClients.createDefault();

	private static Stream<ActionDefinition> queryCatalog(Configuration configuration) {
		try (CloseableHttpResponse response = HTTP_CLIENT
				.execute(new HttpGet(configuration.getGuanyin() + "/reportdb/reports"))) {
			return Arrays.stream(RuntimeSupport.MAPPER.readValue(response.getEntity().getContent(), ReportDto[].class))
					.filter(ReportDto::isValid).map(def -> def.toDefinition(configuration.getGuanyin(),
							configuration.getDrmaa(), configuration.getDrmaaPsk(), configuration.getRootDirectory()));
		} catch (final IOException e) {
			e.printStackTrace();
		}
		return Stream.empty();
	}

	private final List<Configuration> configuration;

	public ReportActionRepository() {
		configuration = RuntimeSupport.dataFiles(Configuration.class, ".guanyin").collect(Collectors.toList());
	}

	@Override
	public Stream<Pair<String, Map<String, String>>> listConfiguration() {
		return configuration.stream().map(configuration -> {
			final Map<String, String> map = new TreeMap<>();
			map.put("drmaa", configuration.getDrmaa());
			map.put("观音", configuration.getGuanyin());
			map.put("directory", configuration.getRootDirectory());
			return new Pair<>("观音 Report Repository", map);
		});
	}

	@Override
	public Stream<ActionDefinition> query() {
		return configuration.stream().flatMap(ReportActionRepository::queryCatalog);
	}
}
