import io.TFReader;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.Vector;

import javax.swing.text.StyleContext.SmallAttributeSet;

import util.EmailDataset;
import util.EmailMessage;
import util.Pair;
import util.ThresholdTable;

/**
 * Class that represents a Naive Bayes Classifier
 * this class constructs classifiers from given TF 
 * files using the TFReader
 * 
 * 
 * @author davide
 * @author ainara
 *
 */
public class NaiveBayes {
	//token occurrency tables
	private EmailDataset trainData;
	private HashMap<Integer, Integer> spamOcurrTable; 
	private HashMap<Integer, Integer> hamOcurrTable;

	//token probability tables
	private HashMap<Integer, Double> spamProbTable; 
	private HashMap<Integer, Double> hamProbTable;

	private int threshold; //classification threshold

	private int dim;
	private double spamProb;
	private double hamProb;



	/**
	 * Constructor 
	 * 
	 * @param filename - String the file used to train the classifier
	 * @param sigTokens - Integer number of sinigicative tokens to be considered
	 * 
	 * @throws FileNotFoundException
	 */
	public NaiveBayes(String filename, Integer sigTokens) throws FileNotFoundException{
		//read and store the data
		TFReader reader = new TFReader(filename);
		EmailDataset data = reader.read();

		//calculate the threshold
		threshold= 8;//threashold(filename);


		initModel(data);
		//trainData=mostSigTokens(sigTokens,trainData);//filter most significative tokens 
	}
	
	public NaiveBayes(EmailDataset trainData, int threshold, int sigTokens) throws FileNotFoundException{
		this.threshold = threshold;
		initModel(trainData);
		//trainData=mostSigTokens(sigTokens,trainData);//filter most significative tokens 
	}

	/**
	 * Initializes this NaiveBayes instance with the train data supplied
	 * @param data EmailDataset - the data used to initialize and train the model
	 */
	private void initModel(EmailDataset data){
		trainData = data;
		dim=trainData.getDictionaryDim();

		//get and init token dictionary tables
		Pair<HashMap<Integer, Integer>> pair = trainData.getTotalTokenOcurr();
		spamOcurrTable = pair.getFirst();
		hamOcurrTable = pair.getSecont();

		//init probability tables
		spamProbTable = new HashMap<Integer, Double>();
		hamProbTable = new HashMap<Integer, Double>();
		tableTokenProb(); //fills the two previous tables

		//init class prob
		spamProb = getClassProb("spam", trainData);
		hamProb = getClassProb("ham", trainData);

	}



	public void algoritmoEM(String filename1, String filename2)throws FileNotFoundException{
		TFReader rf1 = new TFReader(filename1);
		TFReader rf2 = new TFReader(filename2);

		//read 2 files
		EmailDataset dataset1 = rf1.read();
		EmailDataset dataset2 = rf2.read();



		//merge data
		EmailDataset datasetMerged = new EmailDataset();
		datasetMerged.merge(dataset1);
		datasetMerged.merge(dataset2);

		//save the current 2 datasets for later iteration
		EmailDataset tempDataset = datasetMerged.clone();
		EmailDataset tempTrain = trainData.clone();


		//classify 2 sets
		classifyAll(datasetMerged, threshold); 
		
		//merge to training data
		datasetMerged.merge(trainData);
		

		double previousLikehood= 0;
		double currentLikehood = 0;

		int i=0; 

		initModel(datasetMerged);
		do{
			previousLikehood = currentLikehood;


			//some feedback
			System.out.println("Modelo2:");
			System.out.println("nummsg: "+trainData.getNumMessages());
			System.out.println("numham:"+trainData.getNumHam());
			System.out.println("numspam:"+trainData.getNumSpam());


			datasetMerged = tempDataset.clone();
			//classify 
			classifyAll(datasetMerged, threshold);


			datasetMerged.merge(tempTrain);

			initModel(datasetMerged);//reset the model

			currentLikehood=getLikehood();

			System.out.println("diferença corrente: "+(currentLikehood - previousLikehood));
			i++;
		}while(i<5);














	}


	/**
	 * Method used to calculate the likelihood between classified 
	 * datasets with the expression:
	 * 
	 * <log( (pr(ti|c=1)*(pr(c=1)) + (pr(ti|c=-1)*(pr(c=-1))>
	 * 
	 * @return
	 */
	public double getLikehood(){
		double result=0;

		for(Integer token : spamProbTable.keySet()){
			result+= (spamProbTable.get(token) * spamProb) + 
			(hamProbTable.get(token) * hamProb);	
		}

		return Math.log(result);
	}


