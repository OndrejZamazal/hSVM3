FROM debian:jessie

MAINTAINER kizi "prozeman@gmail.com"

WORKDIR /root

RUN echo 'deb http://http.debian.net/debian jessie-backports main' >> /etc/apt/sources.list && \
    apt-get update && \
    apt-get install -y -t jessie-backports openjdk-8-jdk && \
    apt-get install -y git maven curl wget && \
    update-alternatives --set java /usr/lib/jvm/java-8-openjdk-amd64/jre/bin/java && \
    mkdir -p data/datasets && \
    mkdir -p data/output && \
    mkdir -p data/templates

ADD *.txt data/templates/
ADD *.sh ./

RUN git clone https://github.com/OndrejZamazal/hSVM3.git && \
    chmod 755 *.sh
    
WORKDIR hSVM3

RUN mvn clean && \
    mvn install
    
VOLUME ["/root/data/datasets", "/root/data/output"]