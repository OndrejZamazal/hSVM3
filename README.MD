hSVM3
=====
hSVM classifier as hierarchical Support Vector Machines classifier for classification DBpedia type for DBpedia entity.

It returns:
+ **hSVM dataset** (res/hSVMSTI.nt.gz) - types of resources are DBpedia ontology classes built on hSVM algorithm
+ **hSVMSTI dataset** (res/hSVMSTI.nt.gz) - used for DBpedia 2015 release - types of resources are DBpedia ontology classes build on the a statistical type inference algorithm and hSVM algorithm (language independent)

<!-- ## Usage binary release hSVMa: -->
## Requirements

+ Maven 3+
+ Java 8
+ DBpedia Ontology (e.g. ontology.owl)
+ stopwords.en.txt (available on the github)
+ parameters-hSVM3.txt (available on the github)
+ sti_inference_debug
+ Datasets:
  + STI raw dataset, e.g. en.hypoutput.log.dbpedia.manualexclusion.nt.gz,
  + DBpedia Short Abstracts (for the set language, e.g. enwiki-20160305-short-abstracts.ttl.bz2),
  + DBpedia Categories (for the set language, e.g. enwiki-20160305-article-categories.ttl.bz2), 
  + DBpedia Mapping-based Types (e.g. enwiki-20160305-instance-types.ttl.bz2), 
  + DBpedia Mapping-based Types transitive (e.g. enwiki-20160305-instance-types-transitive.ttl.bz2)
+ $ 4+ GB RAM

DBpedia datasets for DBpedia 2016-05 are available at http://vmdbpedia.informatik.uni-leipzig.de:8088/2016-04/

## Setting parameters

You can set up parameters via parameters-hSVM3.txt:
+ min_instances_for_class - number of minimum entities DBpedia class must have in order to be further considered (e.g. 300)
+ dataset_to_type - dataset to by typed (e.g. enwiki-20160305-instance-types.ttl.bz2)
+ sti_types_dataset - STI raw dataset (e.g. en.hypoutput.log.dbpedia.manualexclusion.nt.gz)
+ DBpedia_ontology - e.g. ontology.owl
+ lang_short_abstracts - DBpedia dataset containing short abstracts for entities (DBpedia Short Abstracts), e.g. enwiki-20160305-short-abstracts.ttl.bz2
+ lang_article_categories - DBpedia dataset containing categories for entities (DBpedia Categories), e.g. enwiki-20160305-article-categories.ttl.bz2
+ lang_instance_types - DBpedia Mapping-based Types transitive (e.g. enwiki-20160305-instance-types-transitive.ttl.bz2)
+ sti_inference_debug - debugging file for STI, e.g. en.sti.debug

## Usage instructions

### Preparation and installation

    git clone https://github.com/OndrejZamazal/hSVM3.git
    cd hSVM3/hSVM3/
    # Note: maven must use java 8
    mvn clean
    mvn install

### Running

    mvn exec:java -Dexec.mainClass="cz.vse.swoe.linkedtv.hsvm.hSVMrun"

hSVM will go through nine steps.

1. indexing (it needs roughly 2 GB space)
2. building classification ontology
3. setting up hierarchical SVM models
4. generating training datasets
5. preprocessing content of arff files
6. training SVM models
7. preparing DBpedia and classification ontology
8. preparing probability distribution for STI
9. running hSVM

The typed dataset is in res/hSVMSTI.nt.gz

Steps 1 to 8 take approximately up to 1 hour. Step 9 for DBpedia 2016-05 takes approximately 24 hours.