# Copyright 2020-present Open Networking Foundation
# SPDX-License-Identifier: Apache-2.0

curr_dir := $(shell pwd)

ONOS_IP ?= localhost
ONOS_PORT ?= 8181

P4C_IMG := opennetworking/p4c:stable-20210108
P4C_SRC_DIR := ${curr_dir}/pins-infra/sai_p4/
P4C_OUT_DIR := ${curr_dir}/src/main/resources/

BMV2_CPU_PORT := 255
BMV2_PP_FLAGS := -DCPU_PORT=${BMV2_CPU_PORT} -DWITH_DEBUG
OTHER_PP_FLAGS :=

maven_img := maven:3.6.3-jdk-11-slim
curr_dir_sha := $(shell echo -n "$(curr_dir)" | shasum | cut -c1-7)

mvn_build_container_name := mvn-build-${curr_dir_sha}

.PHONY: deps local_build build _create_mvn_container _mvn_package build-sai-p4 graphs-sai-p4

deps:
	docker pull ${maven_img}

../onos-tools:
	cd ../ && make onos-tools

local_build:
	$(info *** Building ONOS app...)
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

# TODO: use bazel target from pins-infra
build-sai-p4:
	$(info *** Building sai-main.p4)
	@echo "DEPRECATED, use bazel in pins-infra instead"
	docker run --rm -w /p4src -v ${P4C_SRC_DIR}:/p4src \
		-v ${P4C_OUT_DIR}:/p4c-out ${P4C_IMG} \
		p4c-bm2-ss --arch v1model ${BMV2_PP_FLAGS} ${OTHER_PP_FLAGS} \
		--pp /p4c-out/_pp.p4 -o /p4c-out/bmv2.json \
		--p4runtime-files /p4c-out/p4info.txt \
		/p4src/instantiations/google/middleblock.p4
	@echo "*** google/middleblock.p4 compiled successfully. Files available in src/main/resources"

# Run this target on P4Info updates. It creates the SaiConstants.java from the
# file src/main/resources/sai_fixed.p4info.
gen-constants: ../onos-tools
	$(info *** Creating SaiConstants.java from src/main/resources/pins/sai_onf.p4info)
	docker run --rm -v $(shell pwd):/pipeliner \
		-v $(shell pwd)/../onos-tools/:/onos-tools \
		-w /pipeliner --entrypoint python  \
		opennetworking/p4mn:latest /onos-tools/onos-gen-p4-constants Sai \
		/pipeliner/src/main/resources/pins/sai_onf.p4info \
		-o /pipeliner/src/main/java/org/onosproject/pipelines/sai/SaiConstants.java \
		--with-package-path org.onosproject.pipelines.sai

clean:
	@-docker rm ${mvn_build_container_name} > /dev/null
	@-rm -rf target
	@-rm -r ${P4C_OUT_DIR}/graphs
	@-rm ${P4C_OUT_DIR}/p4info.txt
