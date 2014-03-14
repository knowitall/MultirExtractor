package edu.washington.multir.featuregeneration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.Triple;
import edu.washington.multir.corpus.DefaultCorpusInformationSpecification.SentDependencyInformation.DependencyAnnotation;
import edu.washington.multir.corpus.DefaultCorpusInformationSpecification.TokenOffsetInformation.SentenceRelativeCharacterOffsetBeginAnnotation;
import edu.washington.multir.corpus.DefaultCorpusInformationSpecification.TokenOffsetInformation.SentenceRelativeCharacterOffsetEndAnnotation;
import edu.washington.multir.featuregeneration.FeatureGeneratorDraft3.DependencyType;
import edu.washington.multir.featuregeneration.FeatureGeneratorDraft3.Direction;
import edu.knowitall.tool.wordnet.JwiTools;
import edu.mit.jwi.Dictionary;
import edu.mit.jwi.item.IIndexWord;
import edu.mit.jwi.item.IIndexWordID;
import edu.mit.jwi.item.ISynset;
import edu.mit.jwi.item.IWord;
import edu.mit.jwi.item.IWordID;
import edu.mit.jwi.item.POS;
import edu.mit.jwi.morph.WordnetStemmer;

public class FeatureGeneratorMethods {
	
	public static final String PAD = "#PAD#";
	
	
	public static Set<String> acceptablePPChunkTokens = new HashSet<String>();
	public static Set<String> acceptableNPChunkTokens = new HashSet<String>();
	public static Set<String> negativeWords = new HashSet<String>();
	private static JwiTools jt;
	private static Dictionary d;
	private static WordnetStemmer stemmer;
	
	
	static{
	 acceptablePPChunkTokens.add("I-NP");
	 acceptablePPChunkTokens.add("B-NP");
	 acceptablePPChunkTokens.add("I-PP");
	 
	 acceptableNPChunkTokens.add("I-NP");
	 
	 negativeWords.add("not");
	 negativeWords.add("n't");
	 
	 
		d = new Dictionary(new File("/scratch2/resources/WordNet-3.0/WN/dict"));
		try {
			d.open();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		stemmer = new WordnetStemmer(d);
		jt = new JwiTools(d);
	 
	 
	}
	
	public static List<CoreLabel> getMiddleTokens(Integer arg1EndOffset, Integer arg2StartOffset, List<CoreLabel> tokens){
		List<CoreLabel> middleTokens = new ArrayList<CoreLabel>();
		for(CoreLabel tok: tokens){
			Integer tokBeginOffset = tok.get(SentenceRelativeCharacterOffsetBeginAnnotation.class);
			Integer tokEndOffset =  tok.get(SentenceRelativeCharacterOffsetEndAnnotation.class);
			if(tokBeginOffset >= arg1EndOffset && tokEndOffset < arg2StartOffset){
				middleTokens.add(tok);
			}
		}
		return middleTokens;
	}
	
	public static List<CoreLabel> getLeftWindowTokens(Integer arg1StartOffset, List<CoreLabel> tokens){
		List<CoreLabel> leftWindowTokens = new ArrayList<CoreLabel>();
		
		for(CoreLabel tok : tokens){
			int startOffset = tok.get(SentenceRelativeCharacterOffsetBeginAnnotation.class);
			if(startOffset < arg1StartOffset){
				leftWindowTokens.add(tok);
			}
			else{
				break;
			}
		}
		return leftWindowTokens;
	}
	
	public static List<CoreLabel> getRightWindowTokens(Integer arg2EndOffset, List<CoreLabel> tokens){
		List<CoreLabel> rightWindowTokens = new ArrayList<CoreLabel>();
		
		for(CoreLabel tok : tokens){
			int endOffset = tok.get(SentenceRelativeCharacterOffsetEndAnnotation.class);
			if(endOffset > arg2EndOffset){
				rightWindowTokens.add(tok);
			}
		}
		return rightWindowTokens;
	}
	
	public static List<CoreLabel> getRightWindowTokens(Integer arg1EndOffset, List<CoreLabel> tokens, int windowLength){
		List<CoreLabel> rightWindowTokens = getRightWindowTokens(arg1EndOffset,tokens);		
		return rightWindowTokens.subList(0, Math.min(2,rightWindowTokens.size()));
	}
	
	public static List<CoreLabel> getLeftWindowTokens(Integer arg1StartOffset, List<CoreLabel> tokens, int windowLength){
		List<CoreLabel> leftWindowTokens = getLeftWindowTokens(arg1StartOffset,tokens);		
		return leftWindowTokens.subList(Math.max(0,leftWindowTokens.size()-2), leftWindowTokens.size());
	}

	/**
	 * Tokens must have Chunk Annotation on them generated in the Feature Generator
	 * @param tokens
	 * @return
	 */
	public static List<String> getGeneralSequence(List<CoreLabel> tokens) {
		List<String> generalSequence = new ArrayList<String>();
		
		generalSequence.add(PAD);
		
		//get CoreLabel sequences
		List<List<CoreLabel>> sequences = new ArrayList<List<CoreLabel>>();
		List<List<CoreLabel>> ppSequences = getPPSequences(tokens);
		sequences.addAll(ppSequences);
		List<List<CoreLabel>> npSequences = getNPSequences(tokens,sequences);
		sequences.addAll(npSequences);
	    List<List<CoreLabel>> vpSequences = getVPSequences(tokens,sequences);
		
	    
	    //generate features
		List<Pair<String,Integer>> ppFeatures = generatePPFeatures(ppSequences);
		List<Pair<String,Integer>> npFeatuers = generateNPFeatures(npSequences);
		List<Pair<String,Integer>> vpFeatures = generateVPFeatures(vpSequences);
		List<Pair<String,Integer>> miscFeatures = generateMiscFeatures(tokens);
		
		
		List<Pair<String,Integer>> allFeatures = new ArrayList<Pair<String,Integer>>();
		allFeatures.addAll(ppFeatures);
		allFeatures.addAll(npFeatuers);
		allFeatures.addAll(vpFeatures);
		allFeatures.addAll(miscFeatures);
		
		//order features
		generalSequence.addAll(getOrderedFeatures(allFeatures));
		
		generalSequence.add(PAD);
		return generalSequence;
	}

	/**
	 * Misc features include things like negation tokens, possessives, and punctuation.
	 * @param tokens
	 * @return
	 */
	private static List<Pair<String, Integer>> generateMiscFeatures(
			List<CoreLabel> tokens) {
		
		List<Pair<String,Integer>> features = new ArrayList<Pair<String,Integer>>();
		Integer lastEndOffset = null;
		if(tokens.size()>0){
		  lastEndOffset = tokens.get(tokens.size()-1).get(SentenceRelativeCharacterOffsetEndAnnotation.class);
		}
		
		for(CoreLabel t : tokens){
			String chunkString = t.get(CoreAnnotations.ChunkAnnotation.class);
			Integer endOffset = t.get(SentenceRelativeCharacterOffsetEndAnnotation.class);
			String posString = t.get(CoreAnnotations.PartOfSpeechAnnotation.class);
			String word = t.get(CoreAnnotations.TextAnnotation.class);
			String feature = null;
			
			//punctuation
			if(chunkString.equals("O")){
				feature = word;
			}
			
			//possessive
			else if(posString.equals("POS")){
				feature = posString;
			}
			
			//final preposition
			else if(posString.equals("IN") && (lastEndOffset.equals(endOffset))){
				feature = word;
			}
			
			//negative word
			else if(negativeWords.contains(word.toLowerCase())){
				feature = "NEG";
			}
			
			if(feature !=null){
				Pair<String,Integer> p = new Pair<String,Integer>(feature,endOffset);
				features.add(p);
			}
		}
		
		
		return features;
	}

	private static List<Pair<String, Integer>> generateVPFeatures(
			List<List<CoreLabel>> vpSequences) {
		List<Pair<String,Integer>> vpFeatures = new ArrayList<Pair<String,Integer>>();
		
		
		for(List<CoreLabel> vpSequence : vpSequences){
			String vpFeature = generateVPFeature(vpSequence);
			if(vpFeature != null) {
				Integer endOffset = vpSequence.get(vpSequence.size()-1).get(SentenceRelativeCharacterOffsetEndAnnotation.class);
				Pair<String,Integer> p = new Pair<String,Integer> (vpFeature,endOffset);
				vpFeatures.add(p);
			}
		}
		
		return vpFeatures;
	}

	private static String generateVPFeature(List<CoreLabel> vpSequence) {
		StringBuilder sb = new StringBuilder();
		String headVerb = findHeadVerb(vpSequence);
		if(headVerb != null){
			
			sb.append("VP(");
			sb.append(headVerb);
			sb.append(")");
			return sb.toString();
			
			
		}
		return null;
	}
	
	public static String getWordnetVerbStem(String word){
		ISynset s = getWordnetVerbSynset(word);
		if(s!=null){
			return s.getWord(1).getLemma();
		}
		else{
			return null;
		}
	}
	
	private static ISynset getWordnetVerbSynset(String word){
		ISynset s = null;
		try{
		    String strStem = stemmer.findStems(word, POS.VERB).get(0);
		    IIndexWord idxWord = d.getIndexWord(strStem, POS.VERB);
		    IWordID wordID = idxWord.getWordIDs().get(0);
		    IWord wnWord = d.getWord(wordID);
		    s = wnWord.getSynset();
		}
		catch(IllegalArgumentException e){
			
		}
		catch(NullPointerException e){
			
		}
		catch(IndexOutOfBoundsException e){
			
		}
		return s;
	}

	private static String findHeadVerb(List<CoreLabel> sequence) {
		StringBuilder sb = new StringBuilder();
		ISynset lastS = null;
		
		for(int j = sequence.size()-1; j > -1; j--){
			ISynset s = null;
			CoreLabel t = sequence.get(j);
			String word = t.get(CoreAnnotations.TextAnnotation.class);
			String pos = t.get(CoreAnnotations.PartOfSpeechAnnotation.class);
			StringBuilder newWord = new StringBuilder();
			newWord.append(word);
			newWord.append(" ");
			newWord.append(sb);
			if(pos.startsWith("V")){
				s = getWordnetVerbSynset(newWord.toString());
			}
			if(s != null){
				lastS = s;
				sb = newWord;
			}
			else{
				break;
			}
		}
		
		if(lastS != null) {
			return lastS.getWord(1).getLemma();
		}
		else{
			return null;
		}
	}

	private static List<List<CoreLabel>> getVPSequences(List<CoreLabel> tokens,
			List<List<CoreLabel>> sequences) {
		List<List<CoreLabel>> vpSequences = new ArrayList<List<CoreLabel>>();
		
		for(int i =0; i < tokens.size(); i++){
			//get possible search span that doesn't overlap with PPs
			Integer end = getSearchSpan(i,sequences,tokens);
			if(end != null){
				List<CoreLabel> vpSequence = getVPSequence(i,end,tokens);
				if(vpSequence.size() > 0){
					vpSequences.add(vpSequence);
					i += (vpSequence.size()-1);
				}
			}
			
		}
		
		
		//only use non-intersecting sequences
		
		return vpSequences;
	}

	private static List<CoreLabel> getVPSequence(int start, Integer end,
			List<CoreLabel> tokens) {
		
		List<CoreLabel> vpSequence = new ArrayList<CoreLabel>();
		CoreLabel startToken = tokens.get(start);
		String startTokenChunk = startToken.get(CoreAnnotations.ChunkAnnotation.class);
		if(startTokenChunk.endsWith("-VP")){
			vpSequence.add(startToken);
			for(int i = start+1; i< end; i++){
				CoreLabel t = tokens.get(i);
				String chunkString = t.get(CoreAnnotations.ChunkAnnotation.class);
				if(chunkString.endsWith("-VP")){
					    vpSequence.add(t);
				}
				else{
					break;
				}
			}
		}
		return vpSequence;
	}

	private static List<Pair<String, Integer>> generateNPFeatures(
			List<List<CoreLabel>> npSequences) {
		List<Pair<String,Integer>> npFeatures = new ArrayList<Pair<String,Integer>>();
		
		
		for(List<CoreLabel> npSequence : npSequences){
			String npFeature = generateNPFeature(npSequence);
			if(npFeature != null) {
				Integer endOffset = npSequence.get(npSequence.size()-1).get(SentenceRelativeCharacterOffsetEndAnnotation.class);
				Pair<String,Integer> p = new Pair<String,Integer> (npFeature,endOffset);
				npFeatures.add(p);
			}
		}
		
		return npFeatures;
	}

	private static String generateNPFeature(List<CoreLabel> npSequence) {
		StringBuilder sb = new StringBuilder();
		String headNoun = findHeadString(npSequence);
		if(headNoun != null){
			
			sb.append("NP(");
			sb.append(headNoun);
			sb.append(")");
			return sb.toString();
			
			
		}
		return null;
	}

	private static List<String> getOrderedFeatures(
			List<Pair<String, Integer>> features) {
		List<String> orderedFeatures = new ArrayList<String>();
		
		Collections.sort(features, new Comparator<Pair<String,Integer>>(){
			@Override
			public int compare(Pair<String, Integer> arg0,
					Pair<String, Integer> arg1) {
				return arg0.second.compareTo(arg1.second);
			}
		});
		
		
		for(Pair<String,Integer> p : features){
			orderedFeatures.add(p.first);
		}
		return orderedFeatures;
	}

	private static List<List<CoreLabel>> getNPSequences(List<CoreLabel> tokens,
			List<List<CoreLabel>> ppSequences) {
		List<List<CoreLabel>> npSequences = new ArrayList<List<CoreLabel>>();
		
		for(int i =0; i < tokens.size(); i++){
			//get possible search span that doesn't overlap with PPs
			Integer end = getSearchSpan(i,ppSequences,tokens);
			if(end != null){
				List<CoreLabel> npSequence = getNPSequence(i,end,tokens);
				if(npSequence.size() > 0){
					npSequences.add(npSequence);
					i += (npSequence.size()-1);
				}
			}
			
		}
		
		
		//only use non-intersecting sequences
		
		return npSequences;
	}

	private static List<CoreLabel> getNPSequence(int start, Integer end,
			List<CoreLabel> tokens) {
		List<CoreLabel> npSequence = new ArrayList<CoreLabel>();
		CoreLabel startToken = tokens.get(start);
		String startTokenChunk = startToken.get(CoreAnnotations.ChunkAnnotation.class);
		String startTokenPos = startToken.get(CoreAnnotations.PartOfSpeechAnnotation.class);
		if((startTokenChunk.endsWith("-NP")) && (!startTokenPos.equals("POS"))){
			npSequence.add(startToken);
			
			for(int i = start+1; i< end; i++){
				CoreLabel t = tokens.get(i);
				String chunkString = t.get(CoreAnnotations.ChunkAnnotation.class);
				String posString = t.get(CoreAnnotations.PartOfSpeechAnnotation.class);
				if(acceptableNPChunkTokens.contains(chunkString)  && (!posString.equals("POS"))){
					    npSequence.add(t);
				}
				else{
					break;
				}
			}
		}
		return npSequence;
	}

	/**
	 * Identifies if the longest token span starting at start that does not
	 * interfere with any of the sequences passed in as arguments, returns
	 * null if no such span
	 * @param start
	 * @param sequences
	 * @param tokens
	 * @return
	 */
	private static Integer getSearchSpan(int start,
			List<List<CoreLabel>> sequences, List<CoreLabel> tokens) {
		
		CoreLabel startToken = tokens.get(start);
		Integer charStartOffset = startToken.get(SentenceRelativeCharacterOffsetBeginAnnotation.class);
		Integer charEndOffset = startToken.get(SentenceRelativeCharacterOffsetEndAnnotation.class);
		if(intersectsWithSequences(charStartOffset,charEndOffset,sequences)){
			return null;
		}
		for(int j = tokens.size()-1; j >= start; j--){
			CoreLabel endToken = tokens.get(j);
			charEndOffset = endToken.get(SentenceRelativeCharacterOffsetEndAnnotation.class);
			if(!intersectsWithSequences(charStartOffset,charEndOffset,sequences)){
				return j+1;
			}
		}
		
		return null;
	}
	
	private static boolean intersectsWithSequences(Integer charStart, Integer charEnd, List<List<CoreLabel>> sequences){
		
		for(List<CoreLabel> sequence: sequences){
			for(CoreLabel t: sequence){
				Integer tStart = t.get(SentenceRelativeCharacterOffsetBeginAnnotation.class);
				if((tStart >= charStart) && (tStart < charEnd)){
					return true;
				}
			}
		}
		return false;
	}

	private static List<Pair<String,Integer>> generatePPFeatures(
			List<List<CoreLabel>> ppSequences) {
		List<Pair<String,Integer>> ppFeatures = new ArrayList<Pair<String,Integer>>();
		
		
		for(List<CoreLabel> ppSequence : ppSequences){
			String ppFeature = generatePPFeature(ppSequence);
			if(ppFeature != null) {
				Integer endOffset = ppSequence.get(ppSequence.size()-1).get(SentenceRelativeCharacterOffsetEndAnnotation.class);
				Pair<String,Integer> p = new Pair<String,Integer> (ppFeature,endOffset);
				ppFeatures.add(p);
			}
		}
		
		return ppFeatures;
		
	}

	private static String generatePPFeature(List<CoreLabel> ppSequence) {
		StringBuilder sb = new StringBuilder();
		String headNoun = findHeadString(ppSequence);
		if(headNoun != null){
			
			sb.append("PP(");
			sb.append(headNoun);
			sb.append(")");
			return sb.toString();
			
			
		}
		return null;

	}

	private static String findHeadString(List<CoreLabel> sequence) {
		String entityType  = findHeadEntityType(sequence);
	    if(entityType != null){
	    	return entityType;
	    }
	    return findHeadNounByWordnet(sequence);
	}
	
	private static String findHeadNounByPOS(List<CoreLabel> sequence){
		List<String> nounWords = new ArrayList<String>();
		
		for(int j = sequence.size()-1; j > -1; j--){
			CoreLabel t = sequence.get(j);
			String pos = t.get(CoreAnnotations.PartOfSpeechAnnotation.class);
			if(pos.startsWith("N")){
				nounWords.add(t.get(CoreAnnotations.TextAnnotation.class));
				
			}
			else{
				break;
			}
		}
		
		if(nounWords.size() == 0){
			return null;
		}
		
		Collections.reverse(nounWords);
		StringBuilder sb = new StringBuilder();
		for(String nw: nounWords){
			sb.append(nw);
			sb.append(" ");
		}
		return sb.toString().trim();
	}
	
	private static String getWordnetNounLemma(String word){
		ISynset s = getWordnetNounSynset(word);
		
		if(s!=null){
			return s.getWord(1).getLemma();
		}
		else{
			return null;
		}
	}
	
	private static ISynset getWordnetNounSynset(String word){
		ISynset s= null;
		try{
			  s = jt.stringToNthSynset(word,0);
			}
			catch(IllegalArgumentException e){
				
			}
			catch(NullPointerException e){
				
			}
		return s;
	}
	
	private static String findHeadNounByWordnet(List<CoreLabel> sequence){
		StringBuilder sb = new StringBuilder();
		ISynset lastS = null;
		
		for(int j = sequence.size()-1; j > -1; j--){
			ISynset s = null;
			CoreLabel t = sequence.get(j);
			String word = t.get(CoreAnnotations.TextAnnotation.class);
			String pos = t.get(CoreAnnotations.PartOfSpeechAnnotation.class);
			StringBuilder newWord = new StringBuilder();
			newWord.append(word);
			newWord.append(" ");
			newWord.append(sb);
			if(pos.startsWith("N")){
				s = getWordnetNounSynset(newWord.toString().trim());
			}
			if(s != null){
				lastS = s;
				sb = newWord;
			}
			else{
				break;
			}
		}
		
		if(lastS != null) {
			return lastS.getWord(1).getLemma();
		}
		else{
			return null;
		}
		
		
	}
	
	private static String getWordNetNounLemma(String p){
		List<String> stems = stemmer.findStems(p, POS.NOUN);
		if(stems.size()> 0){
			return stems.get(0);
		}
		return null;
	}
	
	private static String getWordNetVerbLemma(String p){
		List<String> stems = stemmer.findStems(p, POS.VERB);
		if(stems.size()> 0){
			return stems.get(0);
		}
		return null;
	}

	private static String findHeadEntityType(List<CoreLabel> sequence) {
		CoreLabel lastToken = sequence.get(sequence.size()-1);
		String type = lastToken.get(CoreAnnotations.NamedEntityTagAnnotation.class);
		if(type != null){
			if(!type.equals("O")){
				return type;
			}
		}
		return null;
		
	}

	private static List<List<CoreLabel>> getPPSequences(
			List<CoreLabel> middleTokens) {
		List<List<CoreLabel>> ppSequences = new ArrayList<List<CoreLabel>>();

		for(int i =0; i < middleTokens.size(); i++){
			List<CoreLabel> ppSequence = getPPSequence(i,middleTokens.size(),middleTokens);
			if(ppSequence.size() > 0){
				ppSequences.add(ppSequence);
				i += ppSequence.size()-1;
			}
		}
		
		return ppSequences;
	}

	private static List<CoreLabel> getPPSequence(int start, int end,
			List<CoreLabel> tokens) {
		List<CoreLabel> ppSequence = new ArrayList<CoreLabel>();
		CoreLabel startToken = tokens.get(start);
		String startTokenChunk = startToken.get(CoreAnnotations.ChunkAnnotation.class);
		if(startTokenChunk.equals("B-PP")){
			ppSequence.add(startToken);
			
			for(int i = start+1; i< end; i++){
				CoreLabel t = tokens.get(i);
				String chunkString = t.get(CoreAnnotations.ChunkAnnotation.class);
				if(acceptablePPChunkTokens.contains(chunkString)){
					ppSequence.add(t);
				}
				else{
					break;
				}
			}
		}
		return ppSequence;
	}

	public static Pair<Integer, Integer> getLeftArgOffsets(
			Integer arg1StartOffset, Integer arg1EndOffset,
			Integer arg2StartOffset, Integer arg2EndOffset) {
		
		if(arg1StartOffset < arg2StartOffset){
			return new Pair<Integer,Integer>(arg1StartOffset,arg1EndOffset);
		}
		else{
			return new Pair<Integer,Integer>(arg2StartOffset,arg2EndOffset);
		}
	}

	public static Pair<Integer, Integer> getRightArgOffsets(
			Integer arg1StartOffset, Integer arg1EndOffset,
			Integer arg2StartOffset, Integer arg2EndOffset) {
		if(arg1StartOffset > arg2StartOffset){
			return new Pair<Integer,Integer>(arg1StartOffset,arg1EndOffset);
		}
		else{
			return new Pair<Integer,Integer>(arg2StartOffset,arg2EndOffset);
		}
	}

//	public static String getDependencyHeightDifference(
//			Pair<Integer, Integer> leftArgOffsets,
//			Pair<Integer, Integer> rightArgOffsets, CoreMap sentence) {
//		
//		List<Triple<Integer,String,Integer>>  dependencyParse = sentence.get(DependencyAnnotation.class);
//		List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
//		
//		Integer leftTokenOffset = null;
//		Integer rightTokenOffset = null;
//		for(int i =0; i < tokens.size(); i++){
//			CoreLabel t = tokens.get(i);
//			Integer endOffset = t.get(CoreAnnotations.CharacterOffsetEndAnnotation.class);
//			if(endOffset.equals(leftArgOffsets.second)){
//				leftTokenOffset = i+1;
//			}
//			if(endOffset.equals(rightArgOffsets.second)){
//				rightTokenOffset = i+1;
//			}
//		}
//		
//		Integer leftParseDepth = getParseDepth(leftTokenOffset,dependencyParse);
//		Integer rightParseDepth = getParseDepth(rightTokenOffset,dependencyParse);
//		
//		
//		return leftParseDepth - rightParseDepth;
//	}
//	
//	private int getParseDepth(int tokenOffset, List<Triple<Integer,String,Integer>> depParse){
//		
//	}

	//returns token offset indexed by 1
	private static Integer getTokenOffset(Integer endCharOffset, List<CoreLabel> tokens){
		
		for(int i =0; i < tokens.size(); i++){
			CoreLabel t= tokens.get(i);
			Integer tEndCharOffset = t.get(SentenceRelativeCharacterOffsetEndAnnotation.class);
			if(tEndCharOffset.equals(endCharOffset)){
				return i+1;
			}
		}
		return null;
	}
	
	public static List<Triple<CoreLabel, DependencyType, CoreLabel>> getDependencyPath(Integer arg1EndCharOffset,
			Integer arg2EndCharOffset, CoreMap sentence) {
		
		List<Triple<CoreLabel,DependencyType,CoreLabel>> depPath= new ArrayList<Triple<CoreLabel,DependencyType,CoreLabel>>();
		List<Triple<Integer,String,Integer>> dependencyInfo = sentence.get(DependencyAnnotation.class);
		List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
		Integer startTokenOffset = getTokenOffset(arg1EndCharOffset,tokens);
		Integer endTokenOffset = getTokenOffset(arg2EndCharOffset,tokens);
//		
//		System.out.println("Start = " + startTokenOffset);
//		System.out.println("End = " + endTokenOffset);
	//	System.out.println("DEP INFO SIZE = " +dependencyInfo.size());
		recursiveDependencySearch(depPath,depPath,dependencyInfo,tokens,startTokenOffset,endTokenOffset,-1,0,new BooleanFlag(false));
	//	System.out.println("DEP PATH SIZE = " + depPath.size());
		return depPath;

	}
	
	private static class BooleanFlag {
		private boolean value;
		
		public BooleanFlag(boolean b){
			this.value = b;
		}
		
		public void setValue(boolean b){
			this.value = b;
		}
		
		public boolean getValue(){return this.value;}
	}

	private static void recursiveDependencySearch(
			List<Triple<CoreLabel, DependencyType, CoreLabel>> depPath,
			List<Triple<CoreLabel, DependencyType, CoreLabel>> currPath,
			List<Triple<Integer, String, Integer>> dependencyInfo,
			List<CoreLabel> tokens, Integer startTokenOffset, Integer endTokenOffset,
			Integer previousStart, int count, BooleanFlag stop) {
		
		if(stop.getValue()){
			return;
		}
		
//		System.out.println("Start :" + startTokenOffset);
//		System.out.println("End: " +endTokenOffset); 
		//stop case
		if(startTokenOffset.equals(endTokenOffset)){
			//set depPath
			depPath.addAll(currPath);
			stop.setValue(true);
			return;
		}
				
		else if(count < 15){
			//find children and parents
			List<Triple<Integer,String,Integer>> children = new ArrayList<Triple<Integer,String,Integer>>();
			List<Triple<Integer,String,Integer>> parents = new ArrayList<Triple<Integer,String,Integer>>();
			Set<Integer> childTokens = new HashSet<Integer>();
			for(int i =0; i < dependencyInfo.size(); i++){
				Triple<Integer,String,Integer> depTriple = dependencyInfo.get(i);
				if(depTriple.first.equals(startTokenOffset) && (!childTokens.contains(depTriple.third))){
					children.add(depTriple);
					childTokens.add(depTriple.third);
				}
				else if(depTriple.third.equals(startTokenOffset)){
					parents.add(depTriple);
				}
			}

//			System.out.println("Children:");
//			for(Triple<Integer,String,Integer> child: children){
//				System.out.print(child.first+"\t" + child.second + "\t" + child.third+"\n");
//			}
//			System.out.println("Parents:");
//			for(Triple<Integer,String,Integer> parent: parents){
//				System.out.print(parent.first+"\t" + parent.second + "\t" + parent.third+"\n");
//			}
			
			

			
			//search children
			//for each child create a new depPath from the previous one and recursively call search
			for(Triple<Integer,String,Integer> childTriple : children){
				CoreLabel parent = null;
				if(childTriple.first >0){
					parent = tokens.get(childTriple.first-1);
				}
				CoreLabel child = tokens.get(childTriple.third-1);
				DependencyType dt = new DependencyType(childTriple.second,Direction.DOWN);
				Triple<CoreLabel,DependencyType,CoreLabel> childPath = new Triple<>(parent,dt,child);
				List<Triple<CoreLabel,DependencyType,CoreLabel>> newCurrPath = new ArrayList<>();
				newCurrPath.addAll(currPath);
				newCurrPath.add(childPath);
				if(!childTriple.third.equals(previousStart)){
					recursiveDependencySearch(depPath,newCurrPath,dependencyInfo,tokens,childTriple.third,endTokenOffset,startTokenOffset,count+1,stop);
				}
			}
			
			//search parent
			//should only be one parent
			if(parents.size() == 1){
				Triple<Integer,String,Integer> parentTriple = parents.get(0);
				CoreLabel parent = null;
				if(parentTriple.first > 0 ){
				  parent = tokens.get(parentTriple.first-1);
				}
				CoreLabel child = tokens.get(parentTriple.third-1);
				DependencyType dt = new DependencyType(parentTriple.second,Direction.UP);
				Triple<CoreLabel,DependencyType,CoreLabel> parentPath = new Triple<>(child,dt,parent);
				List<Triple<CoreLabel,DependencyType,CoreLabel>> newCurrPath = new ArrayList<>();
				newCurrPath.addAll(currPath);
				newCurrPath.add(parentPath);
				if(!parentTriple.first.equals(previousStart)){
				 recursiveDependencySearch(depPath,newCurrPath,dependencyInfo,tokens,parentTriple.first,endTokenOffset,startTokenOffset,count+1,stop);
				}
			}
			else{
				return;
			}
		}
		return;
	}

	public static String getWordnetStemFeature(CoreLabel token) {
		String pos = token.get(CoreAnnotations.PartOfSpeechAnnotation.class);
		String word = token.get(CoreAnnotations.TextAnnotation.class);
		
		if(pos.startsWith("N")){
			String lemma = getWordnetNounLemma(word);
			if(lemma != null){
			  return "N" + "(" + lemma + ")";
			}
			else{
				return null;
			}
		}
		else if(pos.startsWith("V")){
			String lemma = getWordnetVerbStem(word);
			if(lemma != null){
			  return "V" + "(" + lemma + ")";
			}
			else{
				return null;
			}
		}
		else{
			return null;
		}
	}
}
