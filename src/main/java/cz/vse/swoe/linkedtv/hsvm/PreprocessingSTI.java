package cz.vse.swoe.linkedtv.hsvm;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.StringTokenizer;

import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.ontology.OntClass;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;

/**
 * Created by svabo on 2016.
 */

public class PreprocessingSTI {
	
	OntModel dbpedia=null;
	OntModel inf = null;
	HashMap<String,String> superclassesForClasses = null;
	HashSet<String> rootClasses = null;
	HashMap<String,HashSet<String>> subclassesForClasses = null;
	
	HashMap<String, HashMap<String,ProbClass3>> lhdtype_to_sti = null; //mapovani resource type na sti types
	HashMap<String, String> resource_lhdtype = null;
	
	OntologyCache ontCache = null;
	
	public PreprocessingSTI() {
		System.out.println("init direct superclasses");
	}
	
	private HashSet<String> get_superclasses(String dbpediaClass) {
		HashSet<String> superclasses = new HashSet<String>();
		OntClass cls;
		if (dbpediaClass.equals("Thing"))
			cls = this.inf.getOntClass("http://www.w3.org/2002/07/owl#Thing");
		else 
			cls = this.inf.getOntClass("http://dbpedia.org/ontology/"+dbpediaClass);
		System.out.println("here"+dbpediaClass);
		System.out.println(cls);
		if(cls==null) { //e.g. = Wikidata:Q11424
			return superclasses;
		}
		if(cls.listSuperClasses(false)==null)
			return superclasses;
		Iterator<OntClass> i = cls.listSuperClasses(true); //for direct
		while (i.hasNext()) {
			String clsName = i.next().toString();
			if(clsName.matches("http://dbpedia.org/ontology/.*"))
				superclasses.add(clsName.replaceAll("http://dbpedia.org/ontology/", ""));
		}
		
		return superclasses;
	}
	
	public HashSet<String> get_subclasses(String dbpediaClass, boolean direct_only) {
		HashSet<String> subclasses = new HashSet<String>();
		OntClass cls;
		if (dbpediaClass.equals("Thing"))
			cls = this.inf.getOntClass("http://www.w3.org/2002/07/owl#Thing");
		else 			
			cls = this.inf.getOntClass("http://dbpedia.org/ontology/"+dbpediaClass);
		Iterator<OntClass> i = cls.listSubClasses(direct_only);
		while (i.hasNext()) {
			subclasses.add(i.next().toString().replaceAll("http://dbpedia.org/ontology/", ""));			
		}
		return subclasses;
	}
	
