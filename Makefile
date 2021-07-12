# Copyright 2020-present Open Networking Foundation
# SPDX-License-Identifier: Apache-2.0

ONOS_IP ?= localhost
ONOS_PORT ?= 8181

PIPELINER_VERSION := 0.1.0-SNAPSHOT
DRIVER_VERSION := 0.1.0-SNAPSHOT


deps: onos-tools
	cd driver && make deps
	cd pipeliner && make deps

local_build_driver:
	cd ./driver && make local_build

local_build_pipeliner:
	cd ./pipeliner && make local_build

build_driver:
	cd ./driver && make build

build_pipeliner:
	cd ./pipeliner && make build

clean_driver:
	cd ./driver && make clean

clean_pipeliner:
	cd ./pipeliner && make clean

local_build: local_build_driver local_build_pipeliner

build: build_driver build_pipeliner

clean: clean_driver clean_pipeliner
	rm -r onos-tools

onos-tools:
	curl -sS https://repo1.maven.org/maven2/org/onosproject/onos-releases/2.5.0/onos-admin-2.5.0.tar.gz --output onos-admin-2.5.0.tar.gz
	curl -sS https://raw.githubusercontent.com/opennetworkinglab/onos/master/tools/dev/bin/onos-gen-p4-constants --output onos-gen-p4-constants
	tar xf onos-admin-2.5.0.tar.gz
	rm onos-admin-2.5.0.tar.gz
	mv onos-admin-2.5.0 onos-tools
	mv onos-gen-p4-constants onos-tools/

push_driver: driver/target/sonic-${DRIVER_VERSION}.oar onos-tools
	onos-tools/onos-app -P ${ONOS_PORT} ${ONOS_IP} reinstall! driver/target/sonic-${DRIVER_VERSION}.oar

push_pipeliner: pipeliner/target/sai-${PIPELINER_VERSION}.oar onos-tools
	onos-tools/onos-app -P ${ONOS_PORT} ${ONOS_IP} reinstall! pipeliner/target/sai-${PIPELINER_VERSION}.oar
