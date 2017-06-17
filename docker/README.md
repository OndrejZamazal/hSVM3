# hSVM3 in Docker

This is a docker build script of the current hSVM3 version. Thanks to Docker we can run hSVM3 process within one build and one run command. Furthermore, we can simply integrate this process with [LinkedHypernymsDataset](https://github.com/KIZI/LinkedHypernymsDataset), which is required as an input if we want to use the hSVM+LHD fusion.

Docker build script:

```docker build -t hsvm:latest https://github.com/propi/hSVM3.git#:docker```

After the image is built we need to download required datasets for a specific dbpedia version and language:

* Ontology: dbpedia.owl [DBPEDIA]
* Instance types dataset: instance_types.ttl.bz2 [DBPEDIA]
* Instance types transitive dataset: instance_types_transitive.ttl.bz2 [DBPEDIA]
* Short abstracts dataset: short_abstracts.ttl.bz2 [DBPEDIA]
* Article categories dataset: article_categories.ttl.bz2 [DBPEDIA]
* LHD inference dataset: \<language\>.lhd.inference.\<dbpedia-version\>.nt.gz (optionally) [LHD]
* LHD debug: \<language\>.sti.debug (optionally) [LHD]

We can make directories for dbpedia datasets and LHD datasets on the host, copy all needed files into these directories and then use docker volumes:

```docker run -d --name hsvm -v /path/to/dbpedia-datasets-directory[DBPEDIA]:/root/data/datasets -v /path/to/lhd-datasets-directory[LHD]:/root/data/output hsvm /root/start.sh -v=<dbpedia-version> -l=<language>```

We can also download all required dbpedia datasets automatically from the dbpedia download server into a mounted directory:

```docker run -d --name hsvm -v /path/to/dbpedia-datasets-directory[DBPEDIA]:/root/data/datasets -v /path/to/lhd-datasets-directory[LHD]:/root/data/output hsvm /root/download.sh -v=<dbpedia-version> -l=<language>```

## Example

```
LANGUAGE=en
VERSION=2016-04
docker volume create hsvm-datasets
docker volume create hsvm-output
docker run --rm -v hsvm-datasets:/root/data/datasets -v hsvm-output:/root/data/output hsvm /root/download.sh -v=$VERSION -l=$LANGUAGE
docker run -d --name hsvm -v hsvm-datasets:/root/data/datasets -v hsvm-output:/root/data/output hsvm /root/start.sh -v=$VERSION -l=$LANGUAGE
```

After completion of these processes the output hSVM3 dataset will be placed in the hsvm-output docker volume. Final dataset, in this case, does not include results from LHD datasets. If you want to use hSVM+LHD fusion, then you need to copy the LHD inference dataset and LHD debug for the set language into the hsvm-output volume before running.

## Simple integration with LHD

For better hSVM3 results you can optionally put LHD datasets into the main hSVM3 process. This process is currently available only for German, Dutch and English dbpedia. Unfortunately, it may be difficult to obtain these datasets because they are not included in the official dbpedia repository. Therefore we have two choices:

1. Go to the [LinkedHypernymsDataset](https://github.com/KIZI/LinkedHypernymsDataset) site, use the LHD extractor and copy results (inference and debug datasets) into hsvm-output volume (see the example)
2. Use docker-compose:

```
export LANGUAGE=en
export VERSION=2016-04
export DATASETS=<path-to-env-vars-file> # see https://github.com/KIZI/LinkedHypernymsDataset/tree/master/docker
docker-compose up -d hsvm
```

This docker compose first runs the LHD extractor (this process can take several hours); after completion of it, the hSVM3 process starts with results from the LHD extractor. The second part takes additional hours of computing time. As soon as the hSVM3 process ends, all results (LHD+hSVM3) will occur in the hsvm-output volume, which has been automatically created by docker compose.
