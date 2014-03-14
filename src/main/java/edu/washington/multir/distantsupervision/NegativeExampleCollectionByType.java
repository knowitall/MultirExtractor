package edu.washington.multir.distantsupervision;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.Triple;
import edu.washington.multir.corpus.CorpusInformationSpecification.SentGlobalIDInformation.SentGlobalID;
import edu.washington.multir.corpus.DefaultCorpusInformationSpecification.TokenOffsetInformation.SentenceRelativeCharacterOffsetEndAnnotation;
import edu.washington.multir.data.KBArgument;
import edu.washington.multir.data.NegativeAnnotation;
import edu.washington.multir.knowledgebase.KnowledgeBase;

public class NegativeExampleCollectionByType extends NegativeExampleCollection{


	public static NegativeExampleCollection getInstance(double ratio) {
		NegativeExampleCollection nec = new NegativeExampleCollectionByType();
		nec.negativeToPositiveRatio = ratio;
		return nec;
	}
	
	@Override
	public List<NegativeAnnotation> filter(
			List<NegativeAnnotation> negativeExamples,
			List<Pair<Triple<KBArgument, KBArgument, String>, Integer>> positiveExamples,
			KnowledgeBase kb, List<CoreMap> sentences) {
		
		Map<Pair<String,String>,Integer> typeCount = new HashMap<>();
		
		for(Pair<Triple<KBArgument,KBArgument,String>,Integer> p: positiveExamples){
			Triple<KBArgument,KBArgument,String> t = p.first;
			KBArgument arg1 = t.first;
			KBArgument arg2 = t.second;
			Pair<String,String> typePair = getTypePair(arg1.getEndOffset(),arg2.getEndOffset(),findSentence(sentences,p.second));
			if(typeCount.containsKey(typePair)){
				typeCount.put(typePair, typeCount.get(typePair)+1);
			}
			else{
				typeCount.put(typePair,1);
			}
		}
		
		//update Map
		for(Pair<String,String> typePair: typeCount.keySet()){
			Integer originalCount = typeCount.get(typePair);
			Integer newCount = (int) Math.floor(originalCount*negativeToPositiveRatio);
			typeCount.put(typePair, newCount);
		}
		
		//shuffle negative examples
		Collections.shuffle(negativeExamples);
		List<NegativeAnnotation> filtered = new ArrayList<>();
		for(NegativeAnnotation anno : negativeExamples){
			Integer globalID = anno.getSentNum();
			KBArgument arg1 = anno.getArg1();
			KBArgument arg2 = anno.getArg2();
			if(typeCount.size() == 0){
				break;
			}
			else{
				CoreMap sentence = findSentence(sentences,globalID);
				Pair<String,String> typePair = getTypePair(arg1.getEndOffset(),arg2.getEndOffset(),sentence);
				if(typeCount.containsKey(typePair)){
					Integer count = typeCount.get(typePair);
					filtered.add(anno);
					Integer newCount = count -1;
					if(newCount == 0){
						typeCount.remove(typePair);
					}
					else{
						typeCount.put(typePair,newCount);
					}
				}
			}
		}
		return filtered;
	}
	
	
	
	private CoreMap findSentence(List<CoreMap> sentences, Integer sentGlobalID) {
		CoreMap sentence = null;
		for(CoreMap s : sentences){
			if(s.get(SentGlobalID.class).equals(sentGlobalID)){
				sentence = s;
				break;
			}
		}
		return sentence;
	}
	
	private Pair<String,String> getTypePair(Integer arg1EndOffset,Integer arg2EndOffset,CoreMap sentence ){
		List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
		String arg1Type = "";
		String arg2Type = "";
		for(CoreLabel tok : tokens){
			Integer tokEndOffset = tok.get(SentenceRelativeCharacterOffsetEndAnnotation.class);
			if(tokEndOffset.equals(arg1EndOffset)){
				arg1Type = tok.get(CoreAnnotations.NamedEntityTagAnnotation.class);
			}
			if(tokEndOffset.equals(arg2EndOffset)){
				arg2Type = tok.get(CoreAnnotations.NamedEntityTagAnnotation.class);
			}
		}
		Pair<String,String> typePair = new Pair<>(arg1Type,arg2Type);
		return typePair;
	}

}
