#!/bin/bash

# This file is part of the SV-Benchmarks collection of verification tasks:
# https://github.com/sosy-lab/sv-benchmarks
#
# SPDX-FileCopyrightText: 2019-2020 Dirk Beyer
#
# SPDX-License-Identifier: Apache-2.0

# This script returns the task definition files for a given coverage property.
# Requires `yq` to be installed (https://pypi.org/project/yq/). Tested with version 2.10.
# Usage: ./get_tasks_for_property <PROPERTY_FILE> [BENCHMARK_DIRECTORY]
# Execute from directory `sv-benchmarks/c` or provide directory as a second command-line argument.
# From the returned task definitions, it is possible to get the input files with the following
# command line:
# yq --raw-output ".input_files" TASK_DEFINITION

set -euo pipefail
IFS=$'\n\t'

# Make recursive globbing work
shopt -s globstar

property=${1:-}
directory=${2:-./}

if [ -z "$property" ]; then
  echo "Usage: $0 <PROPERTY_NAME> [BENCHMARK_DIRECTORY]"
  exit 1
fi

for task in "$directory"/**/*.yml; do
  for prp in $(yq --raw-output "select(.properties != null) | .properties[].property_file" "$task" ); do
    if [ $property -ef "$(dirname "$task")/$prp" ]; then
      echo "$task"
      break
    fi
  done
done
