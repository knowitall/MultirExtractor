package edu.washington.multir.featuregeneration;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import edu.knowitall.tool.chunk.ChunkedToken;
import edu.knowitall.tool.chunk.OpenNlpChunker;
import edu.knowitall.tool.postag.PostaggedToken;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.Triple;
import edu.washington.multir.corpus.Corpus;
import edu.washington.multir.corpus.CorpusInformationSpecification;
import edu.washington.multir.corpus.DefaultCorpusInformationSpecification.TokenOffsetInformation.SentenceRelativeCharacterOffsetBeginAnnotation;
import edu.washington.multir.corpus.DefaultCorpusInformationSpecificationWithNELAndCoref;
import edu.washington.multir.corpus.DefaultCorpusInformationSpecification.TokenOffsetInformation.SentenceRelativeCharacterOffsetEndAnnotation;
import edu.washington.multir.featuregeneration.FeatureGeneration.SententialArgumentPair;
import edu.washington.multir.util.BufferedIOUtils;

public class GeneralizedFeatureGenerator020314 implements FeatureGenerator {

	private static final int WINDOW_SIZE = 2;
	private static final String MIDDLE_PREFIX = "m:";
	private static final String LEFT_PREFIX = "lw:";
	private static final String RIGHT_PREFIX = "rw:";
	private static final String INVERSE_TRUE_FEATURE = "INVERSE:true";
	private static final String INVERSE_FALSE_FEATURE = "INVERSE:false";
	private static final String GENERAL_FEATURE = "g:";
	private static final String BIGRAM_FEATURE = "b:";
	private static final String DISTANCE_FEATURE = "d:";
	private static final String TYPE_FEATURE = "t:";
	private static final String HEIGHT_FEATURE = "h:";
	
	private static List<String> defaultBigram;
	
	static{
		
		defaultBigram = new ArrayList<String>();
		defaultBigram.add("#PAD#");
		defaultBigram.add("#PAD#");
	}
	
	
	
	@Override
	public List<String> generateFeatures(Integer arg1StartOffset,
			Integer arg1EndOffset, Integer arg2StartOffset,
			Integer arg2EndOffset, String arg1ID, String arg2ID,
			CoreMap sentence, Annotation document) {
		
		List<Feature> features = new ArrayList<Feature>();		
		List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);

		
		
		Pair<Integer,Integer> leftArgOffsets = FeatureGeneratorMethods.getLeftArgOffsets(arg1StartOffset,arg1EndOffset,arg2StartOffset,arg2EndOffset);
		Pair<Integer,Integer> rightArgOffsets =FeatureGeneratorMethods.getRightArgOffsets(arg1StartOffset,arg1EndOffset,arg2StartOffset,arg2EndOffset);
		String inverseFeature = INVERSE_FALSE_FEATURE;
		if(arg1StartOffset > arg2StartOffset){
			inverseFeature = INVERSE_TRUE_FEATURE;
		}
		
		String typeFeature = TYPE_FEATURE + getTypeFeature(leftArgOffsets,rightArgOffsets,tokens);
		
		
		
		//get exact middle token sequnece
		List<CoreLabel> middleTokens = FeatureGeneratorMethods.getMiddleTokens(leftArgOffsets.second, rightArgOffsets.first, tokens);
		//List<Triple<CoreLabel,DependencyType,CoreLabel>> dependencyPathMiddleTokens = FeatureGeneratorMethods.getDependencyPath(leftArgOffsets.second,rightArgOffsets.second,sentence);
		List<CoreLabel> leftWindowTokens = FeatureGeneratorMethods.getLeftWindowTokens(leftArgOffsets.first, tokens, WINDOW_SIZE);
		List<CoreLabel> rightWindowTokens = FeatureGeneratorMethods.getRightWindowTokens(rightArgOffsets.second, tokens, WINDOW_SIZE);
		List<CoreLabel> leftTokens = FeatureGeneratorMethods.getLeftWindowTokens(leftArgOffsets.first, tokens);
		List<CoreLabel> rightTokens = FeatureGeneratorMethods.getRightWindowTokens(rightArgOffsets.second, tokens);

		
		// generalize tokens
		List<String> generalMiddleTokens = FeatureGeneratorMethods.getGeneralSequence(middleTokens);
		List<String> generalLeftTokens = FeatureGeneratorMethods.getGeneralSequence(leftTokens);
		List<String> generalRightTokens = FeatureGeneratorMethods.getGeneralSequence(rightTokens);


