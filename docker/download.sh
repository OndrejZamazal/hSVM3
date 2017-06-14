#!/bin/bash

DOWNLOAD_ONTOLOGY=
DOWNLOAD_INSTANCE_TYPES=
DOWNLOAD_INSTANCE_TYPES_TRANSITIVE=
DOWNLOAD_SHORT_ABSTRACTS=
DOWNLOAD_ARTICLE_CATEGORIES=
LANGUAGE=
VERSION=

for i in "$@"
do
case $i in
    -v=*)
    VERSION="${i#*=}"
    shift
    ;;
    -l=*)
    LANGUAGE="${i#*=}"
    shift
    ;;
    -o=*)
    DOWNLOAD_ONTOLOGY="${i#*=}"
    shift
    ;;
    -it=*)
    DOWNLOAD_INSTANCE_TYPES="${i#*=}"
    shift
    ;;
    -itt=*)
    DOWNLOAD_INSTANCE_TYPES_TRANSITIVE="${i#*=}"
    shift
    ;;
    -sa=*)
    DOWNLOAD_SHORT_ABSTRACTS="${i#*=}"
    shift
    ;;
    -ac=*)
    DOWNLOAD_ARTICLE_CATEGORIES="${i#*=}"
    shift
    ;;
    *)
    #unknown option
    ;;
esac
done

cd /root/data/datasets

if [[ -z "$VERSION" ]]; then
  echo "DBpedia version is required (e.g. -v=2016-04)"
  exit 1
fi

if [[ -z "$LANGUAGE" ]]; then
  echo "Language is required (e.g. -l=en)"
  exit 1
fi

if [[ -z "$DOWNLOAD_ONTOLOGY" ]]; then
  DOWNLOAD_ONTOLOGY="http://downloads.dbpedia.org/$VERSION/ontology.owl"
fi

if [[ -z "$DOWNLOAD_INSTANCE_TYPES" ]]; then
  DOWNLOAD_INSTANCE_TYPES="http://downloads.dbpedia.org/$VERSION/core-i18n/$LANGUAGE/instance_types_$LANGUAGE.ttl.bz2"
fi

if [[ -z "$DOWNLOAD_INSTANCE_TYPES_TRANSITIVE" ]]; then
  DOWNLOAD_INSTANCE_TYPES_TRANSITIVE="http://downloads.dbpedia.org/$VERSION/core-i18n/$LANGUAGE/instance_types_transitive_$LANGUAGE.ttl.bz2"
fi

if [[ -z "$DOWNLOAD_SHORT_ABSTRACTS" ]]; then
  DOWNLOAD_SHORT_ABSTRACTS="http://downloads.dbpedia.org/$VERSION/core-i18n/$LANGUAGE/short_abstracts_$LANGUAGE.ttl.bz2"
fi

if [[ -z "$DOWNLOAD_ARTICLE_CATEGORIES" ]]; then
  DOWNLOAD_ARTICLE_CATEGORIES="http://downloads.dbpedia.org/$VERSION/core-i18n/$LANGUAGE/article_categories_$LANGUAGE.ttl.bz2"
fi

wget -O dbpedia.owl $DOWNLOAD_ONTOLOGY
wget -O instance_types.ttl.bz2 $DOWNLOAD_INSTANCE_TYPES
wget -O instance_types_transitive.ttl.bz2 $DOWNLOAD_INSTANCE_TYPES_TRANSITIVE
wget -O short_abstracts.ttl.bz2 $DOWNLOAD_SHORT_ABSTRACTS
wget -O article_categories.ttl.bz2 $DOWNLOAD_ARTICLE_CATEGORIES