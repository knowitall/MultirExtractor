package edu.washington.multir.eval;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.Triple;
import edu.washington.multir.argumentidentification.ArgumentIdentification;
import edu.washington.multir.argumentidentification.NELArgumentIdentification;
import edu.washington.multir.argumentidentification.NERArgumentIdentification;
import edu.washington.multir.argumentidentification.NERSententialInstanceGeneration;
import edu.washington.multir.argumentidentification.SententialInstanceGeneration;
import edu.washington.multir.data.Argument;
import edu.washington.multir.featuregeneration.DefaultFeatureGenerator;
import edu.washington.multir.featuregeneration.FeatureGenerator;
import edu.washington.multir.multiralgorithm.Mappings;
import edu.washington.multir.preprocess.CorpusPreprocessing;
import edu.washington.multir.sententialextraction.DocumentExtractor;
import edu.washington.multir.util.CLIUtils;

public class SententialEvaluation {
	
	public static Pattern argument1Pattern = Pattern.compile("\\[([^\\[\\]]+)\\]1");
	public static Pattern argument2Pattern = Pattern.compile("\\[([^\\[\\]]+)\\]2");
	
	
	private static Pattern punctPattern = Pattern.compile("\\S(\\s)[\\.!,?:;]");
	private static Pattern encloseQuotesPattern = Pattern.compile("(?:''|\\\")(\\s)[^(?:'')(?:\\\")\\s]+(?:\\s[^(?:'')(?:\\\")\\s]+)*(\\s)(?:''|\\\")");
	private static Pattern moveQuotesLeftPattern = Pattern.compile(".(\\s)(?:''|\\\")(?!\\S)");
	private static Pattern moveApostropheLeftPattern = Pattern.compile(".(\\s)'(?!')");

	private static Set<String> validRelations;
	
	private static DocumentExtractor de;
	
	private static List<String> cjParses;
	
	private static Map<Integer,String> ftID2ft;
	
	
	public static void main(String[] args) throws IOException, InterruptedException, ParseException, ClassNotFoundException, NoSuchMethodException, SecurityException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, InstantiationException{
		
		List<String> arguments = new ArrayList<String>();
		for(String arg: args){
			arguments.add(arg);
		}

		ArgumentIdentification ai = CLIUtils.loadArgumentIdentification(arguments);
		SententialInstanceGeneration sig = CLIUtils.loadSententialInformationGeneration(arguments);
		FeatureGenerator fg = CLIUtils.loadFeatureGenerator(arguments);
		
		//initialize cjParses
		cjParses = new ArrayList<>();
		loadCjParses(arguments.get(3));
		
		//read in relations from mapping file
		validRelations = new HashSet<String>();
		loadRelations(arguments.get(0));
		
		
		//load in annotations
		List<Label> annotations;
		String annotationFilePath = arguments.get(1);
		annotations = loadAnnotations(annotationFilePath);
		
		//get extractions
		de = new DocumentExtractor(arguments.get(2),fg, ai, sig);
		List<Extraction> extractions;
		extractions = extract(annotations);
		

		
		
		
		score(annotations,extractions);
		
		System.out.println("Number of annotations is " + annotations.size());
		
		
	}
	
	


	private static void loadCjParses(String cjParseFile) throws IOException {
		BufferedReader br = new BufferedReader(new FileReader(new File(cjParseFile)));
		String  nextLine;
		
		while((nextLine = br.readLine())!=null){
			cjParses.add(nextLine);
		}
		
		
		br.close();
	}




	private static void writeCJParses(List<Label> annotations,
			String outFileName) throws IOException {
		
		List<Pair<Integer,Annotation>> annotatedSentences = new ArrayList<>();
		for(Label l : annotations){
			Integer ID = l.ID;
			Annotation sen = CorpusPreprocessing.preParseProcess(l.sentence);
			Pair<Integer,Annotation> p = new Pair<>(ID,sen);
			annotatedSentences.add(p);
		}
		
		//serialize cj parses
		
		StringBuilder cjInput = new StringBuilder();
		Integer lastIndex = annotations.get(annotations.size()-1).ID;
		int annotatedSentencesIndex = 0;
		for(int i =0; i <= lastIndex; i++){
			
			Pair<Integer,Annotation> p = annotatedSentences.get(annotatedSentencesIndex);
			if(i == p.first){
				StringBuilder tokenStringBuilder = new StringBuilder();
				CoreMap sentence = p.second.get(CoreAnnotations.SentencesAnnotation.class).get(0);
				List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
				for(CoreLabel tok : tokens){
					tokenStringBuilder.append(tok.word());
					tokenStringBuilder.append(" ");
				}
				cjInput.append("<s> ");
				cjInput.append(tokenStringBuilder.toString().trim());
				cjInput.append(" </s>");
				cjInput.append("\n\n");
				annotatedSentencesIndex++;
			}
			
			//void sentence..
			else{
				cjInput.append("<s> ");
				cjInput.append("XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX </s>");
				cjInput.append("\n\n");
			}
		}
		
		BufferedWriter bw = new BufferedWriter(new FileWriter(new File(outFileName)));
		bw.write(cjInput.toString().trim());
		bw.close();
		
	}