		features.add(new Feature(makeTokenFeature(middleTokens,inverseFeature,typeFeature,MIDDLE_PREFIX)));
		features.add(new Feature(makeWindowTokenFeature(leftWindowTokens,inverseFeature,LEFT_PREFIX,middleTokens)));
		features.add(new Feature(makeWindowTokenFeature(rightWindowTokens,inverseFeature,RIGHT_PREFIX,middleTokens)));		
		features.add(new Feature(makeSequenceFeature(generalMiddleTokens,inverseFeature,MIDDLE_PREFIX,GENERAL_FEATURE)));
		features.add(new Feature (makeSequenceFeature(generalLeftTokens,inverseFeature,LEFT_PREFIX,GENERAL_FEATURE)));
		features.add(new Feature(makeSequenceFeature(generalRightTokens,inverseFeature,RIGHT_PREFIX,GENERAL_FEATURE)));
		
		List<List<String>> generalMiddleBigrams = getMiddleBigrams(generalMiddleTokens,3);
		List<List<String>> generalLeftBigrams = getLeftBigrams(generalLeftTokens,2);
		List<List<String>> generalRightBigrams = getRightBigrams(generalRightTokens,2);
		
		for(List<String> bigrams : generalMiddleBigrams){
			features.add(new Feature(makeSequenceFeature(bigrams,inverseFeature,MIDDLE_PREFIX,GENERAL_FEATURE,BIGRAM_FEATURE)));
		}
		for(List<String> bigrams : generalLeftBigrams){
			String windowBigramFeature = makeSequenceFeature(bigrams,inverseFeature,LEFT_PREFIX,GENERAL_FEATURE,BIGRAM_FEATURE);
			String firstMiddleBigram = generalMiddleBigrams.get(0).get(1);
			features.add(new Feature(windowBigramFeature + " #ARG# " + firstMiddleBigram));
		}
		for(List<String> bigrams : generalRightBigrams){
			List<String> newBigrams = new ArrayList<String>();
			String lastMiddleBigram = generalMiddleBigrams.get(generalMiddleBigrams.size()-1).get(0);
			newBigrams.add(lastMiddleBigram);
			newBigrams.add("#ARG#");
			for(String bi : bigrams){
				newBigrams.add(bi);
			}
			String feature = makeSequenceFeature(newBigrams,inverseFeature,RIGHT_PREFIX,GENERAL_FEATURE,BIGRAM_FEATURE);
			features.add(new Feature(feature));
		}
		
		System.out.println("DEP PATH:");
		//System.out.println(getDependencyPathString(dependencyPathMiddleTokens));
		
		
//		String distanceFeatureSuffix = DISTANCE_FEATURE + " " + getDistanceFeature(middleTokens.size());
//		String typeFeatureSuffix = TYPE_FEATURE + " " + getTypeFeature(leftArgOffsets,rightArgOffsets,tokens);
		//String parseTreeHeightDifferenceSuffix = HEIGHT_FEATURE + " " + FeatureGeneratorMethods.getDependencyHeightDifference(leftArgOffsets,rightArgOffsets,sentence);
		
//		for(Feature f : features){
//			f.setFeature(f.toString()+" " + distanceFeatureSuffix + " " + typeFeatureSuffix + " " + parseTreeHeightDifferenceSuffix);
//		}
		
		
		
		List<String> featureStrings = new ArrayList<String>();
		
