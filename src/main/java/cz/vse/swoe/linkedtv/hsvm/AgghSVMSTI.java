package cz.vse.swoe.linkedtv.hsvm;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Statement;

import libsvm.svm;
import libsvm.svm_model;

/**
 * Created by svabo on 2016.
 */

class ProbClass3 {
	//prob is fin_prob after the run of the algorithm
	double prob = 0;
	double prob_sum = 0;
	int counter = 0;
	
	public ProbClass3(double prob) {
		this.prob_sum=prob;		
		if (prob!=0)
			this.counter=1;
	}
	
	public void addProb(double prob) {
		this.prob_sum+=prob;
		this.counter++;
	}
	
	//09-11-15, for multiplicative approach
	public void multiplyProb(double prob) {
		this.prob_sum*=prob;
		this.counter++;
	}
	
	public String toString() {		
		return this.prob_sum+":"+this.counter+":"+prob;
	}
}

public class AgghSVMSTI {
	
	protected OntologyCache ontCache = null;
	protected LibSVMClassifier lc = null;
	protected ResourceAttributesLucene res = null;
	HashMap<String, ProbClass3> types = null;
	HashSet<String> fin_types=null;
	HashMap<String,svm_model> models_sa = null;
	HashMap<String,svm_model> models_cat = null;
	HashSet<String> all_types = null;
	
	HashMap<String, HashMap<String,Double>> lhdtype_to_sti = null; //mapovani resource type na sti types
	HashMap<String, String> lhdtype_to_selectedSTItype = null; //mapovani resource type na sti selected type
	HashMap<String, String> resource_lhdtype = null;
	