	private static List<Extraction> extract(List<Label> annotations) throws IOException, InterruptedException {
		List<Extraction> extractions = new ArrayList<>();
		
		
		for(Label a : annotations){
			Extraction e= getExtraction(a);
			if( e != null){
				extractions.add(e);
			}
		}
		return extractions;
	}




	private static Extraction getExtraction(Label a) throws IOException, InterruptedException {
		
		
		
		//Annotation doc = CorpusPreprocessing.getTestDocumentFromRawString(a.sentence, "doc"+String.valueOf(a.ID));
		Annotation doc = CorpusPreprocessing.preParseProcess(a.sentence);
		String cjParse = cjParses.get(a.ID);
		CoreMap sentence = doc.get(CoreAnnotations.SentencesAnnotation.class).get(0);
		CorpusPreprocessing.postParseProcessSentence(sentence,cjParse);
		List<CoreMap> sentences = doc.get(CoreAnnotations.SentencesAnnotation.class);
		Argument arg1 = new Argument(a.r.arg1.getArgName(),a.r.arg1.getStartOffset(),a.r.arg1.getEndOffset());
		Argument arg2 = new Argument(a.r.arg2.getArgName(),a.r.arg2.getStartOffset(),a.r.arg2.getEndOffset());
		Pair<Triple<String,Double,Double>,Map<Integer,Double>> result = de.extractFromSententialInstanceWithFeatureScores(arg1, arg2, sentences.get(0), doc);
		
		
		if(result == null){
			return null;
		}
		
		//convert to extraction
		else{
			Extraction e = new Extraction();
			Triple<String,Double,Double> trip = result.first;
			Map<Integer,Double> featureScoreMap = result.second;
			e.sentence = a.sentence;
			e.ID = a.ID;
			e.conf = trip.second;
			e.score = trip.third;
			
			Relation r = new Relation();
			r.rel = trip.first;
			r.arg1 = arg1;
			r.arg2 = arg2;
			
			e.r = r;
			e.featureScores = featureScoreMap;
			
			if(!a.r.rel.equals(r.rel)) System.out.println(e.ID +"\t" + r.rel + "\t" + r.arg1.getArgName() + "\t" + r.arg2.getArgName());
			return e;
		}
	}




	private static void score(List<Label> annotations,
			List<Extraction> extractions) {
		
		//sort extractions and print in order
		Collections.sort(extractions,new ExtractionScoreComparable());
		
		for(int i =1; i < extractions.size(); i++){
			Pair<Double,Double> pr = getPR(new ArrayList<Extraction>(extractions.subList(0, i)),annotations,false);
			System.out.println(pr.first + "\t" + pr.second + "\t" + fscore(pr.first,pr.second));
		}
		Pair<Double,Double> finalPr = getPR(extractions,annotations,true);
		System.out.println(finalPr.first + "\t" + finalPr.second + "\t" + fscore(finalPr.first,finalPr.second));		
	}


	private static double fscore(double precision, double recall){
		
		double numerator = 2 * recall * precision;
		double denom = recall + precision;
		
		return (denom == 0.0)? 0.0 : (numerator/denom);
	}


	private static Pair<Double, Double> getPR(List<Extraction> extractions,
			List<Label> annotations, boolean print) {
		
		
		int numberOfTotalCorrectLabels =0;
		int numberOfTotalCorrectExtractions = 0;
		int totalExtractions =0;
		
		for(Label l : annotations){
			if(l.b){
				numberOfTotalCorrectLabels++;
			}
		}
		
		for(Extraction e: extractions){
			Relation r = e.r;
			//find Label
			Label matchingLabel = null;
			for(Label l : annotations){
				if(e.ID.equals(l.ID)){
					matchingLabel = l;
					break;
				}
			}
			
			//if there should be an extractoin
			if(matchingLabel.b){
				//if its the right extraction
				if(e.r.rel.equals(matchingLabel.r.rel)){
					numberOfTotalCorrectExtractions++;
					totalExtractions++;
					if(print) {
						System.out.print(r.arg1.getArgName()+ "\t" + r.arg2.getArgName() + "\t" + r.rel  +"\t" + e.score + "\t" + "CORRECT\n");
						//System.out.println(e.printFeatureScores());
					}
				}
			}
			else{
				if(e.r.rel.equals(matchingLabel.r.rel)){
					totalExtractions++;
					if(print) {
						System.out.print(r.arg1.getArgName()+ "\t" + r.arg2.getArgName() + "\t" + r.rel  +"\t" + e.score +"\t" + "INCORRECT\n");
						//System.out.println(e.printFeatureScores());
					}
				}
			}
			
//			totalExtractions++;
		}
		
		
		double recall = (numberOfTotalCorrectLabels == 0)? 0.0 : (((double)numberOfTotalCorrectExtractions) / ((double)numberOfTotalCorrectLabels));
		double precision = (totalExtractions == 0) ? 1.0 : (((double)numberOfTotalCorrectExtractions) / ((double)totalExtractions));
		return new Pair<Double,Double>(precision,recall);
	}




