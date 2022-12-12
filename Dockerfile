FROM debian:bullseye

USER root
RUN apt-get update -m && \
    apt-get install -y maven openjdk-17-jdk nodejs npm
RUN npm install -g typescript@4.3.2
RUN mkdir /build
COPY . /build/
RUN --mount=type=cache,target=/root/.m2 cd /build && \
    tsc -p shesmu-server-ui && \
    mvn -Dsurefire.useFile=false -DskipIT=true clean install && \
    mkdir /usr/share/shesmu && \
    cp shesmu-pluginapi/target/shesmu-pluginapi.jar shesmu-server/target/shesmu.jar plugin-*/target/shesmu-plugin-*.jar /usr/share/shesmu

FROM openjdk:17
COPY --from=0 /usr/share/shesmu /usr/share/shesmu
COPY docker-shesmu /usr/bin/shesmu
EXPOSE 8081
EXPOSE 7000
CMD ["shesmu", "/srv/shesmu"]
