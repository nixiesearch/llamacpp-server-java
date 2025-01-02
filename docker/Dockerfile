FROM ubuntu:jammy-20240911.1

RUN apt-get update && apt-get install -y git cmake build-essential

ADD ./entrypoint.sh /
CMD ["/bin/sh", "-c", "/entrypoint.sh"]

