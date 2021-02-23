FROM debian:testing

USER root
RUN apt-get update -m && \
    apt-get install -y maven default-jdk-headless nodejs npm
RUN npm install -g typescript
RUN mkdir /build
COPY . /build/
RUN cd /build && \
    tsc -p shesmu-server-ui && \
    mvn -q -Dsurefire.useFile=false -DskipIT=true clean install && \
    mkdir /usr/share/shesmu && \
    cp shesmu-pluginapi/target/shesmu-pluginapi.jar shesmu-server/target/shesmu.jar plugin-*/target/shesmu-plugin-*.jar /usr/share/shesmu

FROM openjdk:16-slim
COPY --from=0 /usr/share/shesmu /usr/share/shesmu
COPY docker-shesmu /usr/bin/shesmu
EXPOSE 8081
EXPOSE 7000
CMD ["shesmu", "/srv/shesmu"]
