# Copyright 2020-present Open Networking Foundation
# SPDX-License-Identifier: Apache-2.0

# From: https://github.com/opennetworkinglab/stratum-onos-demo/blob/master/app/Makefile
CURR_DIR := $(shell pwd)
MAVEN_IMG := maven:3.6.3-jdk-11-slim
CURR_DIR_SHA := $(shell echo -n "$(CURR_DIR)" | shasum | cut -c1-7)

mvn_build_container_name := mvn-build-${CURR_DIR_SHA}


deps:
	docker pull ${MAVEN_IMG}

local_build:
	mvn clean install

build: _create_mvn_container _mvn_package
	$(info *** ONOS app .oar package created succesfully)
	@ls -1 ./target/*.oar

# Reuse the same container to persist mvn repo cache.
_create_mvn_container:
	@if ! docker container ls -a --format '{{.Names}}' | grep -q ${mvn_build_container_name} ; then \
		docker create -v ${CURR_DIR}:/mvn-src -w /mvn-src  --user "$(id -u):$(id -g)" --name ${mvn_build_container_name} ${MAVEN_IMG} mvn clean install; \
	fi

_mvn_package:
	$(info *** Building PINS DRIVER app...)
	@mkdir -p target
	@docker start -a -i ${mvn_build_container_name}

clean:
	@-docker rm ${mvn_build_container_name} > /dev/null
	@-rm -rf target