		for(Feature f: features){
			featureStrings.add(f.toString());
		}
		return featureStrings;
	}
	
	


	private String makeWindowTokenFeature(List<CoreLabel> windowTokens,
			String inverseFeature, String windowPrefix,
			List<CoreLabel> middleTokens) {
		
		StringBuilder featureBuilder = new StringBuilder();
		featureBuilder.append(inverseFeature);
		featureBuilder.append(" ");
		if(windowPrefix.equals(LEFT_PREFIX)){
			String tokenFeature = makeTokenFeature(windowTokens,windowPrefix);
			featureBuilder.append(tokenFeature);
			featureBuilder.append(" #ARG# ");
			for(int i =0; i < middleTokens.size(); i++){
				if(i == 2){
					break;
				}
				else{
					featureBuilder.append(" ");
					featureBuilder.append(middleTokens.get(i).get(CoreAnnotations.TextAnnotation.class));
				}
			}
		}
		else{
			featureBuilder.append(windowPrefix);
			for(int i =middleTokens.size()-1; i > -1; i--){
				if(i == (middleTokens.size()-3)){
					break;
				}
				else{
					featureBuilder.append(" ");
					featureBuilder.append(middleTokens.get(i).get(CoreAnnotations.TextAnnotation.class));
				}
			}
			featureBuilder.append(" #ARG# ");
			featureBuilder.append(makeTokenFeature(windowTokens));
			
		}
		return featureBuilder.toString();
	}


	private String getDistanceFeature(int size) {
		if(size == 0){
			return "0";
		}
		else if(  (size >0) && (size <6)){
			return "1-5";
		}
		else if( (size >5) && (size <11)){
			return "6-10";
		}
		else if( (size>10) && (size <16)){
			return "11-15";
		}
		else{
			return "15+";
		}
		
	}


	private String getTypeFeature(Pair<Integer, Integer> leftArgOffsets,
			Pair<Integer, Integer> rightArgOffsets, List<CoreLabel> tokens) {
		StringBuilder sb = new StringBuilder();
		String leftType = "O";
		String rightType = "O";
		
		for(CoreLabel t: tokens){
			Integer startOffset = t.get(SentenceRelativeCharacterOffsetBeginAnnotation.class);
			if(startOffset.equals(leftArgOffsets.first)){
				leftType = t.get(CoreAnnotations.NamedEntityTagAnnotation.class);
			}
			if(startOffset.equals(rightArgOffsets.first)){
				rightType = t.get(CoreAnnotations.NamedEntityTagAnnotation.class);
			}
		}
		
		sb.append(leftType);
		sb.append(" ");
		sb.append(rightType);
		return sb.toString().trim();
	}


	private List<List<String>> getMiddleBigrams(List<String> generalMiddleTokens, int numBigrams) {
		List<List<String>> bigrams = new ArrayList<List<String>>();
		
		int leftEnd = Math.min(generalMiddleTokens.size(), numBigrams+1);
		int rightStart = Math.max(0, generalMiddleTokens.size()-numBigrams-1);
		List<String> middleLeftTokens = generalMiddleTokens.subList(0, leftEnd);
		List<String> middleRightTokens = generalMiddleTokens.subList(rightStart,generalMiddleTokens.size());
		
		bigrams.addAll(getRightBigrams(middleLeftTokens,numBigrams));
		List<List<String>> middleRightBigrams = getLeftBigrams(middleRightTokens,numBigrams);
		
		for(List<String> bi: middleRightBigrams){
			if(!bigrams.contains(bi)){
				bigrams.add(bi);
			}
		}
		return bigrams;

	}
	
	private List<List<String>> getRightBigrams(List<String> generalTokens, int numBigrams){
		List<List<String>> bigrams = new ArrayList<List<String>>();
		
		int i =0;
		while((i<generalTokens.size()-1) && (bigrams.size()<numBigrams)){
			
			List<String> bigram = new ArrayList<String>();
			bigram.add(generalTokens.get(i));
			bigram.add(generalTokens.get(i+1));
			
			if(!bigram.equals(defaultBigram)){
				bigrams.add(bigram);
			}	
			i++;
		}
		return bigrams;
	}
	
	private List<List<String>> getLeftBigrams(List<String> tokens, int numBigrams){
		List<List<String>> bigrams = new ArrayList<List<String>>();
		
		int i = tokens.size()-1;
		while((i>0) && (bigrams.size()<numBigrams)){
			
			List<String> bigram = new ArrayList<String>();
			bigram.add(tokens.get(i-1));
			bigram.add(tokens.get(i));
			
			if(!bigram.equals(defaultBigram)){
				bigrams.add(bigram);
			}	
			i--;
		}
		return bigrams;
	}


	private String makeSequenceFeature(List<String> generalTokens,
			String ... prefixes) {
		StringBuilder featureBuilder = new StringBuilder();
		
		//add prefixes
		for(int i =0; i < prefixes.length; i++){
			featureBuilder.append(prefixes[i]);
			featureBuilder.append(" ");
		}
		
		for(String s : generalTokens){
			featureBuilder.append(s);
			featureBuilder.append(" ");
		}
		
		return featureBuilder.toString().trim();
	}
	
	private String makeTokenFeature(List<CoreLabel> tokens,
			String ... prefixes) {
		StringBuilder featureBuilder = new StringBuilder();
		
		//add prefixes
		for(int i =0; i < prefixes.length; i++){
			featureBuilder.append(prefixes[i]);
			featureBuilder.append(" ");
		}
		
		for(CoreLabel t : tokens){
			featureBuilder.append(t.get(CoreAnnotations.TextAnnotation.class));
			featureBuilder.append(" ");
		}
		
		return featureBuilder.toString().trim();
	}


	/**
	 * For testing generate Features
	 * @param args
	 * @throws SQLException 
	 * @throws IOException 
	 * @throws FileNotFoundException 
	 */
	public static void main(String[] args) throws SQLException, FileNotFoundException, IOException{		
		CorpusInformationSpecification cis = new DefaultCorpusInformationSpecificationWithNELAndCoref();
		FeatureGenerator fg = new GeneralizedFeatureGenerator020314();
		Corpus c = new Corpus("NELAndCorefTrain-Chunked",cis,true);
		BufferedReader in;
		in = BufferedIOUtils.getBufferedReader(new File("NELAndCorefTrain-NERDS"));

		String nextLine = in.readLine();
		int count =0;
		List<Integer> sentIds = new ArrayList<Integer>();
		List<SententialArgumentPair> saps = new ArrayList<SententialArgumentPair>();
		while(nextLine != null && count < 20){
			SententialArgumentPair sap = SententialArgumentPair.parseSAP(nextLine);
			sentIds.add(sap.getSentID());
			saps.add(sap);
			nextLine = in.readLine();
			count++;
		}
		
		Map<Integer, Pair<CoreMap,Annotation>> annotationMap = c.getAnnotationPairsForEachSentence(new HashSet<Integer>(sentIds));
		
		for(SententialArgumentPair sap: saps){
			Pair<CoreMap,Annotation> senAnnoPair = annotationMap.get(sap.getSentID());
			List<String> features = fg.generateFeatures(sap.getArg1Offsets().first,sap.getArg1Offsets().second
					,sap.getArg2Offsets().first,sap.getArg2Offsets().second,
					sap.getArg1Id(),sap.getArg2Id(),senAnnoPair.first,senAnnoPair.second);
			
			CoreMap sen = senAnnoPair.first;
			String senText = sen.get(CoreAnnotations.TextAnnotation.class);
			System.out.println(senText);
	        System.out.println("Arg1 :" + senText.substring(sap.getArg1Offsets().first,sap.getArg1Offsets().second));
	        System.out.println("Arg2 :" + senText.substring(sap.getArg2Offsets().first,sap.getArg2Offsets().second));
			System.out.println("----------------------------------------------------\nFeatures:");
			for(String f : features){
				System.out.println(f);
			}
			System.out.println("------------------------------------------------------\n\n");
		}
	}
	
	public static enum Direction{
		UP,DOWN
	}
	
	public static class DependencyType{
		
		private String type;
		private Direction d;
		
		public DependencyType(String type, Direction d){
			this.type = type;
			this.d =d;
		}
		
		public String getType(){
			return type;
		}
		
		public Direction getDirection(){
			return d;
		}
		
		@Override
		public String toString(){
			StringBuilder sb = new StringBuilder();
			switch(d){
			case UP:
				sb.append("->");
				break;
			case DOWN:
				sb.append("<-");
				break;
			default:
				throw new IllegalArgumentException("Bad Direction");
			}
			sb.insert(0,("("+type+")"));
			return sb.toString();
		}
	}
	
	private static String getDependencyPathString(List<Triple<CoreLabel,DependencyType,CoreLabel>> l){
		StringBuilder sb = new StringBuilder();
		
		for(Triple<CoreLabel,DependencyType,CoreLabel> t : l){
			sb.append(getDependencyTripleString(t));
		}
		
		return sb.toString().trim();
	}
	
	private static String getDependencyTripleString(Triple<CoreLabel,DependencyType,CoreLabel> triple){
		StringBuilder sb = new StringBuilder();
		
		sb.append(triple.first.get(CoreAnnotations.TextAnnotation.class));
		sb.append(triple.second.toString());
		sb.append(triple.third.get(CoreAnnotations.TextAnnotation.class));
		sb.append(" ");
		
		return sb.toString();
	}

}
