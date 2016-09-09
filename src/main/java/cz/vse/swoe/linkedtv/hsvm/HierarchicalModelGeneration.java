package cz.vse.swoe.linkedtv.hsvm;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.StringTokenizer;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.io.FileUtils;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.jibx.schema.codegen.extend.DefaultNameConverter;
import org.jibx.schema.codegen.extend.NameConverter;

import org.apache.jena.ontology.OntClass;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;

import cz.vse.swoe.lucene.Indexer;
import cz.vse.swoe.lucene.Indexer.Tuple;

/**
 * Created by svabo on 2016.
 */

public class HierarchicalModelGeneration {
	
	OntModel m = ModelFactory.createOntologyModel( OntModelSpec.OWL_MEM );
	HashSet<String> uniqueClasses;
	OntModel dbpedia = null;
	OntModel inf = null;
	Integer level=0;
	ArrayList<ArrayList<String>> classesInLayers = null;
	int number_of_classes=0;
	ArrayList<ArrayList<String>> SVM_models = null;
	final private File indexDir1Sa = new File("testindex1Sa");
	final private File indexDir1Cat = new File("testindex1Cat");
	final private File indexDir2 = new File("testindex2");
	private Indexer indexer;
	
	//OntModel modelInstances = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM);
	
	public HierarchicalModelGeneration(boolean indexLucene, String dbpediaFile, String lang_short_abstracts, String lang_article_categories, String lang_instance_types) {
		try {
			dbpedia = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM);
			InputStream is = new FileInputStream(dbpediaFile);
			dbpedia.read(is, "RDF/XML" );
			inf = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM_RDFS_INF,dbpedia);
            
