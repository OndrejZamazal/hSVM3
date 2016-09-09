package cz.vse.swoe.linkedtv.hsvm;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.StringTokenizer;
import java.util.Vector;

import libsvm.svm;
import libsvm.svm_model;
import libsvm.svm_node;
import libsvm.svm_parameter;
import libsvm.svm_problem;

/**
 * Created by svabo on 2016.
 */

class ProbClass2 {
	double prob = 0;
	double nprob1 = 0; //normalized probability to interval (-1,1)
	double nprob2 = 0; //normalized probability to interval (0,1)
	String target_class = "";
	
	public ProbClass2(String c, double p, double nprob1, double nprob2) {
		this.target_class=c;
		this.prob=p;			
		this.nprob1=nprob1;
		this.nprob2=nprob2;
	}
	
	public ProbClass2(String c, double p, double nprob1) {
		this.target_class=c;
		this.prob=p;			
		this.nprob1=nprob1;
	}
	
	public String toString() {
		//return target_class+":"+prob;
		//return target_class+":"+prob+":"+nprob1+":"+nprob2;
		return target_class+":"+prob+":"+nprob1;
	}
}

public class LibSVMClassifier {
	
	public ArrayList<ProbClass2> classified_classes = null;
	public HashMap<String,Double> types_to_prob = null;
	protected HashMap<String, ArrayList<String>> class_names = null;
	protected HashMap<String, Double> class_aprob = null;	
	
