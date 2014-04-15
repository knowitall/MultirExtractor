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

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;
import edu.washington.multir.corpus.Corpus;
import edu.washington.multir.corpus.CorpusInformationSpecification;
import edu.washington.multir.corpus.CustomCorpusInformationSpecification;
import edu.washington.multir.corpus.DefaultCorpusInformationSpecification;
import edu.washington.multir.corpus.DocCorefInformation;
import edu.washington.multir.corpus.DocumentInformationI;
import edu.washington.multir.corpus.SentInformationI;
import edu.washington.multir.corpus.SentNamedEntityLinkingInformation;
import edu.washington.multir.corpus.DefaultCorpusInformationSpecification.TokenOffsetInformation.SentenceRelativeCharacterOffsetBeginAnnotation;
import edu.washington.multir.corpus.DefaultCorpusInformationSpecification.TokenOffsetInformation.SentenceRelativeCharacterOffsetEndAnnotation;
import edu.washington.multir.featuregeneration.FeatureGeneration.SententialArgumentPair;
import edu.washington.multir.util.BufferedIOUtils;
import edu.knowitall.tool.postag.PostaggedToken;
import edu.knowitall.tool.chunk.ChunkedToken;
import edu.knowitall.tool.chunk.OpenNlpChunker;



public class GeneralizedFeatureGenerator implements FeatureGenerator {

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
	private static OpenNlpChunker chunker = new OpenNlpChunker();
	
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
		
		List<String> features = new ArrayList<String>();
		
		
		List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);

		
		
		Pair<Integer,Integer> leftArgOffsets = FeatureGeneratorMethods.getLeftArgOffsets(arg1StartOffset,arg1EndOffset,arg2StartOffset,arg2EndOffset);
		Pair<Integer,Integer> rightArgOffsets =FeatureGeneratorMethods.getRightArgOffsets(arg1StartOffset,arg1EndOffset,arg2StartOffset,arg2EndOffset);
		String inverseFeature = INVERSE_FALSE_FEATURE;
		if(arg1StartOffset > arg2StartOffset){
			inverseFeature = INVERSE_TRUE_FEATURE;
		}
		
		
		
		//get exact middle token sequnece
		List<CoreLabel> middleTokens = FeatureGeneratorMethods.getMiddleTokens(leftArgOffsets.second, rightArgOffsets.first, tokens);
		List<CoreLabel> leftWindowTokens = FeatureGeneratorMethods.getLeftWindowTokens(leftArgOffsets.first, tokens, WINDOW_SIZE);
		List<CoreLabel> rightWindowTokens = FeatureGeneratorMethods.getRightWindowTokens(rightArgOffsets.second, tokens, WINDOW_SIZE);
		List<CoreLabel> leftTokens = FeatureGeneratorMethods.getLeftWindowTokens(leftArgOffsets.first, tokens);
		List<CoreLabel> rightTokens = FeatureGeneratorMethods.getRightWindowTokens(rightArgOffsets.second, tokens);

		
		// generalize tokens
		List<String> generalMiddleTokens = FeatureGeneratorMethods.getGeneralSequence(middleTokens);
		List<String> generalLeftTokens = FeatureGeneratorMethods.getGeneralSequence(leftTokens);
		List<String> generalRightTokens = FeatureGeneratorMethods.getGeneralSequence(rightTokens);


		features.add(makeTokenFeature(middleTokens,inverseFeature,MIDDLE_PREFIX));
		features.add(makeTokenFeature(leftWindowTokens,inverseFeature,LEFT_PREFIX));
		features.add(makeTokenFeature(rightWindowTokens,inverseFeature,RIGHT_PREFIX));		
		features.add(makeSequenceFeature(generalMiddleTokens,inverseFeature,MIDDLE_PREFIX,GENERAL_FEATURE));
		features.add(makeSequenceFeature(generalLeftTokens,inverseFeature,LEFT_PREFIX,GENERAL_FEATURE));
		features.add(makeSequenceFeature(generalRightTokens,inverseFeature,RIGHT_PREFIX,GENERAL_FEATURE));
		
		List<List<String>> generalMiddleBigrams = getMiddleBigrams(generalMiddleTokens,3);
		List<List<String>> generalLeftBigrams = getLeftBigrams(generalLeftTokens,2);
		List<List<String>> generalRightBigrams = getRightBigrams(generalRightTokens,2);
		
		for(List<String> bigrams : generalMiddleBigrams){
			features.add(makeSequenceFeature(bigrams,inverseFeature,MIDDLE_PREFIX,GENERAL_FEATURE,BIGRAM_FEATURE));
		}
		for(List<String> bigrams : generalLeftBigrams){
			features.add(makeSequenceFeature(bigrams,inverseFeature,LEFT_PREFIX,GENERAL_FEATURE,BIGRAM_FEATURE));
		}
		for(List<String> bigrams : generalRightBigrams){
			features.add(makeSequenceFeature(bigrams,inverseFeature,RIGHT_PREFIX,GENERAL_FEATURE,BIGRAM_FEATURE));
		}
		
		
		features.add(DISTANCE_FEATURE+" "+ getDistanceFeature(middleTokens.size()));
		features.add(TYPE_FEATURE + " " + inverseFeature + " " + getTypeFeature(leftArgOffsets,rightArgOffsets,tokens));
		return features;
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
		
		bigrams.addAll(getLeftBigrams(middleLeftTokens,numBigrams));
		List<List<String>> middleRightBigrams = getRightBigrams(middleRightTokens,numBigrams);
		
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
		CustomCorpusInformationSpecification cis = new DefaultCorpusInformationSpecification();
		List<DocumentInformationI> ldi = new ArrayList<DocumentInformationI> ();
		List<SentInformationI> lsi = new ArrayList<SentInformationI> ();
		ldi.add(new DocCorefInformation());
		lsi.add(new SentNamedEntityLinkingInformation());

		cis.addDocumentInformation(ldi);
		cis.addSentenceInformation(lsi);
		FeatureGenerator fg = new GeneralizedFeatureGenerator();
		Corpus c = new Corpus("NELAndCorefTrain",cis,true);
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
					,sap.getArg2Offsets().first,sap.getArg2Offsets().second,sap.getArg1Id(),
					sap.getArg2Id(),senAnnoPair.first,senAnnoPair.second);
			
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

}
