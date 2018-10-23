package ca.on.oicr.gsi.shesmu.sftp;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import javax.xml.stream.XMLStreamException;

import org.kohsuke.MetaInfServices;

import ca.on.oicr.gsi.shesmu.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.FunctionParameter;
import ca.on.oicr.gsi.shesmu.FunctionRepository;
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeInterop;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import ca.on.oicr.gsi.shesmu.util.AutoUpdatingDirectory;
import ca.on.oicr.gsi.shesmu.util.AutoUpdatingJsonFile;
import ca.on.oicr.gsi.shesmu.util.Cache;
import ca.on.oicr.gsi.shesmu.util.RuntimeBinding;
import ca.on.oicr.gsi.status.ConfigurationSection;
import ca.on.oicr.gsi.status.SectionRenderer;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.sftp.FileAttributes;
import net.schmizz.sshj.sftp.SFTPClient;

@MetaInfServices
public class SftpFunctionRepository implements FunctionRepository {
	public final class SftpServer extends AutoUpdatingJsonFile<Configuration> {
		private Instant backoff = Instant.EPOCH;

		private SSHClient client;

		private Optional<Configuration> configuration = Optional.empty();

		private final List<FunctionDefinition> definitions;

		private final Cache<String, FileAttributes> fileAttributes = new Cache<String, FileAttributes>("sftp", 10) {

			@Override
			protected FileAttributes fetch(String fileName) throws IOException {
				return sftp == null ? null : sftp.statExistence(fileName);
			}
		};

		private final String service;

		private volatile SFTPClient sftp;

		public SftpServer(Path fileName) {
			super(fileName, Configuration.class);
			service = RuntimeSupport.removeExtension(fileName, EXTENSION);

			definitions = RUNTIME_BINDING.bindFunctions(this);
		}

		private boolean attemptConnect() {
			if (client != null && client.isAuthenticated() && sftp == null) {
				return true;
			}
			if (Duration.between(backoff, Instant.now()).toMinutes() < 2 || !configuration.isPresent()) {
				return false;
			}
			for (int i = 0; i < 3; i++) {
				if (client != null) {
					try {
						client.close();
					} catch (final Exception e) {
						e.printStackTrace();
						client = null;
					}
				}
				try {
					client = new SSHClient();
					client.loadKnownHosts();

					client.connect(configuration.get().getHost(), configuration.get().getPort());
					client.authPublickey(configuration.get().getUser());
					sftp = client.newSFTPClient();
					connectionConnected
							.labels(configuration.get().getHost(), Integer.toString(configuration.get().getPort()))
							.set(1);
					return true;
				} catch (final Exception e) {
					sftp = null;
					e.printStackTrace();
					connectionErrors
							.labels(configuration.get().getHost(), Integer.toString(configuration.get().getPort()))
							.inc();
					connectionConnected
							.labels(configuration.get().getHost(), Integer.toString(configuration.get().getPort()))
							.set(0);

				}
			}
			backoff = Instant.now();
			return false;
		}

		public ConfigurationSection configuration() {
			return new ConfigurationSection(String.format("SFTP “%s”", service)) {

				@Override
				public void emit(SectionRenderer renderer) throws XMLStreamException {
					renderer.line("Filename", fileName().toString());
					configuration.ifPresent(configuration -> {
						renderer.line("Host", configuration.getHost());
						renderer.line("Port", configuration.getPort());
						renderer.line("User", configuration.getUser());
					});
					renderer.line("Active", client == null ? "No" : "Yes");
				}
			};
		}

		public Stream<FunctionDefinition> definitions() {
			return definitions.stream();
		}

		@RuntimeInterop
		public synchronized boolean exists(String fileName, boolean errorValue) {
			if (!attemptConnect()) {
				return errorValue;
			}
			return fileAttributes.get(fileName).isPresent();
		}

		@RuntimeInterop
		public synchronized Instant mtime(String fileName, Instant errorValue) {
			if (!attemptConnect()) {
				return errorValue;
			}
			return fileAttributes.get(fileName).map(a -> Instant.ofEpochSecond(a.getMtime())).orElse(errorValue);
		}

		@RuntimeInterop
		public synchronized long size(String fileName, long errorValue) {
			if (!attemptConnect()) {
				return errorValue;
			}
			return fileAttributes.get(fileName).map(FileAttributes::getSize).orElse(errorValue);
		}

		@Override
		public synchronized Optional<Integer> update(Configuration configuration) {
			this.configuration = Optional.of(configuration);
			try {
				if (client != null && client.isConnected()) {
					client.disconnect();
					connectionConnected.labels(configuration.getHost(), Integer.toString(configuration.getPort()))
							.set(0);
				}
			} catch (final Exception e) {
				sftp = null;
				e.printStackTrace();
			}
			attemptConnect();
			return Optional.empty();
		}
	}

	private static final Gauge connectionConnected = Gauge
			.build("shesmu_sftp_connection_connected", "Whether the SFTP connection is connected or not.")
			.labelNames("host", "port").register();

	private static final Counter connectionErrors = Counter
			.build("shesmu_sftp_connection_errors", "Number of SFTP errors encountered.").labelNames("host", "port")
			.register();

	private static final String EXTENSION = ".sftp";

	private static final RuntimeBinding<SftpServer> RUNTIME_BINDING = new RuntimeBinding<>(SftpServer.class, EXTENSION)//
			.function("%1$s_size", "size", Imyhat.INTEGER,
					"Get the size of a file, in bytes, living on the SFTP server described in %2$s.", //
					new FunctionParameter("file_name", Imyhat.STRING), //
					new FunctionParameter("size_if_not_exists", Imyhat.INTEGER))//
			.function("%1$s_exists", "exists", Imyhat.BOOLEAN,
					"Returns true if the file or directory exists on the SFTP server described in %2$s.", //
					new FunctionParameter("file_name", Imyhat.STRING), //
					new FunctionParameter("result_on_error", Imyhat.BOOLEAN))//
			.function("%1$s_mtime", "mtime", Imyhat.DATE,
					"Gets the last modification timestamp of a file or directory living on the SFTP server described in %2$s.", //
					new FunctionParameter("file_name", Imyhat.STRING), //
					new FunctionParameter("date_if_not_exists", Imyhat.DATE));

	private final AutoUpdatingDirectory<SftpServer> configurations;

	public SftpFunctionRepository() {
		configurations = new AutoUpdatingDirectory<>(EXTENSION, SftpServer::new);
	}

	@Override
	public Stream<ConfigurationSection> listConfiguration() {
		return configurations.stream().map(SftpServer::configuration);
	}

	@Override
	public Stream<FunctionDefinition> queryFunctions() {
		return configurations.stream().flatMap(SftpServer::definitions);
	}

}
