FROM registry.access.redhat.com/ubi9/ubi-minimal:latest
VOLUME /data
WORKDIR /data
RUN microdnf install -y git

ENV GIT_REPO_URL="" \
    GIT_BRANCH=${GIT_BRANCH:-main}

CMD ["/bin/bash", "-c", "git clone -b $GIT_BRANCH $GIT_REPO_URL"]