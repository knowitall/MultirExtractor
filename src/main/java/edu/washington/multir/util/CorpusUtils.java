package edu.washington.multir.util;

import java.util.List;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;
import edu.washington.multir.corpus.DefaultCorpusInformationSpecification.TokenOffsetInformation.SentenceRelativeCharacterOffsetBeginAnnotation;
import edu.washington.multir.corpus.DefaultCorpusInformationSpecification.TokenOffsetInformation.SentenceRelativeCharacterOffsetEndAnnotation;

public class CorpusUtils {
	
	public static Pair<Integer,Integer> getTokenOffsetsFromCharacterOffsets(Integer charStartOffset, Integer charEndOffset, CoreMap sentence){
		List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
		
		Integer tokenStart =-1;
		Integer tokenEnd = -1;
		for(int i =0; i < tokens.size(); i++){
			
			CoreLabel t = tokens.get(i);
			Integer tokenCharStartOffset = t.get(SentenceRelativeCharacterOffsetBeginAnnotation.class);
			Integer tokenCharEndOffset = t.get(SentenceRelativeCharacterOffsetEndAnnotation.class);
			
			if(tokenCharStartOffset.equals(charStartOffset)){
				tokenStart = i;
			}
			if(tokenCharEndOffset.equals(charEndOffset)){
				tokenEnd = i +1;
			}
		}
		
		if(tokenStart != -1 && tokenEnd != -1){
			return new Pair<Integer,Integer>(tokenStart,tokenEnd);
		}
		else{
			return null;
		}
		
	}
	
	
	public static Pair<Integer,Integer> getCharOffsetsFromTokenOffsets(Integer tokenStartOffset, Integer tokenEndOffset, CoreMap sentence){
		
		List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
		Integer startTokenCharOffset = -1;
		Integer endTokenCharOffset = -1;
		
		if(tokenStartOffset < tokens.size()){
			startTokenCharOffset = tokens.get(tokenStartOffset).get(SentenceRelativeCharacterOffsetBeginAnnotation.class);
		}
		if(tokenEndOffset <= tokens.size()){
			endTokenCharOffset = tokens.get(tokenEndOffset-1).get(SentenceRelativeCharacterOffsetEndAnnotation.class);
		}
		
		
		
		if(startTokenCharOffset != -1 && endTokenCharOffset != -1){
			return new Pair<Integer,Integer>(startTokenCharOffset,endTokenCharOffset);
		}
		else{
			return null;
		}
		
	}

}
