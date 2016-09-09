package cz.vse.swoe.lucene;

import org.apache.jena.ontology.OntClass;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.riot.RiotException;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;

/**
 * Created by propan on 6. 4. 2015.
 * Upgraded by svabo on 8. 4. 2015 2016.
 */

public class Indexer {

    final private Logger logger = LoggerFactory.getLogger(getClass());
    final private Directory directory1Sa;
    final private Directory directory1Cat;
    final private Directory directory2;
    private IndexSearcher is1Sa;
    private IndexSearcher is1Cat;
    private IndexSearcher is2;
    public DirectoryReader dr1Sa;
    private DirectoryReader dr1Cat;
    private DirectoryReader dr2;
    
    HashMap<String,ArrayList<String>> wikiToDBclasses = new HashMap<String,ArrayList<String>>();
    
    public Indexer(File indexDir1Sa, File indexDir1Cat, File indexDir2, String DBpediaOntology) throws IOException {
        directory1Sa = FSDirectory.open(indexDir1Sa.toPath());
        directory1Cat = FSDirectory.open(indexDir1Cat.toPath());
        directory2 = FSDirectory.open(indexDir2.toPath());
        
        String dbpediaFile = DBpediaOntology;
        InputStream is = new FileInputStream(dbpediaFile);
        OntModel dbpedia = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM); 
        dbpedia.read(is, "RDF/XML");
        OntModel inf = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM_RDFS_INF, dbpedia);
        inf.setStrictMode(false);
        for (OntClass cl : inf.listClasses().toList()) {
	        	for (OntClass cl1 : cl.listEquivalentClasses().toList()) {
	        		if(cl1.toString().matches("http://www.wikidata.org/entity/.*")||cl1.toString().matches("http://schema.org/.*")) {
		        		if(wikiToDBclasses.containsKey(cl1.getURI().toString()))
		        			wikiToDBclasses.get(cl1.getURI().toString()).add(cl.getURI().toString());
		        		else 
		        			wikiToDBclasses.put(cl1.getURI().toString(), new ArrayList<String>(Arrays.asList(cl.getURI().toString())));
	        		}
	        	}
        }
    }
    
    
    public void close() throws IOException {
        directory1Sa.close();
        directory1Cat.close();
        directory2.close();
    }
    
    private void indexBufferedString1Sa(IndexWriter iw, String string) throws IOException {
        final Model model = ModelFactory.createDefaultModel();
        
        try {
            model.read(new ByteArrayInputStream(string.getBytes()), null, "N-TRIPLE");
        } catch (RiotException e) {
            try (final BufferedReader sr = new BufferedReader(new StringReader(string))) {
                final String line1 = sr.readLine();
                String line = sr.readLine();
                if (line != null) {
                    indexBufferedString1Sa(iw, line1);
                    do {
                        indexBufferedString1Sa(iw, line);
                    } while ((line = sr.readLine()) != null);
                } else {
                    logger.warn("Invalid triple: " + string);
                }
            }
        }
        
        if (model.size() > 0) {
            for (Iterator<Statement> it = model.listStatements(); it.hasNext(); ) {
                final Statement stmt = it.next();
                final Document doc = new Document();                
                doc.add(new StringField("subject", URLDecoder.decode(stmt.getSubject().getURI(),"UTF-8"), Field.Store.NO));
                //doc.add(new StringField("subject", stmt.getSubject().getURI(), Field.Store.NO));
                //doc.add(new StringField("subject", stmt.getSubject().getURI(), Field.Store.YES));
                //doc.add(new StoredField("predicate", stmt.getPredicate().getURI()));                
                //resourcesSa.add(stmt.getSubject().getURI());
                doc.add(new StoredField("object", stmt.getObject().toString()));
                iw.addDocument(doc);
            }
        }
    }
    
    private void indexBufferedString1Cat(IndexWriter iw, String string) throws IOException {
        final Model model = ModelFactory.createDefaultModel();
        
        try {
            model.read(new ByteArrayInputStream(string.getBytes()), null, "N-TRIPLE");
        } catch (RiotException e) {
            try (final BufferedReader sr = new BufferedReader(new StringReader(string))) {
                final String line1 = sr.readLine();
                String line = sr.readLine();
                if (line != null) {
                    indexBufferedString1Sa(iw, line1);
                    do {
                        indexBufferedString1Sa(iw, line);
                    } while ((line = sr.readLine()) != null);
                } else {
                    logger.warn("Invalid triple: " + string);
                }
            }
        }
        
        if (model.size() > 0) {
            for (Iterator<Statement> it = model.listStatements(); it.hasNext(); ) {
                final Statement stmt = it.next();
                final Document doc = new Document();                
                doc.add(new StringField("subject", URLDecoder.decode(stmt.getSubject().getURI(), "UTF-8"), Field.Store.NO));
                doc.add(new StoredField("object", stmt.getObject().toString()));
                iw.addDocument(doc);
            }
        }
    }
    
    //15-04-15, for type-instance indexing
    private void indexBufferedString2(IndexWriter iw, String string) throws IOException {
    	final OntModel model = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM);
        
        try {
            model.read(new ByteArrayInputStream(string.getBytes()), null, "N-TRIPLE");
        } catch (RiotException e) {
            try (final BufferedReader sr = new BufferedReader(new StringReader(string))) {
                final String line1 = sr.readLine();
                String line = sr.readLine();
                if (line != null) {
                    indexBufferedString2(iw, line1);
                    do {
                        indexBufferedString2(iw, line);
                    } while ((line = sr.readLine()) != null);
                } else {
                    logger.warn("Invalid triple: " + string);
                }
            }
        }                
        
        if (model.size() > 0) {
            for (Iterator<Statement> it = model.listStatements(); it.hasNext(); ) {
                final Statement stmt = it.next();                
                String resource = stmt.getSubject().getURI();                 
                if(this.search1Cat(resource, 1).length!=0&&this.search1Sa(resource, 1).length!=0) {                	
                	String clURI = stmt.getObject().asResource().getURI().toString(); 
                	if(clURI.matches("http://dbpedia.org/ontology/.*")) {
                		final Document doc = new Document();
                    	doc.add(new StringField("subject", clURI, Field.Store.NO));
    	                doc.add(new StoredField("object", stmt.getSubject().getURI()));
    	                iw.addDocument(doc);
                	}
                	else {
                		if(this.wikiToDBclasses.containsKey(clURI)) {
	                		for(String s : this.wikiToDBclasses.get(clURI)) {
		                			final Document doc = new Document();
		    	                	doc.add(new StringField("subject", s, Field.Store.NO));
		    		                doc.add(new StoredField("object", stmt.getSubject().getURI()));
		    		                iw.addDocument(doc);
	                		}
                		}
                	}
            	}                                                
            }
        }
        
    }
    
    public void index1Sa(InputStream[] inputStreams) throws IOException {
        try (final IndexWriter iw = new IndexWriter(directory1Sa, new IndexWriterConfig(new KeywordAnalyzer()))) {
            for (final InputStream is : inputStreams) {
                try (
                        final BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                        final StringWriter stringWriter = new StringWriter();
                        final PrintWriter printWriter = new PrintWriter(stringWriter)
                ) {                    
                	String line;
                    int lineCounter = 0;
                    while ((line = br.readLine()) != null) {
                        printWriter.println(line);
                        if (++lineCounter % 1000 == 0) {
                            indexBufferedString1Sa(iw, stringWriter.toString());
                            stringWriter.getBuffer().setLength(0);
                        }
                    }
                    indexBufferedString1Sa(iw, stringWriter.toString());
                }
            }
            iw.forceMerge(1);
        }
    }
    
    public void index1Cat(InputStream[] inputStreams) throws IOException {
        try (final IndexWriter iw = new IndexWriter(directory1Cat, new IndexWriterConfig(new KeywordAnalyzer()))) {
            for (final InputStream is : inputStreams) {
                try (
                        final BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                        final StringWriter stringWriter = new StringWriter();
                        final PrintWriter printWriter = new PrintWriter(stringWriter)
                ) {                    
                	String line;
                    int lineCounter = 0;
                    while ((line = br.readLine()) != null) {
                        printWriter.println(line);
                        if (++lineCounter % 1000 == 0) {
                            indexBufferedString1Cat(iw, stringWriter.toString());
                            stringWriter.getBuffer().setLength(0);
                        }
                    }
                    indexBufferedString1Cat(iw, stringWriter.toString());
                }
            }
            iw.forceMerge(1);
        }
    }
    
    //15-04-15, for type-instance indexing
    public void index2(InputStream[] inputStreams) throws IOException {
        try (final IndexWriter iw = new IndexWriter(directory2, new IndexWriterConfig(new KeywordAnalyzer()))) {
        	this.initSearcher1Sa();
        	this.initSearcher1Cat();
        	
            for (final InputStream is : inputStreams) {
                try (
                        final BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                        final StringWriter stringWriter = new StringWriter();
                        final PrintWriter printWriter = new PrintWriter(stringWriter)
                ) {
                	String line;
                    int lineCounter = 0;
                    while ((line = br.readLine()) != null) {
                        printWriter.println(line);
                        if (++lineCounter % 1000 == 0) {
                            indexBufferedString2(iw, stringWriter.toString());
                            stringWriter.getBuffer().setLength(0);
                        }
                    }
                    indexBufferedString2(iw, stringWriter.toString());
                }
            }
            iw.forceMerge(1);
        }
    }
    
    public void initSearcher1Sa() {
    	try {
    		this.dr1Sa = DirectoryReader.open(directory1Sa);
    		this.is1Sa = new IndexSearcher(dr1Sa);
    	}
    	catch (IOException e) {
    		e.printStackTrace();;
    	}
    }
    
    public void initSearcher1Cat() {
    	try {
    		this.dr1Cat = DirectoryReader.open(directory1Cat);
    		this.is1Cat = new IndexSearcher(dr1Cat);
    	}
    	catch (IOException e) {
    		e.printStackTrace();;
    	}
    }
    
    public void initSearcher2() {
    	try {
    		this.dr2 = DirectoryReader.open(directory2);
    		this.is2 = new IndexSearcher(dr2);
    	}
    	catch (IOException e) {
    		e.printStackTrace();;
    	}
    }
    
    public void closeSearcher1Sa() {
    	try {
    		this.dr1Sa.close();
    	}
    	catch(Exception e) {
    		e.printStackTrace();
    	}
    }
    
    public void closeSearcher1Cat() {
    	try {
    		this.dr1Cat.close();
    	}
    	catch(Exception e) {
    		e.printStackTrace();
    	}
    }
    
    public void closeSearcher2() {
    	try {
    		this.dr2.close();
    	}
    	catch(Exception e) {
    		e.printStackTrace();
    	}
    }
    
    public Tuple[] search1Sa(String resource, int top_n) {
    	try {
    		final ScoreDoc[] hits = this.is1Sa.search(new TermQuery(new Term("subject", resource)), top_n).scoreDocs;
            final Tuple[] result = new Tuple[hits.length];
            for (int i = 0; i < hits.length; i++) {
                Document hitDoc = this.is1Sa.doc(hits[i].doc);
                result[i] = new Tuple(resource, hitDoc.get("object"));
            }
            return result;
        } 
    	catch (IOException e) {
            return new Tuple[]{};
        }
    }
    
    public Tuple[] search1Cat(String resource, int top_n) {
    	try {
    		final ScoreDoc[] hits = this.is1Cat.search(new TermQuery(new Term("subject", resource)), top_n).scoreDocs;
            final Tuple[] result = new Tuple[hits.length];
            for (int i = 0; i < hits.length; i++) {
                Document hitDoc = this.is1Cat.doc(hits[i].doc);
                result[i] = new Tuple(resource, hitDoc.get("object"));
            }
            return result;
        } 
    	catch (IOException e) {
            return new Tuple[]{};
        }
    }
    
    public Tuple[] search2(String resource, int top_n) {
    	try {
    		final ScoreDoc[] hits = this.is2.search(new TermQuery(new Term("subject", resource)), top_n).scoreDocs;
            final Tuple[] result = new Tuple[hits.length];
            for (int i = 0; i < hits.length; i++) {
                Document hitDoc = this.is2.doc(hits[i].doc);
                result[i] = new Tuple(resource, hitDoc.get("object"));
            }
            return result;
        } 
    	catch (IOException e) {
            return new Tuple[]{};
        }
    }
    
    public class Triple {

        final public String subject;
        final public String predicate;
        final public String object;

        public Triple(String subject, String predicate, String object) {
            this.subject = subject;
            this.predicate = predicate;
            this.object = object;
        }

    }
    
    public class Tuple {

        final public String subject;        
        final public String object;

        public Tuple(String subject, String object) {
            this.subject = subject;            
            this.object = object;
        }

    }
    
}
