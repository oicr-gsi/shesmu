FROM debian:bookworm

USER root
RUN apt-get update -m && \
    apt-get install -y maven openjdk-17-jdk nodejs npm
RUN mkdir /build
COPY . /build/
RUN cd /build && \
    mvn -B install && \
    mvn help:evaluate -Dexpression=project.version -Doutput=version && \
    mkdir /usr/share/shesmu && \
    cp install-pom.xml /usr/share/shesmu/pom.xml && \
    cd /usr/share/shesmu && \
    mvn -B -DVERSION=$(cat /build/version) dependency:copy-dependencies && \
    rm pom.xml

FROM openjdk:17
COPY --from=0 /usr/share/shesmu /usr/share/shesmu
COPY docker-shesmu /usr/bin/shesmu
EXPOSE 8081
EXPOSE 7000
CMD ["shesmu", "/srv/shesmu"]
