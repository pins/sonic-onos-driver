# Copyright 2020-present Open Networking Foundation
# SPDX-License-Identifier: Apache-2.0

ONOS_IP ?= localhost
ONOS_PORT ?= 8181

PIPELINER_VERSION := 0.1.0-SNAPSHOT
DRIVER_VERSION := 0.1.0-SNAPSHOT

# From: https://github.com/opennetworkinglab/stratum-onos-demo/blob/master/app/Makefile
curr_dir := $(shell pwd)
maven_img := maven:3.6.3-jdk-11-slim
curr_dir_sha := $(shell echo -n "$(curr_dir)" | shasum | cut -c1-7)

mvn_build_container_name := mvn-build-${curr_dir_sha}

.PHONY: clean build local_build clean_pipeliner clean_driver build_pipeliner build_driver local_build_driver local_build_pipeliner push_driver push_pipeliner

local_build_driver driver/target/sonic-0.1.0-SNAPSHOT.oar:
	cd ./driver && make local_build

local_build_pipeliner driver/target/sai-0.1.0-SNAPSHOT.oar:
	cd ./pipeliner && make local_build

build_driver:
	cd ./driver && make local_build

build_pipeliner:
	cd ./pipeliner && make local_build

clean_driver:
	cd ./driver && make clean

clean_pipeliner:
	cd ./pipeliner && make clean

local_build: driver/target/sonic-${DRIVER_VERSION}.oar driver/target/sai-${PIPELINER_VERSION}.oar

build: build_driver build_pipelinermake l

clean: clean_driver clean_pipeliner
	rm -r onos-tools

onos-tools:
	curl -sS https://repo1.maven.org/maven2/org/onosproject/onos-releases/2.5.0/onos-admin-2.5.0.tar.gz --output onos-admin-2.5.0.tar.gz
	tar xf onos-admin-2.5.0.tar.gz
	rm onos-admin-2.5.0.tar.gz
	mv onos-admin-2.5.0 onos-tools

push_driver: driver/target/sonic-0.1.0-SNAPSHOT.oar onos-tools
	onos-tools/onos-app ${ONOS_IP} reinstall! driver/target/sonic-${DRIVER_VERSION}.oar

push_pipeliner: pipeliner/target/sai-0.1.0-SNAPSHOT.oar onos-tools
	onos-tools/onos-app ${ONOS_IP} reinstall! pipeliner/target/sai-${PIPELINER_VERSION}.oar
