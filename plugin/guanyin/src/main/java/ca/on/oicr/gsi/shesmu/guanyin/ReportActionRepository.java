package ca.on.oicr.gsi.shesmu.guanyin;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.xml.stream.XMLStreamException;

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

/**
 * Converts Guanyin reports into actions
 */
@MetaInfServices(ActionRepository.class)
public class ReportActionRepository implements ActionRepository {
	private class GuanyinFile extends AutoUpdatingJsonFile<Configuration> {
		private List<ActionDefinition> actions = Collections.emptyList();

		private Optional<Configuration> configuration = Optional.empty();

		public GuanyinFile(Path fileName) {
			super(fileName, Configuration.class);
		}

		public ConfigurationSection configuration() {
			return new ConfigurationSection("观音 Report Repository: " + fileName()) {

				@Override
				public void emit(SectionRenderer renderer) throws XMLStreamException {
					configuration.ifPresent(configuration -> {
						renderer.link("DRMAAWS", configuration.getDrmaa(), configuration.getDrmaa());
						renderer.link("观音", configuration.getGuanyin(), configuration.getGuanyin());
						renderer.line("Script", configuration.getScript());
					});
				}
			};

		}

		public Stream<ActionDefinition> stream() {
			return actions.stream();
		}

		@Override
		protected Optional<Integer> update(Configuration configuration) {
			try (CloseableHttpResponse response = HTTP_CLIENT
					.execute(new HttpGet(configuration.getGuanyin() + "/reportdb/reports"))) {
				actions = Stream
						.of(RuntimeSupport.MAPPER.readValue(response.getEntity().getContent(), ReportDto[].class))//
						.filter(ReportDto::isValid)//
						.<ActionDefinition>map(def -> def.toDefinition(configuration.getGuanyin(),
								configuration.getDrmaa(), configuration.getDrmaaPsk(), configuration.getScript()))//
						.collect(Collectors.toList());
				this.configuration = Optional.of(configuration);
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
	public Stream<ConfigurationSection> listConfiguration() {
		return configurations.stream().map(GuanyinFile::configuration);
	}

	@Override
	public Stream<ActionDefinition> queryActions() {
		return configurations.stream().flatMap(GuanyinFile::stream);
	}

	@Override
	public void writeJavaScriptRenderer(PrintStream writer) {
		writer.print(
				"actionRender.set('guanyin-report', a => [title(a, `${a.reportName} – 观音 Report ${a.reportId}`)].concat(jsonParameters(a)));");
	}
}