			//indexing
			if (indexLucene) {
				FileUtils.deleteDirectory(indexDir1Sa);
				FileUtils.deleteDirectory(indexDir1Cat);
				FileUtils.deleteDirectory(indexDir2);
		        indexDir1Sa.mkdirs();
		        indexDir1Cat.mkdirs();
		        indexDir2.mkdirs();
			}
	        indexer = new Indexer(indexDir1Sa, indexDir1Cat, indexDir2, dbpediaFile);
	        if(indexLucene) {
	        	InputStream is1 = new BZip2CompressorInputStream(new FileInputStream(lang_short_abstracts));
	        	InputStream is2 = new BZip2CompressorInputStream(new FileInputStream(lang_article_categories));
	        	InputStream is3 = new BZip2CompressorInputStream(new FileInputStream(lang_instance_types));
	            long t1 = System.currentTimeMillis();				
	            indexer.index1Sa(new InputStream[]{is1});
	            long t2 = System.currentTimeMillis();
				System.out.println("sa indexed:"+(t2-t1));
				
				t1 = System.currentTimeMillis();					            
	            indexer.index1Cat(new InputStream[]{is2});
	            t2 = System.currentTimeMillis();
				System.out.println("cat indexed:"+(t2-t1));
				
				//types not indexing - not really necessary
				t1 = System.currentTimeMillis();
	            indexer.index2(new InputStream[]{is3});
	            t2 = System.currentTimeMillis();
				System.out.println("types indexed:"+(t2-t1));				
	        }
            System.out.println("initializing searchers");
            long t1 = System.currentTimeMillis();
            indexer.initSearcher1Sa();
            indexer.initSearcher1Cat();
            indexer.initSearcher2();
            long t2 = System.currentTimeMillis();
			System.out.println("indexes initialized:"+(t2-t1));
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	private void random_entities_pick_lucene(PrintWriter toFile1, PrintWriter toFile2, String dbpediaClass, int max) {
		try {			    		    	
			Tuple[] listOfResources = null;
			
			int resources=0;
			listOfResources = this.indexer.search2("http://dbpedia.org/ontology/"+dbpediaClass, 3000000);
			HashSet<Integer> selectedResources = new HashSet<Integer>(); 
		    while(resources<max) {			    	
		    	//take randomly some resource of the dbpedia class
		    	int random_number = (int)(Math.random()*listOfResources.length);			    				    				    
		    	if(selectedResources.contains(random_number)) {
		    		continue;		    		
		    	}
		    	else {
		    		selectedResources.add(random_number);
		    	}
		    	String res=listOfResources[random_number].object;
		    	resources++;
	    		//get short_abstract and categories for resource (it is decoded inside)
		    	String sa_cat=this.get_categories_and_short_abstracts_for_resource_lucene(res);
		    	
		    	String categories=sa_cat.substring(0,sa_cat.indexOf(":"));
		    	String sa=sa_cat.substring(sa_cat.indexOf(":")+1);
		    	
		    	res=res.replaceAll("http://nl.dbpedia.org/resource/", "").replaceAll("http://de.dbpedia.org/resource/", "").replaceAll("http://dbpedia.org/resource/", "");
		    	res= URLEncoder.encode(res,"UTF-8");
				toFile1.println("'"+res+"','"+categories+"',"+dbpediaClass);
				toFile2.println("'"+res+"','"+sa+"',"+dbpediaClass);
				toFile1.flush();
				toFile2.flush();
		    }
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	private int queryResourcesForClassLucene(String dbpediaClass) {		
		return this.indexer.search2(dbpediaClass, 5000000).length;
	}
	
	private void traverse_classes_hierarchy1(boolean createOntology, PrintWriter toFile, OntClass cls, Integer level, int min_instances) {
		int instances=this.queryResourcesForClassLucene(cls.getURI());
		if(instances>=min_instances) {
    		
			if(createOntology) {
				Resource s = ResourceFactory.createResource(cls.getURI());
				Property prop = ResourceFactory.createProperty("http://keg.vse.cz/swoe/dbpedia/hasInstances");
				Literal o = ResourceFactory.createPlainLiteral(String.valueOf(instances));
				org.apache.jena.rdf.model.Statement statement = ResourceFactory.createStatement(s, prop, o);
			    this.m.add(statement);
			    
			    s = ResourceFactory.createResource(cls.getURI());
				prop = ResourceFactory.createProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#type");
				Resource oR = ResourceFactory.createResource("http://www.w3.org/2002/07/owl#Class");
				statement = ResourceFactory.createStatement(s, prop, oR);
			    this.m.add(statement);
			}
			
			if(this.uniqueClasses.contains(cls.getLocalName()))
    			return;
    		else 
    			this.uniqueClasses.add(cls.getLocalName());
    		toFile.println(level+":"+cls.getLocalName()+":"+cls.listSubClasses(true).toList().size()+":"+instances);
    		toFile.flush();
    		
    		if(createOntology) {
    			
    			for(OntClass clsSup : cls.listSuperClasses(true).toList()) {
	    			if(!clsSup.toString().matches(".*dbpedia.org/ontology/.*")&&!clsSup.toString().equals("http://www.w3.org/2002/07/owl#Thing"))
	    				continue;
	    			Resource s = ResourceFactory.createResource(cls.getURI());
	    			Property prop = ResourceFactory.createProperty("http://www.w3.org/2000/01/rdf-schema#subClassOf");
					Resource oR = ResourceFactory.createResource(clsSup.getURI());
					org.apache.jena.rdf.model.Statement statement = ResourceFactory.createStatement(s, prop, oR);
				    this.m.add(statement);
				}
			}
    		
    		//next level
    		level++;
    		for(OntClass cls2 : cls.listSubClasses(true).toList()) {    			
    			traverse_classes_hierarchy1(createOntology, toFile,cls2,level,min_instances);
    		}
		}
	}
	
	public void traverse_classes_hierarchy_start(boolean createOntology, int min_instances) {
		try {			
			this.uniqueClasses=new HashSet<String>();
			PrintWriter toFile = new PrintWriter(new FileWriter("res/classesInstancesHierarchy.txt", false));
			level = new Integer(1);			
			
			for ( OntClass cls : inf.listClasses().toList() ) {      
				boolean root_class=false;
				
				if(cls.listSuperClasses().toList().size()==0) {
					root_class=true;
				}
				else {
					int fromDbpedia=0;
					for(OntClass s : cls.listSuperClasses().toList()) {
						if(s.toString().matches(".*dbpedia.org/ontology/.*"))
							fromDbpedia++;
					}
					if(fromDbpedia==0) {
						root_class=true;
					}
				}
				
				if(root_class) {
					traverse_classes_hierarchy1(createOntology,toFile,cls,level,min_instances);;
				}
			}
			toFile.close();
			
			if (createOntology) {
				Resource s = ResourceFactory.createResource("http://keg.vse.cz/swoe/dbpedia/hasInstances");
				Property prop = ResourceFactory.createProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#type");
				Resource o = ResourceFactory.createResource("http://www.w3.org/2002/07/owl#AnnotationProperty");
				org.apache.jena.rdf.model.Statement statement = ResourceFactory.createStatement(s, prop, o);
			    this.m.add(statement);
			    
				OutputStream stream = new FileOutputStream("res/dbpedia-excerpt.owl");
				RDFDataMgr.write(stream, m, Lang.RDFXML);
			}
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}		
	
	public String get_categories_and_short_abstracts_for_resource_lucene(String resource) {
		String shortAbstractEn="",cats="";
    	String categories="";
    	for (Tuple tuple : this.indexer.search1Sa(resource, 1000)) {
    		shortAbstractEn=tuple.object.toString();
        }
    	for (Tuple tuple : this.indexer.search1Cat(resource, 1000)) {
    		categories+=" "+tuple.object.toString();
    	}
    	categories=categories.trim().replaceAll("http://nl.dbpedia.org/resource/Categorie:","").replaceAll("http://de.dbpedia.org/resource/Kategorie:","").replaceAll("http://dbpedia.org/resource/Category:","").replaceAll("_", " ").replaceAll("\n", " ").replaceAll("@en", "").toLowerCase().replaceAll("'","").replaceAll("\\\"", "").replaceAll(resource.replaceAll("http://dbpedia.org/resource/", "").replaceAll("[-\\+\\*/%\\(\\)]", ""), "").replaceAll("[0-9]*","");
	    //delimiters according to Weka
	    categories=categories.replaceAll(",", " ").replaceAll("\\.", " ").replaceAll(";", " ").replaceAll(":", " ").replaceAll("\\(", " ").replaceAll("\\)", " ").replaceAll("\\?", " ").replaceAll("!", " ");
	    cats="";
    	StringTokenizer st = new StringTokenizer(categories," ");
    	while (st.hasMoreTokens()) {
    		String s = st.nextToken();
    		NameConverter nc = new DefaultNameConverter();
    		cats=cats+" "+nc.depluralize(s);
    	}
    	cats=cats.trim();
	    
	    shortAbstractEn=shortAbstractEn.replaceAll("'","").replaceAll("\"", "").replaceAll(resource, "").replaceAll("[0-9]*","").replaceAll("\n", " ").replaceAll("@en", "").toLowerCase().replaceAll("\\\"", "").replaceAll("\\\\", "");
	    shortAbstractEn=shortAbstractEn.replaceAll(",", " ").replaceAll("\\.", " ").replaceAll(";", " ").replaceAll(":", " ").replaceAll("\\(", " ").replaceAll("\\)", " ").replaceAll("\\?", " ").replaceAll("!", " ");
	    return cats+":"+shortAbstractEn;
	}
	
	//19-08-16, adding instances_number
	public void generate_arff_files(String svmModelsFile, int instances_number) {
		try {
			//load SVM_models from file
			ArrayList<ArrayList<String>> SVM_models=new ArrayList<ArrayList<String>>();
			ArrayList<String> SVMmodelsNames = new ArrayList<String>();
			BufferedReader vstup = null;
			vstup = new BufferedReader(new FileReader ("res/"+svmModelsFile));
			String s="";
			StringTokenizer st = null;			
			while ((s = vstup.readLine()) != null) {
				if (s.matches("#.*")) continue;
				if (s.matches("@.*")) continue;
				if(s.indexOf("-")!=-1) {
					String SVMmodelName=s.substring(0,s.indexOf("-"));
					SVMmodelsNames.add(SVMmodelName);
				}
				String classes=s.substring(s.indexOf(":")+1).replaceAll("\\[", "").replaceAll("\\]", "");
				st = new StringTokenizer(classes,",");
				ArrayList<String> classesInModel = new ArrayList<String>(); 
				while(st.hasMoreTokens()) {
					String cls=st.nextToken().trim();
					classesInModel.add(cls);
				}
				SVM_models.add(classesInModel);
			}		
			vstup.close();
			//end load SVM_models from file
			
			PrintWriter toFile1,toFile2;
			for(int i=0;i<SVM_models.size();i++) {
				if(SVMmodelsNames.get(i)!=null) {
					toFile1 = new PrintWriter(new FileWriter("res/arff-complete/categories-"+SVMmodelsNames.get(i)+"-dataset.arff", false));
					toFile2 = new PrintWriter(new FileWriter("res/arff-complete/short_abstract-"+SVMmodelsNames.get(i)+"-dataset.arff", false));
				}
				else {
					toFile1 = new PrintWriter(new FileWriter("res/arff-complete/categories-"+i+"-dataset.arff", false));
					toFile2 = new PrintWriter(new FileWriter("res/arff-complete/short_abstract-"+i+"-dataset.arff", false));
				}
				
				toFile1.println("@relation 'dbpedia-categories-"+i+"-dataset'");
				toFile1.println("@attribute entityName string");
				toFile1.println("@attribute categories string");			
				toFile1.println("@attribute className {"+SVM_models.get(i).toString().replaceAll("\\[", "").replaceAll("\\]", "")+"}");
				toFile1.println("");
				toFile1.println("@data");
				toFile1.println("");
				
				toFile2.println("@relation 'dbpedia-short_abstract-"+i+"-dataset'");
				toFile2.println("@attribute entityName string");
				toFile2.println("@attribute short_abstract string");			
				toFile2.println("@attribute className {"+SVM_models.get(i).toString().replaceAll("\\[", "").replaceAll("\\]", "")+"}");
				toFile2.println("");
				toFile2.println("@data");
				toFile2.println("");
				
				//classes in particulat SVM model
				for(String className : SVM_models.get(i)) {
					random_entities_pick_lucene(toFile1,toFile2,className,instances_number);
				}
				toFile1.flush();
				toFile1.close();
				toFile2.flush();
				toFile2.close();
			}
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	//remove classes having one or only two subclasses. These subclasses will replace them in taxonomy.
	public void adjustOntology(String ontology) {
		try {	
			OntModel ont = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM );
			ont.read("res/"+ontology, "RDF/XML" );
			
			for (OntClass cls : ont.listClasses().toList()) {
				int n = cls.listSubClasses(true).toList().size();
				if(n>0&&n<5) { //min__number_subclasses=5
					OntClass superClass=cls.listSuperClasses(true).toList().get(0);
					for(OntClass cls1 : cls.listSubClasses(true).toList()) {
						Resource s = ResourceFactory.createResource(cls1.getURI());
		    			Property prop = ResourceFactory.createProperty("http://www.w3.org/2000/01/rdf-schema#subClassOf");
						Resource oR = ResourceFactory.createResource(superClass.getURI());
						org.apache.jena.rdf.model.Statement statement = ResourceFactory.createStatement(s, prop, oR);
					    ont.add(statement);
					}
					cls.remove();
				}
			}
				
			//save ontology in new file
			OutputStream stream = new FileOutputStream("res/dbpedia-excerpt-cleaned.owl");
			RDFDataMgr.write(stream, ont, Lang.RDFXML);
		}
		catch(Exception e) {
			e.printStackTrace();
		}
		
	}
	
	public void init_ontology_cache(String ontology) {
		try {
			PrintWriter toFile;
			toFile = new PrintWriter(new FileWriter("res/SubClassesMLClassifier.txt"));
			
			OntModel ont = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM_RDFS_INF);			
			ont.read("res/"+ontology, "RDF/XML" );
			for (OntClass cls : ont.listClasses().toList()) {
				int n = cls.listSubClasses(false).toList().size();
				if(n>0) {
					toFile.print(cls.getLocalName()+" ");
					StringBuilder sb = new StringBuilder();
					for(OntClass cls1 : cls.listSubClasses(false).toList()) {
						sb.append(cls1.getLocalName()+" ");
					}
					toFile.println(sb.toString().trim());
				}
				toFile.flush();
			}
			toFile.close();
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	//27-03-15,set up SVM models
	public void subclassesSplitIntoSVMmodels(String ontology) {
		try {
			int instances = 0;
			PrintWriter toFile = new PrintWriter(new FileWriter("res/svmModelsSubclasses.txt", false));
			StringBuffer sb = new StringBuffer();
			
			OntModel ont = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM_RDFS_INF);
			ont.read("res/"+ontology, "RDF/XML" );
			
			int models=0;
			for (OntClass cls : ont.listClasses().toList()) {
				int n = cls.listSubClasses(true).toList().size();
				if(n>0) {
					models++;
					sb.append(cls.getLocalName()+"-");
					for(RDFNode node : cls.listPropertyValues(ResourceFactory.createProperty("http://keg.vse.cz/swoe/dbpedia/hasInstances")).toList()) {
						sb.append(node.toString()+"-");
						instances+=node.asLiteral().getInt();
						break;
					}
					ArrayList<String> SVM_model = new ArrayList<String>();
					for(OntClass cls1 : cls.listSubClasses(true).toList()) {
						SVM_model.add(cls1.getLocalName());
					}
					sb.append(SVM_model.size()+":"+SVM_model+"\n");
				}
			}
			sb.append("Thing-");
			ArrayList<String> SVM_model = new ArrayList<String>();
			for(OntClass cls1 : ont.listHierarchyRootClasses().toList()) {
				SVM_model.add(cls1.getLocalName());
			}
			sb.append(SVM_model.size()+":"+SVM_model+"\n");
			
			toFile.println("@instances:"+instances);
			toFile.println("#number of classes:"+ont.listClasses().toList().size());
			toFile.println("#number of SVM models:"+models);
			toFile.print(sb.toString());
			toFile.close();			
			toFile.close();
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	//01-04-15, filter raw arff files
	public void filter_arff_files(String svm_models) {		
		try {
			//init
			PrintWriter toFile1 = new PrintWriter(new FileWriter("res/uniqueAttributesSa.txt", false));
			PrintWriter toFile2 = new PrintWriter(new FileWriter("res/uniqueAttributesCat.txt", false));
			HashSet<String> stop_words = new HashSet<String>();
			BufferedReader swFile = new BufferedReader(new FileReader("stopwords.en.txt"));
			while(true) {
				String l = swFile.readLine();
				if(l == null) break;
				stop_words.add(l);
			}
			swFile.close();
			
			//process arff files
			BufferedReader input = new BufferedReader(new FileReader(svm_models));
			ArrayList<String> unique_attributes_sa = new ArrayList<String>();
			ArrayList<String> unique_attributes_cat = new ArrayList<String>();
			
			while(true) {
				String SVMmodelName = "";
				String line = input.readLine();
				if(line == null) break;
				
				if (line.matches("#.*")) continue;
				if (line.matches("@.*")) continue;
				if(line.indexOf("-")!=-1) {
					SVMmodelName=line.substring(0,line.indexOf("-"));
					
					//sa
					BufferedReader inputArff = new BufferedReader(new FileReader("res/arff-complete/"+"short_abstract-"+SVMmodelName+"-dataset.arff"));
					//BufferedReader inputArff = new BufferedReader(new FileReader("res/arff-complete/"+"categories-"+SVMmodelName+"-dataset.arff"));
					String data = "";
					while(true) {
						String row = inputArff.readLine();
						if(row == null) break;
						
						if (row.equals("")||row.matches("#.*")||row.matches("@.*")) continue;
						data = row.substring(row.indexOf(",")+2, row.lastIndexOf(",")-1);
						String[] splitted = data.split("[ \\r\\n\\t.,;:\\\'\\\"()?!\"]");
						for(int i=0; i<splitted.length;i++) {
							String s = splitted[i];
							s = s.toLowerCase().trim();
							if(!s.equals("")&&!stop_words.contains(s)&&!unique_attributes_sa.contains(s))
								unique_attributes_sa.add(s);
						}
					}
					inputArff.close();
					
					//cat
					inputArff = new BufferedReader(new FileReader("res/arff-complete/"+"categories-"+SVMmodelName+"-dataset.arff"));
					data = "";
					while(true) {
						String row = inputArff.readLine();
						if(row == null) break;
						
						if (row.equals("")||row.matches("#.*")||row.matches("@.*")) continue;
						data = row.substring(row.indexOf(",")+2, row.lastIndexOf(",")-1);
						String[] splitted = data.split("[ \\r\\n\\t.,;:\\\'\\\"()?!\"]");
						for(int i=0; i<splitted.length;i++) {
							String s = splitted[i];
							s = s.toLowerCase().trim();
							if(!s.equals("")&&!stop_words.contains(s)&&!unique_attributes_cat.contains(s))
								unique_attributes_cat.add(s);
						}
					}
					inputArff.close();
				}
			}
			input.close();
			System.out.println(unique_attributes_sa.size());
			System.out.println(unique_attributes_cat.size());
			toFile1.println(unique_attributes_sa.toString().replaceAll("\\[", "").replaceAll("\\]", ""));
			toFile2.println(unique_attributes_cat.toString().replaceAll("\\[", "").replaceAll("\\]", ""));
			toFile1.close();
			toFile2.close();
			
			//2nd process - output LibSVM format wrt. position of unique attibutes
			input = new BufferedReader(new FileReader(svm_models));
			PrintWriter toFile = null;
			HashMap<String,Counter> atts = null;
			while(true) {				
				String line = input.readLine();
				if(line == null) break;
				String SVMmodelName = "";
								
				if (line.matches("#.*")) continue;
				if(line.indexOf("-")!=-1) {
					SVMmodelName=line.substring(0,line.indexOf("-"));					
					
					//sa
					toFile = new PrintWriter(new FileWriter("res/LibSVM-unique/sa-"+SVMmodelName+".train", false));
					atts = new HashMap<String,Counter>();
					BufferedReader inputArff = new BufferedReader(new FileReader("res/arff-complete/"+"short_abstract-"+SVMmodelName+"-dataset.arff"));
					String data = "";
					String lastCls = "";
					String cls = "";
					int nrClass=-1;
					while(true) {
						String row = inputArff.readLine();
						if(row == null) break;
						
						if (row.equals("")||row.matches("#.*")||row.matches("@.*")) continue;
						atts.clear();
						data = row.substring(row.indexOf(",")+2, row.lastIndexOf(",")-1);
						cls = row.substring(row.lastIndexOf(",")+1).trim();
						if(!cls.equals(lastCls)) {
							lastCls=cls;
							nrClass++;
						}
						String[] splitted = data.split("[ \\r\\n\\t.,;:\\\'\\\"()?!\"]");
						for(int i=0; i<splitted.length;i++) {
							String s = splitted[i];
							s = s.toLowerCase();
							if(unique_attributes_sa.contains(s)) {
								if(atts.containsKey(s))
									atts.get(s).freq++;
								else
									atts.put(s, new Counter());
							}
						}
						toFile.print(nrClass);
						for(int i=0;i<unique_attributes_sa.size();i++) {
							if(atts.containsKey(unique_attributes_sa.get(i)))
								toFile.print(" "+i+":"+atts.get(unique_attributes_sa.get(i)).freq);							
						}
						toFile.println();
						toFile.flush();
					}
					inputArff.close();
					toFile.close();
					
					//cat
					toFile = new PrintWriter(new FileWriter("res/LibSVM-unique/cat-"+SVMmodelName+".train", false));
					atts = new HashMap<String,Counter>();
					inputArff = new BufferedReader(new FileReader("res/arff-complete/"+"categories-"+SVMmodelName+"-dataset.arff"));
					data = "";
					lastCls = "";
					cls = "";
					nrClass=-1;
					while(true) {
						String row = inputArff.readLine();
						if(row == null) break;
						
						if (row.equals("")||row.matches("#.*")||row.matches("@.*")) continue;
						atts.clear();
						data = row.substring(row.indexOf(",")+2, row.lastIndexOf(",")-1);
						cls = row.substring(row.lastIndexOf(",")+1).trim();
						if(!cls.equals(lastCls)) {
							lastCls=cls;
							nrClass++;
						}
						String[] splitted = data.split("[ \\r\\n\\t.,;:\\\'\\\"()?!\"]");
						for(int i=0; i<splitted.length;i++) {
							String s = splitted[i];
							s = s.toLowerCase();
							if(unique_attributes_cat.contains(s)) {
								if(atts.containsKey(s))
									atts.get(s).freq++;
								else
									atts.put(s, new Counter());
							}
						}
						toFile.print(nrClass);
						for(int i=0;i<unique_attributes_cat.size();i++) {
								if(atts.containsKey(unique_attributes_cat.get(i)))
									toFile.print(" "+i+":"+atts.get(unique_attributes_cat.get(i)).freq);							
							}
						toFile.println();
						toFile.flush();
					}
					inputArff.close();
					toFile.close();
				}
			}
			input.close();
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	public void close() {
		try {
			this.indexer.close();
			this.indexer.closeSearcher1Sa();
			this.indexer.closeSearcher1Cat();
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}
	
}
