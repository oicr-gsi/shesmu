FROM debian:testing

USER root
RUN apt-get update -m && \
    apt-get install -y maven default-jdk && \
    mkdir /build
COPY . /build/
RUN cd /build && \
    mvn -q -pl "!plugin-niassa+pinery" clean install && \
    mkdir /usr/share/shesmu && \
    cp shesmu-pluginapi/target/shesmu-pluginapi.jar shesmu-server/target/shesmu.jar plugin-*/target/shesmu-plugin-*.jar /usr/share/shesmu

FROM openjdk:8-jre-alpine
COPY --from=0 /usr/share/shesmu /usr/share/shesmu
COPY docker-shesmu /usr/bin/shesmu
EXPOSE 8081
CMD ["shesmu", "/srv/shesmu"]