	public LibSVMClassifier(String input_file) {
		try {
			int all_instances = 0;
			this.class_names = new HashMap<String, ArrayList<String>>();
			this.class_aprob = new HashMap<String, Double>();
			//e.g. input_file="svmModelsSubclasses1000.txt";
			BufferedReader fp = new BufferedReader(new FileReader("res/"+input_file));
			
			while(true)
			{
				String line = fp.readLine();
				if(line == null) break;
				if(line.matches("@instances.*")) {
					all_instances = new Integer(line.substring(line.indexOf(":")+1)).intValue();
					continue;
				}
				if(line.matches("#.*")) continue;
				
				String supertype = line.substring(0, line.indexOf("-"));
				if(!supertype.equals("Thing")) {
					String s = line.substring(line.indexOf("-")+1,line.lastIndexOf(":"));
					int ins = new Integer(s.substring(0, s.indexOf("-"))).intValue();
					this.class_aprob.put(supertype, new Double((double)ins/all_instances));
				}
				StringTokenizer st = new StringTokenizer(line.substring(line.indexOf(":")+1).replaceAll("\\[", "").replaceAll("\\]", ""),",");
				ArrayList<String> subtypes = new ArrayList<String>();
				while(st.hasMoreTokens()) {
					subtypes.add(st.nextToken().trim());
				}
				this.class_names.put(supertype, subtypes);
			}
			fp.close();
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	//code taken from svm_train.java from LibSVM library
	private static double atof(String s)
	{
		double d = Double.valueOf(s).doubleValue();
		if (Double.isNaN(d) || Double.isInfinite(d))
		{
			System.err.print("NaN or Infinity in input\n");
			System.exit(1);
		}
		return(d);
	}
	
	//code taken from svm_train.java from LibSVM library
	private static int atoi(String s)
	{
		return Integer.parseInt(s);
	}
	
	// read in a problem (in svmlight format) code taken from svm_train.java from LibSVM library
	//private svm_problem read_problem(String input_file_name, svm_parameter param) throws IOException
	private svm_problem read_problem(File input_file, svm_parameter param) throws IOException
	{
		svm_problem prob;
		
		BufferedReader fp = new BufferedReader(new FileReader(input_file));
		Vector<Double> vy = new Vector<Double>();
		Vector<svm_node[]> vx = new Vector<svm_node[]>();
		int max_index = 0;	

		while(true)
		{
			String line = fp.readLine();
			if(line == null) break;

			StringTokenizer st = new StringTokenizer(line," \t\n\r\f:");

			vy.addElement(atof(st.nextToken()));
			int m = st.countTokens()/2;
			svm_node[] x = new svm_node[m];
			for(int j=0;j<m;j++)
			{
				x[j] = new svm_node();
				x[j].index = atoi(st.nextToken());
				x[j].value = atof(st.nextToken());
			}
			if(m>0) max_index = Math.max(max_index, x[m-1].index);
			vx.addElement(x);
		}

		prob = new svm_problem();
		prob.l = vy.size();
		prob.x = new svm_node[prob.l][];
		for(int i=0;i<prob.l;i++)
			prob.x[i] = vx.elementAt(i);
		prob.y = new double[prob.l];
		for(int i=0;i<prob.l;i++)
			prob.y[i] = vy.elementAt(i);

		if(param.gamma == 0 && max_index > 0)
			param.gamma = 1.0/max_index;

		if(param.kernel_type == svm_parameter.PRECOMPUTED)
			for(int i=0;i<prob.l;i++)
			{
				if (prob.x[i][0].index != 0)
				{
					System.err.print("Wrong kernel matrix: first column must be 0:sample_serial_number\n");
					System.exit(1);
				}
				if ((int)prob.x[i][0].value <= 0 || (int)prob.x[i][0].value > max_index)
				{
					System.err.print("Wrong input format: sample_serial_number out of range\n");
					System.exit(1);
				}
			}

		fp.close();
		return prob;
	}
	
	//according to svm_train.java code from LibSVM distribution
	public void train_model() {
		try {
			svm_model model;
			svm_parameter param;
			svm_problem prob;
			String model_file_name;
			
			//parameters:
			param = new svm_parameter();
			// default values
			param.svm_type = svm_parameter.C_SVC;
			param.kernel_type = svm_parameter.LINEAR;
			param.degree = 3;
			param.gamma = 0;	// 1/num_features
			param.coef0 = 0;
			param.nu = 0.5;
			param.cache_size = 100;
			param.C = 1;
			param.eps = 1e-3;
			param.p = 0.1;
			param.shrinking = 1;
			param.probability = 1; //prepinac b 1
			param.nr_weight = 0;
			param.weight_label = new int[0];
			param.weight = new double[0];
			//end of parameters
			
			File file = new File("res/LibSVM-unique/.");
			String files[] = file.list();
			for (int i=0; i<files.length; i++) {
				System.out.println(files[i]);
				File f = new File("res/LibSVM-unique/"+files[i]);
				prob = this.read_problem(f,param);
				model = svm.svm_train(prob,param);
				model_file_name="res/LibSVM-model/"+files[i].replaceAll("\\.train", "")+".model";
				svm.svm_save_model(model_file_name,model);
			}
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	//according to svm_predict.java code from LibSVM distribution
	//take instance as string
	public boolean predict(String model_name, String instance, svm_model model, String smoothing) {
		try {
			classified_classes = new ArrayList<ProbClass2>();
			types_to_prob = new HashMap<String,Double>();
			double[] probDistrOverClasses = null;
			int nr_class=svm.svm_get_nr_class(model);
			probDistrOverClasses = new double[nr_class];
			
			//read instance from String
			StringTokenizer st = new StringTokenizer(instance," \t\n\r\f:");
			int m = st.countTokens()/2;
			svm_node[] x = new svm_node[m];
			for(int j=0;j<m;j++)
			{
				x[j] = new svm_node();
				x[j].index = atoi(st.nextToken());
				x[j].value = atof(st.nextToken());
			}

			svm.svm_predict_probability(model,x,probDistrOverClasses);			
			for(int i=0;i<probDistrOverClasses.length;i++) {
				double prob = probDistrOverClasses[i];
				double nprob1=0.0;
				if (smoothing.equals("target_types_number")) {
					nprob1=(prob-(1/nr_class)) / (1-(1/(double)nr_class));
				}
				else if(smoothing.equals("apriori_probability")) {
					if(!model_name.equals("Thing"))
						nprob1=prob*this.class_aprob.get(model_name);
				}
				else if(smoothing.equals("probability")) {					
					nprob1=prob;
				}
				classified_classes.add(new ProbClass2(this.class_names.get(model_name).get(i), probDistrOverClasses[i], nprob1));
				types_to_prob.put(this.class_names.get(model_name).get(i), nprob1);
			}
			return true;
		}
		catch(Exception e) {
			e.printStackTrace();
			return false;
		}
	}
	
}
