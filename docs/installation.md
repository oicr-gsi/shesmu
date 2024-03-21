# Installation
This is a guide to building and deploying Shesmu.

## Dependencies
What Shesmu requires will depend on which plugins you enable. Plugins can be
disabled by passing `-pl "!plugin-foo"` to disable a plugin when calling `mvn`
commands.

Build dependencies:

- Java 17 or later
- Maven 3.8 or later
- NPM
- Docker (optional) for container builds

Optional runtime dependencies:

- Prometheus (strongly recommended)
- Prometheus Alert Manager (required for `Olive Alert` to work)
- JIRA
- GitHub as a data source (current branches)
- GitHub, GitLab, or BitBucket for storing configuration files (recommended)

## Running an Instance
Maybe you want to first check [if Shesmu is right for you](ask-your-doctor.md)
and figure out what you would need for an installation.

Setting up Shesmu involves collecting all the necessary bits and putting them
into one place. It will discover most of the configuration from there.

To bring up a test instance, first create `/srv/shesmu`. In this directory, the
other configuration files will be placed (see below). Shesmu can read many
`.shesmu` scripts containing multiple olives from `/srv/shesmu`. If you don't
know how to write them, have a look at [the tutorial](tutorial.md) and [the
language guide](language.md).

An unconfigured Shesmu server is pretty boring. Try the instructions for
[running a demo server](demo/README.md) to bring up a server with a set of
demonstration olives.

### Docker Setup
You can build and run the container with:

    docker build -t shesmu:latest .

Which will build all of the plugins available. Then run with:

    docker run -p 8081:8081 \
      --mount type=bind,source=/srv/shesmu,target=/srv/shesmu \
      shesmu:latest

Shesmu's Dockerfile also supports caching of dependency fetching through
the use of Docker's [BuildKit](https://docs.docker.com/build/buildkit/).

To enable this, add the following to your /etc/docker/daemon.json config:
```
{
  "features": {
    "buildkit" : true
  }
}
```

### Local Setup
Now, compile the main server using Maven:

    mvn install

This will create `shesmu-server/target/shesmu.jar`. If you require any
additional plugins (described below), compile them and collect all the JARs and
their dependencies in a directory on your server in `/srv/shesmu` or a path of
your choosing. The complete set of plugins and dependencies can be copied into
a `jars` directory inside the build directory by doing:

```
test -d jars || mkdir jars
cd jars
ln -sf ../install-pom.xml pom.xml
rm -rf *.jar
mvn -DVERSION=$(cd ..; mvn help:evaluate -Dexpression=project.version -q -DforceStdout) dependency:copy-dependencies
cd ..
```

If you are installing to `/srv/shesmu`, then copy the JARs:

```
rm /srv/shesmu/*.jar
cp jars/*.jar /srv/shesmu
```

The configuration for Shesmu is kept in a directory and will be automatically
updated if it changes. This makes it easy to store the configuration in git and
deploy automatically.

On a Linux server, create a systemd configuration in `/lib/systemd/system/shesmu.service` as follows:

    [Unit]
    Description=Shesmu decision-action server

    [Service]
    Environment=SHESMU_DATA=/srv/shesmu
    ExecStart=/usr/bin/java -p /srv/shesmu/* -m ca.on.oicr.gsi.shesmu.server/ca.on.oicr.gsi.shesmu.Server
    KillMode=process

    [Install]
    WantedBy=multi-user.target

If your Shesmu server cannot determine it's own URL (it attempts to use the
FQDN of the local system), in the `[Unit]` section, add:

    Environment=LOCAL_URL=http://shesmu.myinstitute.org:8081/

Start the server using:

    sudo systemctl daemon-reload
    sudo systemctl enable shesmu
    sudo systemctl start shesmu

Once running, the status page of the server on port `:8081` will display all
the configuration read. The _Definitions_ page will show all the actions and
lookups available to the script and the provenance variables and their types.

To start doing something, write some olives. A description for olives is found
in [the tutorial](tutorial.md). The [builtin](builtin.md) features won't get
you too far, so also add some [plugins](index.md#plugins).
