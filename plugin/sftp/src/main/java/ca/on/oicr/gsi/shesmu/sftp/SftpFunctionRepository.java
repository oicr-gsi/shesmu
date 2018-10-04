package ca.on.oicr.gsi.shesmu.sftp;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;

import org.kohsuke.MetaInfServices;

import ca.on.oicr.gsi.shesmu.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.FunctionParameter;
import ca.on.oicr.gsi.shesmu.FunctionRepository;
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.Pair;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeInterop;
import ca.on.oicr.gsi.shesmu.util.AutoUpdatingDirectory;
import ca.on.oicr.gsi.shesmu.util.AutoUpdatingJsonFile;
import ca.on.oicr.gsi.shesmu.util.Cache;
import ca.on.oicr.gsi.shesmu.util.function.FunctionForInstance;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.sftp.FileAttributes;
import net.schmizz.sshj.sftp.SFTPClient;

@MetaInfServices
public class SftpFunctionRepository implements FunctionRepository {
	private class SftpServer extends AutoUpdatingJsonFile<Configuration> {
		private Instant backoff = Instant.EPOCH;

		private SSHClient client;

		private Optional<Configuration> configuration = Optional.empty();

		private final List<FunctionDefinition> definitions = new ArrayList<>();

		private final Cache<String, FileAttributes> fileAttributes = new Cache<String, FileAttributes>("sftp", 10) {

			@Override
			protected FileAttributes fetch(String fileName) throws IOException {
				return sftp == null ? null : sftp.statExistence(fileName);
			}
		};

		private final Map<String, String> properties = new TreeMap<>();

		private final String service;

		private volatile SFTPClient sftp;

		public SftpServer(Path fileName) {
			super(fileName, Configuration.class);
			properties.put("path", fileName.toString());
			final String filePart = fileName.getFileName().toString();
			service = filePart.substring(0, filePart.length() - EXTENSION.length());

			try {
				final Lookup lookup = MethodHandles.lookup();
				definitions.add(FunctionForInstance.bind(lookup, SftpServer.class, this, "size",
						String.format("%s_size", service),
						String.format("Get the size of a file, in bytes, living on the SFTP server described in %s.",
								fileName),
						Imyhat.INTEGER, //
						new FunctionParameter("file_name", Imyhat.STRING), //
						new FunctionParameter("size_if_not_exists", Imyhat.INTEGER)));
				definitions.add(FunctionForInstance.bind(lookup, SftpServer.class, this, "exists",
						String.format("%s_exists", service),
						String.format(
								"Returns true if the file or directory exists on the SFTP server described in %s.",
								fileName),
						Imyhat.BOOLEAN, //
						new FunctionParameter("file_name", Imyhat.STRING), //
						new FunctionParameter("result_on_error", Imyhat.BOOLEAN)));
				definitions.add(FunctionForInstance.bind(lookup, SftpServer.class, this, "mtime",
						String.format("%s_mtime", service),
						String.format(
								"Gets the last modification timestamp of a file or directory living on the SFTP server described in %s.",
								fileName),
						Imyhat.DATE, //
						new FunctionParameter("file_name", Imyhat.STRING), //
						new FunctionParameter("date_if_not_exists", Imyhat.DATE)));
			} catch (NoSuchMethodException | IllegalAccessException e) {
				e.printStackTrace();
			}
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

		public Pair<String, Map<String, String>> configuration() {
			return new Pair<>(String.format("SFTP “%s”", service), properties);
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
			properties.put("host", configuration.getHost());
			properties.put("port", Integer.toString(configuration.getPort()));
			properties.put("user", configuration.getUser());
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

	private final AutoUpdatingDirectory<SftpServer> configurations;

	public SftpFunctionRepository() {
		configurations = new AutoUpdatingDirectory<>(EXTENSION, SftpServer::new);
	}

	@Override
	public Stream<Pair<String, Map<String, String>>> listConfiguration() {
		return configurations.stream().map(SftpServer::configuration);
	}

	@Override
	public Stream<FunctionDefinition> queryFunctions() {
		return configurations.stream().flatMap(SftpServer::definitions);
	}

}
