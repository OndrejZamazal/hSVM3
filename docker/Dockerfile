FROM debian:jessie

MAINTAINER kizi "prozeman@gmail.com"

WORKDIR /root

RUN echo 'deb http://http.debian.net/debian jessie-backports main' >> /etc/apt/sources.list && \
    apt-get update && \
    apt-get install -y -t jessie-backports openjdk-8-jdk && \
    apt-get install -y git maven curl wget && \
    update-alternatives --set java /usr/lib/jvm/java-8-openjdk-amd64/jre/bin/java && \
    mkdir data/datasets && \
    mkdir data/output && \
    mkdir data/templates

ADD parameters-hSVM3.txt data/templates
ADD stopwords.en.txt data/datasets
ADD *.sh ./

RUN git clone https://github.com/propi/hSVM3.git && \
    chmod 755 *.sh
    
WORKDIR hSVM3

RUN mvn clean && \
    mvn install
    
VOLUME ["/root/data/datasets", "/root/data/output"]