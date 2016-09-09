package cz.vse.swoe.linkedtv.hsvm;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

import org.apache.commons.io.FileUtils;

/**
 * Created by svabo on 2016.
 */

public class hSVMrun {
	
	private int min_instances_for_class = 300;
	public String dataset_to_type = "";
	public String sti_inference_debug = "";
	public String sti_types_dataset = "nl.lhd.inference.2015.nt.gz";
	public String DBpedia_ontology = "ontology.owl";
	public String lang_short_abstracts = "enwiki-20150205-short-abstracts.nt.gz";
	public String lang_article_categories = "enwiki-20150205-article-categories.nt.gz";
	public String lang_instance_types = "enwiki-20150205-instance-types.nt.gz";
	
	public hSVMrun(boolean res) {
		try {
			if (res) {
				FileUtils.deleteDirectory(new File("res"));
				new File("res/LibSVM-model").mkdirs();
				new File("res/LibSVM-unique").mkdirs();
				new File("res/arff-complete").mkdirs();
			}
			
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	public void finish() {
		try {
			FileUtils.deleteDirectory(new File("res"));
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	void set_parameters(String input_file) {
		try {
			BufferedReader fp = new BufferedReader(new FileReader(input_file));
			
			while(true)
			{
				String line = fp.readLine();
				
				if(line == null) break;
				
				if(line.matches("#.*")) {
					continue;
				}
				String key = line.substring(0, line.indexOf("|"));
				String value = line.substring(line.indexOf("|")+1);
				
				if(key.equals("min_instances_for_class")) this.min_instances_for_class=new Integer(value).intValue();
				if(key.equals("dataset_to_type")) this.dataset_to_type=value;
				if(key.equals("sti_inference_debug")) this.sti_inference_debug=value;
				if(key.equals("sti_types_dataset")) this.sti_types_dataset=value;
				if(key.equals("DBpedia_ontology")) this.DBpedia_ontology=value;
				if(key.equals("lang_short_abstracts")) this.lang_short_abstracts=value;
				if(key.equals("lang_article_categories")) this.lang_article_categories=value;
				if(key.equals("lang_instance_types")) this.lang_instance_types=value;
			}
			fp.close();
		}
		catch(Exception e) {
			e.printStackTrace();
		}
			
	}
	
	public void applyhSVM() {
		this.set_parameters("parameters-hSVM3.txt");
		System.out.println(DBpedia_ontology+":"+lang_short_abstracts+":"+lang_article_categories+":"+lang_instance_types+":"+this.min_instances_for_class+":"+this.dataset_to_type+":"+this.sti_inference_debug);

		
		//boolean indexing = false;
		boolean indexing = true;
		
		//workflow:
		//1. indexing phase:		
		if (indexing)
			System.out.println("indexing");
		else
			System.out.println("using existing index");
		HierarchicalModelGeneration hmg = new HierarchicalModelGeneration(indexing, DBpedia_ontology, lang_short_abstracts, lang_article_categories, lang_instance_types);
		
		//2. building ontology for hierarchical SVM model:
		System.out.println("building classification ontology");
		hmg.traverse_classes_hierarchy_start(true,min_instances_for_class);
		hmg.adjustOntology("dbpedia-excerpt.owl");
		hmg.init_ontology_cache("dbpedia-excerpt-cleaned.owl");
		
		//3. setting up hierarchical SVM models:
		System.out.println("setting up hierarchical SVM models");
		hmg.subclassesSplitIntoSVMmodels("dbpedia-excerpt-cleaned.owl");
		
		//4. generating training datasets for each SVM model:
		System.out.println("generating training datasets");
		hmg.generate_arff_files("svmModelsSubclasses.txt",min_instances_for_class);
		
		//5. preprocessing content of arff files, creating unique vectors of attributes for cat and sa separately and transforming into LibSVM format:
		System.out.println("preprocessing content of arff files");
		hmg.filter_arff_files("res/svmModelsSubclasses.txt");
		
		//6. training SVM models using LibSVM:
		System.out.println("training SVM models");
		LibSVMClassifier lc = new LibSVMClassifier("svmModelsSubclasses.txt");
		lc.train_model();
		
		//7. preparing DBpedia and classification ontology
		System.out.println("preparing DBpedia and classification ontology");
		PreprocessingSTI p = new PreprocessingSTI();
		p.init_ontology_cache("res/dbpedia-excerpt-cleaned.owl");
		p.init_ontology_cache_subclasses("res/dbpedia-excerpt-cleaned.owl");
		p.init_ontology_cache_direct_subclasses("res/dbpedia-excerpt-cleaned.owl");
		
		p.init_ontology_cache(this.DBpedia_ontology);
		p.init_ontology_cache_subclasses(this.DBpedia_ontology);
		p.init_ontology_cache_direct_subclasses(this.DBpedia_ontology);
		
		//8. preparing probability distribution for STI
		System.out.println("preparing probability distribution for STI");
		p.init_direct_superclasses_of_DBpedia();
		p.init_direct_subclasses_of_DBpedia();
		p.preprocess_debug_results2(this.sti_inference_debug);
		
		//9. running hSVM
		System.out.println("running hSVM");
		AgghSVMSTI agghSVMSTI = new AgghSVMSTI(this.DBpedia_ontology,this.sti_types_dataset,"res/sti.debug.inference.prob");
		agghSVMSTI.process_resources2WeightsForLHD(this.dataset_to_type, true, "alpha", 0.0, 0.33);
		
		//10. removing working directory:
		//System.out.println("removing working directory");
		//this.finish();
	}
	
	public static void main(String[] args) throws Exception {		
		hSVMrun h = new hSVMrun(true);
		//hSVMrun h = new hSVMrun(false);
		h.applyhSVM();
	}

}