	//TODO rever isto
	/**
	 * Method that returns the EmailDataset filtered 
	 * considering only the most significative tokens
	 * 
	 * @param t threashold for # tokens
	 * @param trainData current taindata
	 * @return
	 */
	private void filterSignificativeTokens(int threshold){

		LinkedHashMap<Integer, Double> sortedTokens = new LinkedHashMap<Integer, Double>();
		List<Integer> significativeTokens = new LinkedList<Integer>();
		
		
		double result=0;
		for(Integer token: spamProbTable.keySet()){
			result = spamProbTable.get(token) / hamProbTable.get(token);
			sortedTokens.put(token, result);	
		}
		//order the current values
		sortedTokens= orderValues(sortedTokens);


		Iterator<Integer> it = sortedTokens.keySet().iterator();//key iterator
		int numTokens = 0;
		int currentToken = 0;

		if (sortedTokens.size() > threshold){
			while(numTokens < threshold){
				currentToken = it.next();
				significativeTokens.add(currentToken);
			}		
		}
		//create dataset with significative tokens
		
		EmailDataset finalData = new EmailDataset();
		EmailMessage currentMessage = null;
		
		for(EmailMessage m : trainData){
			currentMessage = m.clone();
			currentMessage.filter(significativeTokens);
			finalData.add(currentMessage);
			
		}
		trainData = finalData;

	}


/**
 *	Method used to create a hashmap of the expression 
 *	(P(ti | classe = +) / P(ti | classe = -))
 *	@param map 
 */
public static LinkedHashMap<Integer, Double> orderValues(LinkedHashMap<Integer, Double> map){ //de valor mais pequeno a mais grande
	LinkedHashMap<Integer, Double> newMap = new LinkedHashMap<Integer, Double>();

	ArrayList<Double> values = new ArrayList<Double>(map.values());
	Collections.sort(values);

	for(Double value : values){
		for(Integer token : map.keySet()){
			if(value == map.get(token)){
				newMap.put(token, value);
			}	
		}
	}
	return newMap;
}



/**
 * Method used to classify a dataset of messages received
 * 
 * @param data EmailDataset - the data to be classified
 * @param threshold Integer - the threshold of classification
 * 
 * The dataset passed is modified adding the classifications to the data
 */
private void classifyAll(EmailDataset data, int threshold){
	int predict = 0;		
	for(EmailMessage m:data){
		predict= classify(m, threshold);
		//System.out.println(classePredic);
		m.classify(predict);	
	}
}

private double getClassProb(String c, EmailDataset dados){
	double result = 0;
	if(c.equals("spam")){
		result=dados.getNumSpam() /dados.getNumSpam()+ dados.getNumHam();
	}
	else if(c.equals("ham")){
		result=dados.getNumHam() / dados.getNumHam() + dados.getNumSpam();
	}

	return result;
}


private double getTokenProb(int token, String c){

	HashMap<Integer, Integer> current = null;
	if(c.equals("spam")){
		current = spamOcurrTable;
	}if(c.equals("ham")){
		current = spamOcurrTable;
	}
	int ocurrToken = 0;
	if(current.containsKey(token))
		ocurrToken = current.get(token);
	int sumOcurrToken = 0;
	sumOcurrToken = allTokenOcurr(current);
	double result = (ocurrToken + 1) / sumOcurrToken + dim;

	return result;

}

private int allTokenOcurr(HashMap<Integer, Integer> classTable){
	int sumOcurrToken = 0;
	for(Integer key : classTable.keySet()){
		sumOcurrToken += classTable.get(key);	
	}
	return sumOcurrToken;
}

private void tableTokenProb(){
	for(Integer key : spamOcurrTable.keySet()){
		spamProbTable.put(key, getTokenProb(key, "spam"));
	}
	for(Integer key : hamOcurrTable.keySet()){
		hamProbTable.put(key, getTokenProb(key, "ham"));
	}
}


public int classify(EmailMessage m, double threshold){

	double result =  Math.log(spamProb / hamProb); 

	for(Integer token: m){
		if(spamProbTable.containsKey(token)){
			result += Math.log(spamProbTable.get(token) / hamProbTable.get(token));
		}else{
			result+=Math.log((getTokenProb(token, "spam")/getTokenProb(token, "ham")));
		}
	}

	int classification = 0;
	
	if(result > Math.log(threshold))
		classification = 1;
	else
		classification = -1;

	//System.out.println("Classification: "+ classification);

	return classification;
}

public static List<ThresholdTable> threashold(String filename)throws FileNotFoundException{
	List<ThresholdTable> v =new LinkedList<ThresholdTable>();
	
	//read file
	TFReader reader = new TFReader(filename);
	EmailDataset readData = reader.read();
	Pair<EmailDataset> pair = readData.split();
	
	EmailDataset train = pair.getFirst();
	EmailDataset validation = pair.getSecont();

	NaiveBayes model = new NaiveBayes(train, 4, 0);
	
	//clasificaçao de emails com diferentes thresholdes
	int predict;
	EmailMessage currentMessage = null;
	for (int t=0; t<20;t++){
		int fp=0;
		int fn=0;
		for(EmailMessage m: validation){
			currentMessage = m.clone();
			predict = model.classify(currentMessage, t);	//classify the message
			if ((predict == 1) && (m.getClassification() == -1)) //FP
				fp++;
			else if((predict == -1) && (m.getClassification() == 1)) //FN
				fn++;

		}
		v.add(new ThresholdTable(t,fp,fn)); 
	}

	
	return v;
}




}