	public AgghSVMSTI(String DBpediaOntology, String input_resources, String debug_input) {
		try {
			ontCache =  new OntologyCache("SubClassesMLClassifier.txt","DirectSubClassesMLClassifier.txt");
			lc = new LibSVMClassifier("svmModelsSubclasses.txt");
			res = new ResourceAttributesLucene(false, "res/uniqueAttributesSa.txt", "res/uniqueAttributesCat.txt", DBpediaOntology);
			//loading models sa and cat										
			this.models_sa = new HashMap<String, svm_model>();
			this.models_cat = new HashMap<String, svm_model>();
			for(String m_name : lc.class_names.keySet()) {
				this.models_sa.put(m_name, svm.svm_load_model("res/LibSVM-model/sa-"+m_name+".model"));			
				this.models_cat.put(m_name, svm.svm_load_model("res/LibSVM-model/cat-"+m_name+".model"));
			}
			
			this.types = new HashMap<String, ProbClass3>();
			
			//e.g. resources from en.instances.notmapped.new.nt
			resource_lhdtype = new HashMap<String, String>();
			//resource to lhd_type
			if(!input_resources.equals("")) {
				Model model = ModelFactory.createDefaultModel();
				model = RDFDataMgr.loadModel(input_resources, Lang.NTRIPLES) ;
				
				for (Iterator<Statement> it = model.listStatements(); it.hasNext(); ) {
	                final Statement stmt = it.next();
	                String res=stmt.getSubject().getURI();;
					String lhdtype=stmt.getObject().toString();
					resource_lhdtype.put(URLDecoder.decode(res, "UTF-8"), lhdtype);
				}
				model.close();
			}

			//2. mapovani lhd type na sti typy
			lhdtype_to_sti = new HashMap<String, HashMap<String,Double>>();
			lhdtype_to_sti = this.read_debug_results(debug_input);
			//lhdtype_to_selectedSTItype = new HashMap<String, String>();
			//lhdtype_to_selectedSTItype = this.read_debug_resultsSelectedType(debug_input);
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	//19-08-16, processing entities from inference dataset from LHD
	public void process_resources2WeightsForLHD(String input_file, boolean multiplied, String strategy, double threshold, double sti_weight) {
		try {
			String path="res/";
			PrintWriter toFile;
			GZIPOutputStream stream = new GZIPOutputStream(new FileOutputStream(path+"hSVMSTI.nt.gz"));
			toFile = new PrintWriter(stream);
			
			InputStream is;
			if(input_file.endsWith("bz2")||input_file.endsWith("bzip2")) {
				is = new BZip2CompressorInputStream(new FileInputStream(input_file));
			}
			else if(input_file.endsWith("gz")||input_file.endsWith("gzip")) {
				is = new GZIPInputStream(new FileInputStream(input_file));
			}
			else {
				is = new FileInputStream(input_file);
			}
			BufferedReader fp = new BufferedReader(new InputStreamReader(is));
			
			while(true)
			{
				String line = fp.readLine();
				if(line == null) break;
				
				if(line.matches("#.*")) {
					continue;
				}
				
				String resource = line.substring(0,line.indexOf(" "));
				resource=resource.replaceAll(">", "").replaceAll("<", "");
				//System.out.println(resource);
				computeTypesProbabilityTrueDirectThresholding2Weight(resource, multiplied, strategy, threshold, sti_weight);
				String result="";
				for(String s : this.fin_types) {
					result+=" "+s;
				}
				if(!result.trim().isEmpty()) {
					String URIpart = resource.substring(0,resource.lastIndexOf("/")+1);
	                String localName=resource.substring(resource.lastIndexOf("/")+1);
	                localName=StringEscapeUtils.escapeEcmaScript(localName);
	                toFile.println("<"+URIpart+localName+"> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://dbpedia.org/ontology/"+result.trim()+"> .");
					//toFile.println(resource+";"+result);
					toFile.flush();
				}
			}
			fp.close();
			toFile.close();
			stream.close();
			
			//this.res.close();
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	private String get_type_with_strategy(boolean multiplied, String strategy) {
		String max_type="";
		double max_prob=0.0;
		double prob=0.0;
		for(String type : this.types.keySet()) {			
			if (multiplied)
				prob=this.types.get(type).prob_sum;
			else //additive
				prob=this.types.get(type).prob_sum/this.types.get(type).counter;
			((ProbClass3)this.types.get(type)).prob=prob;
			
			//alpha strategy
			if (strategy.equals("alpha")||strategy.equals("thresholding-alfa")) {
				if(ontCache.subtypesforSupertypes.containsKey(type)) 
					continue;
				if(!ontCache.subtypesforSupertypes.containsKey(type)&&!ontCache.subTypes.contains(type))
					continue;
				else if (prob>max_prob) {
					max_prob=prob;
					max_type=type;
				}
			}
			
			else if (strategy.equals("alpha2")) {
				if(!ontCache.subTypes.contains(type)) 
					continue;
				else if (prob>max_prob) {
					max_prob=prob;
					max_type=type;
				}
			}
			
			else if (strategy.equals("beta")||strategy.equals("thresholding-beta")) {
				if (prob>max_prob) {
					max_prob=prob;
					max_type=type;
				}
			}
		}
		
		return max_type;
	}
	
	//15-04-16, agregace abstract, cat a STI klasifikatoru
	public HashMap<String,Double> aggregateClassifiersACSWeights(String resource, double sti_weight) {
		double w1=0; //cat
		double w2=0; //abstract
		//double w3=0.33; //sti
		double w3=sti_weight; //sti
		
		String URIpart = resource.substring(0,resource.lastIndexOf("/"));
		if(URIpart.equals("http://de.wikipedia.org/wiki"))
			URIpart="http://de.dbpedia.org/resource/";
		else if (URIpart.equals("http://nl.wikipedia.org/wiki"))
			URIpart="http://nl.dbpedia.org/resource/";
		else if (URIpart.equals("http://en.wikipedia.org/wiki"))
			URIpart="http://dbpedia.org/resource/";
		else URIpart="http://dbpedia.org/resource/";
		resource = resource.substring(resource.lastIndexOf("/")+1);		
		String localName = resource;
		try {
			localName = URLDecoder.decode(localName, "UTF-8");
		}
		catch(Exception e) {
			e.printStackTrace();
		}
		resource = URIpart+localName;
		
		HashMap<String,Double> abstract_classifier_results = new HashMap<String,Double>();
		HashMap<String,Double> cat_classifier_results = new HashMap<String,Double>();
		HashMap<String,Double> STI_classifier_results = new HashMap<String,Double>();
		HashMap<String,Double> aggregated_results = new HashMap<String,Double>();
		
		res.get_categories_and_short_abstracts_for_resource_lucene(resource);
		String sa = res.createOneBowRepresentationSa();
		String cat = res.createOneBowRepresentationCat();
		for(String m_name : lc.class_names.keySet()) {
			ArrayList<ProbClass2> sa_classified_classes = new ArrayList<ProbClass2>();
			ArrayList<ProbClass2> cat_classified_classes = new ArrayList<ProbClass2>();
			
			if(!sa.trim().equals("")) {
				lc.predict(m_name, sa, this.models_sa.get(m_name), "probability");
				//lc.predict(m_name, sa, this.models_sa.get(m_name), "target_types_number");
				sa_classified_classes = lc.classified_classes;				
				for(ProbClass2 pc2 : sa_classified_classes) {
					abstract_classifier_results.put(pc2.target_class, pc2.nprob1);
				}
			}			
			
			if(!cat.trim().equals("")) {
				lc.predict(m_name, cat, this.models_cat.get(m_name), "probability");
				//lc.predict(m_name, cat, this.models_cat.get(m_name), "target_types_number");
				cat_classified_classes = lc.classified_classes;
				for(ProbClass2 pc2 : cat_classified_classes) {
					cat_classifier_results.put(pc2.target_class, pc2.nprob1);
				}
			}
			
		}
		
		boolean STI_input=false;
		STI_classifier_results.clear();
		String lhdtype=this.resource_lhdtype.get(resource); 
		if(lhdtype!=null) {
			if (this.lhdtype_to_sti.get(lhdtype)!=null) {
				STI_classifier_results = this.lhdtype_to_sti.get(lhdtype);
				STI_input=true;
			}
		}
		HashSet<String> abstractClassifiers = new HashSet<String>();
		HashSet<String> catClassifiers = new HashSet<String>();
		HashSet<String> stiClassifiers = new HashSet<String>();
		for(String superType : ontCache.directSubtypesforSupertypes.keySet()) {
			double sum=0.0;
			double sum2=0.0;
			double sum3=0.0;
			for(String subType : ontCache.directSubtypesforSupertypes.get(superType)) {
				if(cat_classifier_results.containsKey(subType)) {
					sum+=cat_classifier_results.get(subType);
				}
				if(abstract_classifier_results.containsKey(subType)) {
					sum2+=abstract_classifier_results.get(subType);
				}
				if(STI_classifier_results.containsKey(subType)) {
					sum3+=STI_classifier_results.get(subType);
				}
			}
			if(sum!=0)
				catClassifiers.add(superType);
			if(sum2!=0)
				abstractClassifiers.add(superType);
			if(sum3!=0)
				stiClassifiers.add(superType);
		}
		
		//aggregate pres union of types abstract, cat a STI
		HashSet<String> all_types = new HashSet<String>();
		all_types.addAll(abstract_classifier_results.keySet());
		all_types.addAll(cat_classifier_results.keySet()); //to je ta sama mnozina jako pro abstract
		all_types.addAll(STI_classifier_results.keySet());
		ProbClass3 pc3 = new ProbClass3(0.0); 
		for(String type : all_types) {
			pc3 = new ProbClass3(0.0);
			w1=0.0;
			w2=0.0;
			//w3=0.33;
			w3=sti_weight;
			if(ontCache.directSupertypeforSubtypes.containsKey(type)) {
				if(!abstractClassifiers.contains(ontCache.directSupertypeforSubtypes.get(type)))
					w2=0;
				if(!catClassifiers.contains(ontCache.directSupertypeforSubtypes.get(type)))
					w1=0;
				if(!stiClassifiers.contains(ontCache.directSupertypeforSubtypes.get(type)))
					w3=0;
			}
			else if(!STI_input) {
				w3=0;
			}
			w1=(1-w3)/2;
			w2=(1-w3)/2;
			if(w1==0&&w3==0)
				w2=1;
			else if(w2==0&&w3==0)
				w1=1;
			else if(w1==0&&w2==0&&w3!=0)
				w3=1;
			else if(w1==0) {
				w2=(1-w3);
			}
			else if(w2==0) {
				w1=(1-w3);
			}
			else if(w3==0) {
				w1=(1-w3)/2;
				w2=(1-w3)/2;
			}
			
			if(abstract_classifier_results.containsKey(type)) {
				pc3.addProb(abstract_classifier_results.get(type)*w2);
			}
			if(cat_classifier_results.containsKey(type)) {
				pc3.addProb(cat_classifier_results.get(type)*w1);
			}
			if(STI_classifier_results.containsKey(type)) {
				pc3.addProb(STI_classifier_results.get(type)*w3);
			}
			aggregated_results.put(type, (double)pc3.prob_sum);
			
		}
		return aggregated_results;
	}
	
	private HashMap<String, HashMap<String,Double>> read_debug_results(String file) {
		try {
			HashMap<String, HashMap<String,Double>> results = new HashMap<String, HashMap<String,Double>>();
			
			BufferedReader vstup = null;
			int position=0; //1=type,2=list,3=pruned,4=selected
			vstup = new BufferedReader(new FileReader(file));				
			String s="";
			HashMap<String,Double> list=new HashMap<String,Double>();
			String lhdtype="";			
			while ((s = vstup.readLine()) != null) {
				if(s.trim().equals("# type"))	 {
					if(!lhdtype.equals("")) {
						results.put(lhdtype, list);
					}
					position=1;
					lhdtype="";
				}
				else if(s.trim().equals("#list of  candidate mapped types,probability"))	 {
					position=2;
					list=new HashMap<String,Double>();
				}
				else if(s.trim().equals("#Selected mapping"))	 {
					position=3;
				}
				else if(s.trim().equals(""))	 {
					position=4;	
				}
				else if(position==1) {
					lhdtype=s.trim();
				}
				else if(position==2) {
					s = s.trim();
					String type = s.substring(0,s.indexOf(","));
					String value = s.substring(s.indexOf(",")+1);
					list.put(type.trim(), new Double(new Double(value).doubleValue()));
				}
			}
			results.put(lhdtype, list);
			
			vstup.close();
			return results;
		}
		catch(Exception e) {			
			e.printStackTrace();
			return null;
		}
	}
	
	//13-04-16, nova implementace pro direct thresholding efektivnejsi
	public void computeTypesProbabilityTrueDirectThresholding2Weight(String resource, boolean multiplied, String strategy, double threshold, double sti_weight) {
		try {
			//init
			this.types=new HashMap<String, ProbClass3>();
			this.fin_types=new HashSet<String>();
			
			HashMap<String,Double> aggregated_results = this.aggregateClassifiersACSWeights(resource,sti_weight);
			
			//initialization
			for(String predictedType : aggregated_results.keySet()) {
				double prob = aggregated_results.get(predictedType);
				this.types.put(predictedType, new ProbClass3(prob));
			}
			
			for(String superType : ontCache.subtypesforSupertypes.keySet()) {
				if(aggregated_results.containsKey(superType)) {
					double prob = aggregated_results.get(superType);
					for(String subType : ontCache.subtypesforSupertypes.get(superType)) {
						if(aggregated_results.containsKey(subType)) {
							if (multiplied)
								((ProbClass3)this.types.get(subType)).multiplyProb(prob);
							else //additive
								((ProbClass3)this.types.get(subType)).addProb(prob);
						}
					}
				}
			}
			
			try {
				for(String superType : aggregated_results.keySet()) {
					double prob;
					if(this.types.containsKey(superType)) {
						if (multiplied)
							prob=this.types.get(superType).prob_sum;
						else //additive
							prob=this.types.get(superType).prob_sum/this.types.get(superType).counter;
					}
					else {
						continue;
					}
					if(prob<=threshold) {//odrezani cele vetve
						if(ontCache.subtypesforSupertypes.containsKey(superType)) {
							for(String subType : ontCache.subtypesforSupertypes.get(superType)) {
								if(this.types.containsKey(subType)) {
									this.types.remove(subType);
								}
							}
						}
						this.types.remove(superType);
					}
				}
				
				for(String singleton_root_class : ontCache.singletonRootClasses) {
					double prob;
					if(this.types.containsKey(singleton_root_class)) {
						if (multiplied)
							prob=this.types.get(singleton_root_class).prob_sum;
						else //additive
							prob=this.types.get(singleton_root_class).prob_sum/this.types.get(singleton_root_class).counter;
						if(prob<=threshold) {//odrezani leaf subtype
							this.types.remove(singleton_root_class);
						}
					}
				}
			}
			catch(Exception e) {
				e.printStackTrace();
			}
			
			if (strategy.equals("thresholding")) {
				for(String type : this.types.keySet()) {			
					this.fin_types.add(type);
				}
			}
			else //alfa, beta
				this.fin_types.add(this.get_type_with_strategy(multiplied,strategy));
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}
	
}
