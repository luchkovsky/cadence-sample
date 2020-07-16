#!/bin/bash

DOMAIN=${1:-"sample"}
WORKFLOW_TYPE=${2:-"GreetingWorkflow::getGreeting"}
DOCKER=${4:-"yes"}
CADENCE=${5:-"172.18.0.9:7933"}
ELASTIC=${6:-"localhost:9200"}


result=$(curl --location --request GET $ELASTIC/cadence-visibility-dev/_search \
--header 'Content-Type: application/json' \
--data-raw '{
   "size": 0,
	 "query": {
	    "bool": {
			"must_not": [
			   { "match": { "WorkflowType": "cadence-sys-history-scanner-workflow"} }
			 ]}
 	},
	  "aggs" : {
         "unique" : { "terms": {"field": "WorkflowType"} }
	  }
}' | sed -e 's/[{}]/''/g' \
   | awk  -v k="text" '{
	  n=split($0,a,",");
	  for (i=1; i<=n; i++) {
	    if(a[i]~"'"key"'") {

        m=split(a[i],b,"key:");
			  for (j=1; j<=m; j++) {
			     gsub("[\"\[]","", b[j])
			     gsub("buckets:|key:","", b[j])
				   print( b[j] )
        }
	    }
	  }
    }')

   read -r -a arrayType <<< $result
   echo ${#arrayType[@]} " workflow types found"
   for element in "${arrayType[@]}"

	 do
	   result=$(curl --location --request GET $ELASTIC/cadence-visibility-dev/_search \
      --header 'Content-Type: application/json' \
      --data-raw '{
         "size": 1,
         "script_fields": {
             "WorkflowID": {
                "script": "doc['\''WorkflowID'\''].value"
             }},
	          "query": {
	              "bool": {
			            "must": [
			                { "match": { "WorkflowType": "'"$element"'"} }
			             ]
	               }
 	             }
         }'| sed -e 's/[{}]/''/g' \
           | awk  -v k="text" '{
         	  n=split($0,a,",");
	          for (i=1; i<=n; i++) {
	            if(a[i]~"WorkflowID") {
			           m=split(a[i],b,"[");
		           	 for (j=1; j<=m; j++) {
				           if(b[j]!~"WorkflowID") {
				              gsub("]|\"","", b[j])
				              print( b[j] )
				           }
			           }
	            }
	          }
         }')
        SAVE_IFS=$IFS
        IFS=$(echo -en "")
         if [ "$DOCKER" == "yes" ]; then
	   	     flow=$(docker exec -it docker_cadence_1 /usr/local/bin/cadence -ad $CADENCE --do $DOMAIN workflow desc -w "$result")
	       else
		       flow=$(./cadence -ad $CADENCE --do $DOMAIN workflow desc -w "$result")
		     fi
echo $flow


		     result=$(echo $flow | sed -e 's/[{}]/''/g' | awk  -v k="text" '{
         	  n=split($0,a,",");
	          for (i=1; i<=n; i++) {
	            if(a[i]~"taskList") {
			           print( a[i] )

	            }
	          }
         }')
         echo $result
  IFS=$SAVE_IFS

   done