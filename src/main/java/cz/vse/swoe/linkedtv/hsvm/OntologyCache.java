package cz.vse.swoe.linkedtv.hsvm;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.StringTokenizer;

import org.apache.jena.ontology.OntClass;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.rdf.model.ModelFactory;

/**
 * Created by svabo on 2016.
 */

public class OntologyCache {
	
	HashMap<String, HashSet<String>> subtypesforSupertypes = null;
	HashMap<String, HashSet<String>> directSubtypesforSupertypes = null;
	HashMap<String, String> directSupertypeforSubtypes = null;
	HashMap<String, HashSet<String>> supertypesforSubtype = null;
	HashSet<String> subTypes = null;
	HashSet<String> singletonRootClasses = null;
	
	public OntologyCache(String input_file, String direct_input_file) {
		try {
			this.subtypesforSupertypes = new HashMap<String, HashSet<String>>();
			this.directSubtypesforSupertypes = new HashMap<String, HashSet<String>>();
			this.directSupertypeforSubtypes = new HashMap<String, String>();
			this.subTypes = new HashSet<String>();
			this.singletonRootClasses = new HashSet<String>();
			this.supertypesforSubtype = new HashMap<String, HashSet<String>>();
			BufferedReader fp = new BufferedReader(new FileReader("res/"+input_file));
			
			while(true)
			{
				String line = fp.readLine();
				if(line == null) break;
				
				StringTokenizer st = new StringTokenizer(line," ");
				String superType = st.nextToken();				
				HashSet<String> subtypes = new HashSet<String>();
				while(st.hasMoreTokens()) {
					String subtype=st.nextToken();
					subtypes.add(subtype);
					if(this.supertypesforSubtype.containsKey(subtype))
						this.supertypesforSubtype.get(subtype).add(superType);
					else {
						HashSet<String> set1 = new HashSet<String>();
						set1.add(superType);
						this.supertypesforSubtype.put(subtype, set1);
					}
				}
				this.subtypesforSupertypes.put(superType, subtypes);
				this.subTypes.addAll(subtypes);
			}
			fp.close();
			
			fp = new BufferedReader(new FileReader("res/"+direct_input_file));
			
			while(true)
			{
				String line = fp.readLine();
				if(line == null) break;
				
				StringTokenizer st = new StringTokenizer(line," ");
				String superType = st.nextToken();				
				HashSet<String> subtypes = new HashSet<String>();
				while(st.hasMoreTokens()) {
					String subtype=st.nextToken();
					subtypes.add(subtype);
					this.directSupertypeforSubtypes.put(subtype, superType);
				}
				this.directSubtypesforSupertypes.put(superType, subtypes);
			}
			fp.close();
			
			this.init_singleton_rootclasses();
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}	
	
	//31-03-16, init root classes without subclasses
	public void init_singleton_rootclasses() {
		try {
			OntModel dbpedia = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM);
			dbpedia.read("res/dbpedia-excerpt-cleaned.owl", "RDF/XML" );
			
			String clsName="";
			for(OntClass cls : dbpedia.listHierarchyRootClasses().toList()) {
				clsName=cls.toString();
				if(clsName.matches("http://dbpedia.org/ontology/.*")) {
					Iterator<OntClass> i = cls.listSubClasses(true);
					if (!i.hasNext()) {
						clsName=clsName.replaceAll("http://dbpedia.org/ontology/", "");
						this.singletonRootClasses.add(clsName);			
					}
				}
			}
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	public static void main(String[] args) {
		OntologyCache ontologyCache = new OntologyCache("SubClassesMLClassifier.txt","DirectSubClassesMLClassifier.txt");
		//System.out.println(ontologyCache.directSubtypesforSupertypes);
		//System.out.println(ontologyCache.supertypesforSubtype);
		System.out.println("Leaf classes");
		for(String subType : ontologyCache.subTypes) {
			if(!ontologyCache.directSubtypesforSupertypes.containsKey(subType))
				System.out.println(subType);
		}
		System.out.println("SingletonRootClasses");
		for(String subType : ontologyCache.singletonRootClasses) {
			System.out.println(subType);
		}
		System.exit(1);
		System.out.println(ontologyCache.subtypesforSupertypes);
		System.exit(1);
		System.out.println(ontologyCache.directSupertypeforSubtypes);
		System.exit(1);
		System.out.println(ontologyCache.subtypesforSupertypes);
		String type="MusicalArtist";
		if (ontologyCache.subTypes.contains(type))
			System.out.println("continue2");
		if(!ontologyCache.subtypesforSupertypes.containsKey(type)&&!ontologyCache.subTypes.contains(type))
			System.out.println("continue");
		//System.out.println(ontologyCache.subTypes);
		System.out.println(ontologyCache.supertypesforSubtype);
		System.out.println(ontologyCache.singletonRootClasses);
	}

}