	private static void loadRelations(String mappingFile) throws IOException {
		BufferedReader br = new BufferedReader(new FileReader( new File(mappingFile)));
		String firstLine;
		firstLine = br.readLine();
		Integer numRelations = Integer.parseInt(firstLine);
		
		int i =0;
		String nextLine;
		while(i < numRelations){
			nextLine = br.readLine();
			String[] lineValues = nextLine.split("\t");
			String rel = lineValues[1];
			validRelations.add(rel);
			i++;
		}
		br.close();
	}




	private static class Label{
		Relation r;
		boolean b;
		String sentence;
		Integer ID;
	}
	
	private static class Relation{
		String rel;
		Argument arg1;
		Argument arg2;
	}
	
	private static class Extraction{
		Relation r;
		String sentence;
		Integer ID;
		double conf;
		double score;
		Map<Integer,Double> featureScores;
		private List<Pair<String,Double>> featureScoreList = null;
		
		
		public String printFeatureScores(){
			if(featureScoreList == null){
				featureScoreList = new ArrayList<>();
				for(Integer i : featureScores.keySet()){
					String f = ftID2ft.get(i);
					Double score = featureScores.get(i);
					Pair<String,Double> p = new Pair<>(f,score);
					featureScoreList.add(p);
				}
				//sort by scores
				Collections.sort(featureScoreList, new Comparator<Pair<String,Double>>(){

					@Override
					public int compare(Pair<String, Double> arg0,
							Pair<String, Double> arg1) {
						return arg0.second.compareTo(arg1.second);
					}
					
				});
				Collections.reverse(featureScoreList);
			}
			StringBuilder sb = new StringBuilder();
			sb.append("Features in order of score:\n");
			for(Pair<String,Double> p : featureScoreList){
				sb.append(p.first + "\t" + p.second +"\n");
			}
			sb.setLength(sb.length()-1);
			return sb.toString();
		}
	}

	
	private static List<Label> loadAnnotations(String annotationFilePath) throws IOException {
		List<Label> annotations = new ArrayList<>();
		
		BufferedReader br = new BufferedReader(new FileReader( new File(annotationFilePath)));
		String nextLine;
		
		Integer id = 0;
		while((nextLine = br.readLine()) !=null){
			String[] lineValues = nextLine.split("\t");
			String relString = lineValues[3];
			String label = lineValues[4];
			String annoSentence = lineValues[5];
			String argument1String = lineValues[6];
			String argument2String = lineValues[7];
			String tokenizedSentence = lineValues[8];
			
			
			//step 1: identify what occurrence of the string the arguments are.
			Integer argument1OccurrenceNumber = getArgumentOccurrence(annoSentence,argument1Pattern);
			Integer argument2OccurrenceNumber = getArgumentOccurrence(annoSentence,argument2Pattern);
//			System.out.println(argument1OccurrenceNumber);
//			System.out.println(argument2OccurrenceNumber);
			
			
			//step 2: convert tokenized sentence to raw sentence
			String rawSentence = convertTokenizedSentence(tokenizedSentence);
			
			
			//step 3: identify offsets of each argument
			Pair<Integer,Integer> argument1Offsets = getOffsetsOfArgument(argument1String,argument1OccurrenceNumber,rawSentence);
			Pair<Integer,Integer> argument2Offsets = getOffsetsOfArgument(argument2String,argument2OccurrenceNumber,rawSentence);
			
			
			if(argument1Offsets!=null && argument2Offsets!=null){
				
				//create Annotation object and store it
				Label a = new Label();
				a.ID = id;
				a.b = (label.equals("y") || label.equals("indirect"))? true : false;
				a.sentence = rawSentence;
				Relation r = new Relation();
				r.rel = relString;
				r.arg1 = new Argument(argument1String,argument1Offsets.first,argument1Offsets.second);
				r.arg2 = new Argument(argument2String,argument2Offsets.first,argument2Offsets.second);
				a.r =r;
				
				
				//if OccurrenceNumbers are negative don't had to annotations set.. or if relatin wasn't trained on..
				if(isValidAnnotation(a)){
					annotations.add(a);
				}
			}
			
			id++;
		}
		
		br.close();
		
		return annotations;
	}


