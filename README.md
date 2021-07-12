# sonic-onos-driver
Driver and pipeliner for PINS/SONiC in ONOS

## Pre-requisites:
To build the ONOS SONiC/PINS driver and SAI pipeliner only `Docker` and `curl` are required as dependencies.

## Build driver
Run `make build_driver`

## Build pipeliner
Run `make build_pipeliner`

## Push driver or pipeliner app to a running ONOS instance
`make {push_driver|push_pipeliner} [ONOS_IP=<ip-onos-instance>]`

ONOS_IP default value is `localhost`.

