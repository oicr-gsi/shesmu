FROM debian:stable

USER root
RUN apt-get update -m && \
    apt-get install -y maven default-jdk && \
    mkdir /build
COPY shesmu-server /build/shesmu-server
COPY plugin /build/plugin
RUN cd /build/shesmu-server && \
    mvn -q clean install && \
    mkdir /usr/share/shesmu && \
    cp target/shesmu.jar /usr/share/shesmu && \
    for P in /build/plugin/*/; do cd ${P}; pwd; mvn -q clean install; cp target/shesmu-plugin-*.jar /usr/share/shesmu; done

FROM openjdk:8-jre-alpine
COPY --from=0 /usr/share/shesmu /usr/share/shesmu
COPY docker-shesmu /usr/bin/shesmu
EXPOSE 8081
CMD ["shesmu", "/srv/shesmu"]
