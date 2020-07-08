FROM debian:testing

USER root
RUN apt-get update -m && \
    apt-get install -y maven default-jdk-headless nodejs npm chromium
RUN npm install -g typescript
RUN mkdir /build
COPY . /build/
RUN cd /build && \
    tsc -p shesmu-server-ui && \
    mvn -q -pl "!plugin-niassa+pinery" -Dsurefire.useFile=false clean install && \
    mkdir /usr/share/shesmu && \
    cp shesmu-pluginapi/target/shesmu-pluginapi.jar shesmu-server/target/shesmu.jar plugin-*/target/shesmu-plugin-*.jar /usr/share/shesmu

FROM openjdk:8-jre-alpine
COPY --from=0 /usr/share/shesmu /usr/share/shesmu
COPY docker-shesmu /usr/bin/shesmu
EXPOSE 8081
EXPOSE 7000
CMD ["shesmu", "/srv/shesmu"]
