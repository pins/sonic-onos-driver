# Copyright 2020-present Open Networking Foundation
# SPDX-License-Identifier: Apache-2.0

ONOS_IP ?= localhost
ONOS_PORT ?= 8181

# From: https://github.com/opennetworkinglab/stratum-onos-demo/blob/master/app/Makefile
curr_dir := $(shell pwd)
maven_img := maven:3.6.3-jdk-11-slim
curr_dir_sha := $(shell echo -n "$(curr_dir)" | shasum | cut -c1-7)

mvn_build_container_name := mvn-build-${curr_dir_sha}

local_build:
	mvn clean install

build: _create_mvn_container _mvn_package
	$(info *** ONOS app .oar package created succesfully)
	@ls -1 ./target/*.oar

# Reuse the same container to persist mvn repo cache.
_create_mvn_container:
	@if ! docker container ls -a --format '{{.Names}}' | grep -q ${mvn_build_container_name} ; then \
		docker create -v ${curr_dir}:/mvn-src -w /mvn-src  --user "$(id -u):$(id -g)" --name ${mvn_build_container_name} ${maven_img} mvn clean install; \
	fi

_mvn_package:
	$(info *** Building ONOS app...)
	@mkdir -p target
	@docker start -a -i ${mvn_build_container_name}

clean:
	@-docker rm ${mvn_build_container_name} > /dev/null
	@-rm -rf target

onos-tools:
	curl -sS https://repo1.maven.org/maven2/org/onosproject/onos-releases/2.2.6/onos-admin-2.2.6.tar.gz --output onos-admin-2.2.6.tar.gz
	tar xf onos-admin-2.2.6.tar.gz
	rm onos-admin-2.2.6.tar.gz
	mv onos-admin-2.2.6 onos-tools

push-app:
	onos-tools/onos-app ${ONOS_IP} reinstall! target/sonic-0.1.0-SNAPSHOT.oar
