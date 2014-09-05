#!/bin/sh
set -x

. ./host.env


curl 	--request GET \
	-w "\\nHTTP Response : %{http_code}\\n" \
	${HOST}/pipeline/async
#	--header "Accept: application/json"\
