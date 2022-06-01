FROM openjdk:8-jre-buster
ARG hadoop_version=3.3.3
ARG hive_version=3.1.3
ARG mysql_version=8.0.29

ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update
RUN apt-get install -y curl
RUN rm -rf /var/lib/apt/lists/*

RUN useradd -m -s /bin/bash hive
USER hive
WORKDIR /home/hive

RUN curl -fsSL https://dlcdn.apache.org/hadoop/common/hadoop-${hadoop_version}/hadoop-${hadoop_version}.tar.gz | tar -C . -xz
RUN mv hadoop-${hadoop_version} hadoop
RUN curl -fsSL https://dlcdn.apache.org/hive/hive-${hive_version}/apache-hive-${hive_version}-bin.tar.gz | tar -C . -xz
RUN mv apache-hive-${hive_version}-bin hive
RUN curl -fsSL https://dev.mysql.com/get/Downloads/Connector-J/mysql-connector-java-${mysql_version}.tar.gz | tar -C . -xz
RUN mv mysql-connector-java-${mysql_version}/mysql-connector-java-${mysql_version}.jar hive/lib
RUN rm -rf mysql-connector-java-${mysql_version}

ENV JAVA_HOME=/usr/local/openjdk-8
ENV HADOOP_HOME=/home/hive/hadoop
ENV HIVE_HOME=/home/hive/hive

CMD /home/hive/hive/bin/hive --service metastore