	public static String convertTokenizedSentence(String tokenizedSentence) {
	//	System.out.println("CONVERTING TOKENIZED STRING");
		try{
			String rawSentence = tokenizedSentence;
			if(tokenizedSentence.startsWith("\"") && tokenizedSentence.endsWith("\"")){
				rawSentence = tokenizedSentence.substring(1, tokenizedSentence.length()-1);				
			}
			rawSentence = applyReplacementPattern(rawSentence,punctPattern);
			rawSentence = applyReplacementPattern(rawSentence,encloseQuotesPattern);
			rawSentence = applyReplacementPattern(rawSentence,moveQuotesLeftPattern);
			rawSentence = applyReplacementPattern(rawSentence,moveApostropheLeftPattern);
			rawSentence = rawSentence.replaceAll("-LRB-\\s", "(");
			rawSentence = rawSentence.replaceAll("\\s-RRB-", ")");
			
			
//			System.out.println(tokenizedSentence);
//			System.out.println(rawSentence);
			return rawSentence;
		}
		catch(Exception e){
			System.err.println(e);
			return null;
		}
	}




	private static String applyReplacementPattern(String rawSentence,Pattern pattern) {

		Matcher m = pattern.matcher(rawSentence);
		if(m.find()){
			do{
				//change string and change matcher to point to new string
				int cumulativeLengthDifference =0;
				for(int i =1; i <= m.groupCount(); i++){
					int removalStart = m.start(i);
					int removalEnd = m.end(i);
					rawSentence = rawSentence.substring(0, removalStart-cumulativeLengthDifference) + rawSentence.substring(removalEnd-cumulativeLengthDifference);
					cumulativeLengthDifference += (removalEnd-removalStart);
				}
				m = pattern.matcher(rawSentence);
			}
			while(m.find());
		}
		return rawSentence;
	}




	private static boolean isValidAnnotation(Label a) {
		
		if(!validRelations.contains(a.r.rel)){
			return false;
		}
		if(a.sentence == null){
			return false;
		}
		
		//not labeled properly in annotations file
		if(a.r.rel.equals("/location/administrative_division/country")){
			return false;
		}
//		if(a.r.rel.contains("/location/")){
//			return false;
//		}
		
		return true;
	}


	public static Integer getArgumentOccurrence(String annoSentence, Pattern argPattern) {
//		System.out.println(annoSentence);
		Matcher m = argPattern.matcher(annoSentence);
		if(m.find()){
			String word = m.group(1);
			Integer startOffset = m.start(1);
			
//			System.out.println(word);
//			System.out.println(startOffset);
			//iterate over occurrence of word in sentence to find ith occurrence that matches the pattern
	        Pattern wordPattern = Pattern.compile(word);
	        Matcher wordPatternMatcher = wordPattern.matcher(annoSentence);
	        
	        int i =1;
	        while(wordPatternMatcher.find()){
	        	Integer nextWordStartOffset = wordPatternMatcher.start();
//	        	System.out.println(nextWordStartOffset);
	        	if(nextWordStartOffset.equals(startOffset)){
	        		break;
	        	}
	        	i++;
	        }
	        return i;
		}
		else{
			return -1;
		}
			
	}
	
	public static Pair<Integer,Integer> getOffsetsOfArgument(String argString, Integer occurenceNum, String rawSentence){
		Pattern p = Pattern.compile(argString);
		Matcher m = p.matcher(rawSentence);
		int i =0;
		while(m.find()){
			i++;
			if(i == occurenceNum){
				return new Pair<Integer,Integer>(m.start(),m.end());
			}
		}
		return null;
	}
	
	private static class ExtractionScoreComparable implements Comparator<Extraction>{

		@Override
		public int compare(Extraction arg0, Extraction arg1) {
			
			if(arg0.score > arg1.score){
				return -1;
			}
			else{
				return 1;
			}
		}
		
	}
	
	private static class ExtractionConfidenceComparable implements Comparator<Extraction>{

		@Override
		public int compare(Extraction arg0, Extraction arg1) {
			
			if(arg0.conf > arg1.conf){
				return -1;
			}
			else{
				return 1;
			}
		}
		
	}
}