	//05-03-16, init DBpedia
	public void init_direct_superclasses_of_DBpedia() {
		try {
			this.superclassesForClasses = new HashMap<String, String>();
			this.rootClasses = new HashSet<String>();
			BufferedReader fp = new BufferedReader(new FileReader("res/DirectSuperClassesDBpedia.txt"));
			
			while(true)
			{
				String line = fp.readLine();
				
				if(line == null) break;
				
				StringTokenizer st = new StringTokenizer(line," ");
				String type = st.nextToken();				
				String superclass = "";
				if (st.hasMoreTokens()) {
					superclass=st.nextToken();
				}
				else {
					this.rootClasses.add(type);
				}
				this.superclassesForClasses.put(type, superclass);
			}
			fp.close();
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	//29-03-16, init direct subclasses
	public void init_direct_subclasses_of_DBpedia() {
		try {
			this.subclassesForClasses = new HashMap<String, HashSet<String>>();
			BufferedReader fp = new BufferedReader(new FileReader("res/DirectSubClassesDBpedia.txt"));
			
			while(true)
			{
				String line = fp.readLine();
				
				if(line == null) break;
				
				StringTokenizer st = new StringTokenizer(line," ");
				String type = st.nextToken();				
				HashSet<String> subclasses = new HashSet<String>();
				while (st.hasMoreTokens()) {
					subclasses.add(st.nextToken());
				}
				this.subclassesForClasses.put(type, subclasses);
			}
			fp.close();
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	//05-03-16, init direct superclasses of DBpedia
	public void init_ontology_cache(String dbpedia_ontology) {
		try {
			dbpedia = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM);
			dbpedia.read(dbpedia_ontology, "RDF/XML" );
			inf = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM_RDFS_INF,dbpedia);
			
			PrintWriter toFile;			
			if(dbpedia_ontology.equals("res/dbpedia-excerpt-cleaned.owl"))
				toFile = new PrintWriter(new FileWriter("res/DirectSuperClassesMLClassifier.txt"));
			else
				toFile = new PrintWriter(new FileWriter("res/DirectSuperClassesDBpedia.txt"));
			
			String clsName="";
			for(OntClass cls : inf.listClasses().toList()) {
				clsName=cls.toString();
				if(clsName.matches("http://dbpedia.org/ontology/.*")) {
					clsName=clsName.replaceAll("http://dbpedia.org/ontology/", "");
					toFile.print(clsName+" ");
					HashSet<String> superclasses = this.get_superclasses(clsName);								
					String line="";
					for(String r : superclasses) {
						line=line+r+" ";
					}				
					toFile.println(line.trim());
				}
			}
			toFile.close();
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	//05-03-16, init subclasses of DBpedia
	public void init_ontology_cache_subclasses(String dbpedia_ontology) {
		try {
			dbpedia = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM);
			dbpedia.read(dbpedia_ontology, "RDF/XML" );
			inf = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM_RDFS_INF,dbpedia);
			
			PrintWriter toFile;			
			if(dbpedia_ontology.equals("res/dbpedia-excerpt-cleaned.owl"))
				toFile = new PrintWriter(new FileWriter("res/SubClassesMLClassifier.txt"));
			else
				toFile = new PrintWriter(new FileWriter("res/SubClassesDBpedia.txt"));
			
			String clsName="";
			for(OntClass cls : inf.listClasses().toList()) {
				clsName=cls.toString();
				if(clsName.matches("http://dbpedia.org/ontology/.*")) {
					clsName=clsName.replaceAll("http://dbpedia.org/ontology/", "");					
					HashSet<String> subclasses = this.get_subclasses(clsName,false);
					if(subclasses.isEmpty())
						continue;
					toFile.print(clsName+" ");
					String line="";
					for(String r : subclasses) {
						line=line+r+" ";
					}				
					toFile.println(line.trim());
				}
			}
			toFile.close();
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	
	//29-03-16, direct subclasses
	public void init_ontology_cache_direct_subclasses(String dbpedia_ontology) {
		try {
			dbpedia = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM);
			dbpedia.read(dbpedia_ontology, "RDF/XML" );
			inf = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM_RDFS_INF,dbpedia);
			
			PrintWriter toFile;			
			if(dbpedia_ontology.equals("res/dbpedia-excerpt-cleaned.owl"))
				toFile = new PrintWriter(new FileWriter("res/DirectSubClassesMLClassifier.txt"));
			else
				toFile = new PrintWriter(new FileWriter("res/DirectSubClassesDBpedia.txt"));
			
			String clsName="";
			for(OntClass cls : inf.listClasses().toList()) {
				clsName=cls.toString();
				if(clsName.matches("http://dbpedia.org/ontology/.*")) {
					clsName=clsName.replaceAll("http://dbpedia.org/ontology/", "");					
					HashSet<String> subclasses = this.get_subclasses(clsName,true);
					if(subclasses.isEmpty())
						continue;
					toFile.print(clsName+" ");
					String line="";
					for(String r : subclasses) {
						line=line+r+" ";
					}				
					toFile.println(line.trim());
				}
			}
			toFile.close();
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	//29-03-16, uplne podle classification ontologie podle siblings
	public void preprocess_debug_results2(String file) {
		try {
			PrintWriter toFile;			
			toFile = new PrintWriter(new FileWriter("res/sti.debug.inference.prob"));
			
			BufferedReader vstup = null;
			int position=0; //1=type,2=list,3=pruned,4=selected
			vstup = new BufferedReader(new FileReader(file));				
			String s="";
			HashMap<String,Integer> list=new HashMap<String,Integer>();
			String lhdtype="";
			String selectedMapping="";
			int frequency_root_classes=0;
			while ((s = vstup.readLine()) != null) {
				if(s.trim().equals("# type"))	 {
					if(!lhdtype.equals("")) {
						toFile.println("# type ");
						toFile.println(lhdtype);
						toFile.println("#list of  candidate mapped types,probability");
						double prob=0.0;
						for(String type : list.keySet()) {
							if(!this.superclassesForClasses.containsKey(type)) {
								continue;
							}
							if(this.superclassesForClasses.get(type).equals("")) {
								prob=(double)list.get(type)/(double)frequency_root_classes;
							}
							else {
								try {
									String directsuperclass=this.superclassesForClasses.get(type);
									int sibling_sum_support=0;
									for(String sibling : this.subclassesForClasses.get(directsuperclass)) {
										if(list.containsKey(sibling))
											sibling_sum_support+=list.get(sibling);
									}
									prob=(double)list.get(type)/(double)sibling_sum_support;
								}
								catch(Exception e) {
									System.out.println(type);
									e.printStackTrace();
								}
							}
							toFile.println(type+","+prob);
						}
						toFile.flush();
						toFile.println("#Selected mapping");
						toFile.println(selectedMapping);
						toFile.println();
					}
					
					position=1;
					lhdtype="";
					frequency_root_classes=0;
				}
				else if(s.trim().equals("# list of candidate mapped types, frequency, confidence"))	 {
					position=2;
					list=new HashMap<String,Integer>();
				}
				else if(s.trim().equals("# pruned set of types, frequency, confidence"))	 {
					position=3;
				}
				else if(s.trim().equals("# selected mapping, confidence"))	 {
					position=4;
					selectedMapping="";
				}
				else if(position==1) {
					lhdtype=s.trim();
				}
				else if(position==2) {
					s = s.trim();
					String type = s.substring(s.lastIndexOf("/")+1,s.indexOf(","));
					String value = s.substring(s.indexOf(",")+2,s.lastIndexOf(","));
					list.put(type.trim(), new Integer(new Integer(value).intValue()));
					if(this.rootClasses.contains(type.trim()))
						frequency_root_classes+=new Integer(value).intValue();
				}
				else if(position==3) {
					;
				}
				else if(position==4) {
					if(!s.trim().equals(""))
						selectedMapping = s.substring(0,s.indexOf(","));
				}
			}
			toFile.println("# type ");
			toFile.println(lhdtype);
			toFile.println("#list of  candidate mapped types,probability");
			double prob=0.0;
			for(String type : list.keySet()) {
				if(!this.superclassesForClasses.containsKey(type)) {
					continue;
				}
				if(this.superclassesForClasses.get(type).equals("")) {
					prob=(double)list.get(type)/(double)frequency_root_classes;
				}
				else {
					prob=(double)list.get(type)/(double)list.get(this.superclassesForClasses.get(type));
				}
				toFile.println(type+","+prob);
			}
			toFile.flush();
			toFile.println("#Selected mapping");
			toFile.println(selectedMapping);
			toFile.println();
			
			toFile.close();
			vstup.close();
		}
		catch(Exception e) {			
			e.printStackTrace();
		}
	}
	
}
