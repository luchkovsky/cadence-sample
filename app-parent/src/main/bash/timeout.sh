#!/bin/bash

DOMAIN=${1:-"sample"}
WORKFLOW_TYPE=${2:-"GreetingWorkflow::getGreeting"}
TIMEOUT=${3:-60}
((TIMEOUT*=60*1000))
COMMAND=${4:-show}
CADENCE=${5:-"172.18.0.9:7933"}
ELASTIC=${6:-"localhost:9200"}
DOCKER=${7:-"yes"}
TIMEOUTS="doc['"'StartTime'"'].value/1000000 < new Date().getTime() - $TIMEOUT"

echo "== Workflows id: == "

result=$(curl --location --request GET $ELASTIC/cadence-visibility-dev/_search \
--header 'Content-Type: application/json' \
--data-raw '{
	 "script_fields": {
         "WorkflowID": {
             "script": "doc['\''WorkflowID'\''].value"
          }
      },
	 "query": {
	    "bool": {
     	   "must": [
          	 { "match": {  "WorkflowType":"'"$WORKFLOW_TYPE"'"}},
			 { "script":
			    {
				    "script": {
					    "source":"'"$TIMEOUTS"'",
                        "lang": "painless" }
                 }
			 }
			],
			"must_not": [
				{"exists":{  "field":"CloseStatus"} }
			],
			"filter": [
			   {"range":{"ExecutionTime":{"lt":"1"}}}
			]
		}
	 }
}' |  sed -e 's/[{}]/''/g' | awk  -v k="text" '{
	  n=split($0,a,",");
	  for (i=1; i<=n; i++) {
	    if(a[i]~/'WorkflowID'/) {
			 m=split(a[i],b,"[");
			 for (j=1; j<=m; j++) {
				 if(b[j]!~/'WorkflowID'/) {
				   gsub("]","", b[j])
				   gsub("\"","", b[j])
				   print( b[j] )
				 }
			 }
	    }
	  }
    }')


	read -r -a array <<< $result

	echo ${#array[@]} " results found"
	for element in "${array[@]}"
	do
	    echo "$element"
	done

	#echo "Press Enter to continue"
	#read -p "$*"
	for element in "${array[@]}"
	do
		if [ "$DOCKER" == "yes" ]; then
	   	  docker exec -it docker_cadence_1 /usr/local/bin/cadence -ad $CADENCE --do $DOMAIN workflow $COMMAND -w "$element"
	    else
		  ./cadence -ad $CADENCE --do $DOMAIN workflow $COMMAND -w "$element"
		fi

		#termiate
		#cancel
		#echo "Press Enter to process next element"
		#read -p "$*"
	done

