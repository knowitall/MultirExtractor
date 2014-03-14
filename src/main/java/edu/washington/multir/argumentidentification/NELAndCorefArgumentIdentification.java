package edu.washington.multir.argumentidentification;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.stanford.nlp.dcoref.CorefChain;
import edu.stanford.nlp.dcoref.CorefChain.CorefMention;
import edu.stanford.nlp.dcoref.CorefCoreAnnotations;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.Triple;
import edu.washington.multir.corpus.DefaultCorpusInformationSpecification.TokenOffsetInformation.SentenceRelativeCharacterOffsetBeginAnnotation;
import edu.washington.multir.corpus.DefaultCorpusInformationSpecification.TokenOffsetInformation.SentenceRelativeCharacterOffsetEndAnnotation;
import edu.washington.multir.corpus.DefaultCorpusInformationSpecificationWithNEL.SentNamedEntityLinkingInformation.NamedEntityLinkingAnnotation;
import edu.washington.multir.data.Argument;
import edu.washington.multir.data.KBArgument;

public class NELAndCorefArgumentIdentification implements
		ArgumentIdentification {
	
	private static NELAndCorefArgumentIdentification instance = null;
	
	private NELAndCorefArgumentIdentification(){}
	public static NELAndCorefArgumentIdentification getInstance(){
		if(instance == null) instance = new NELAndCorefArgumentIdentification();
		return instance;
		}

	@Override
	public List<Argument> identifyArguments(Annotation d, CoreMap s) {
		List<Argument> args = new ArrayList<>();
		List<Argument> corefArgs = new ArrayList<>();
		List<CoreLabel> tokens = s.get(CoreAnnotations.TokensAnnotation.class);
		int localSentNum = getLocalSentIndex(d,s);
		Map<Integer,CorefChain> corefChainMap = d.get(CorefCoreAnnotations.CorefChainAnnotation.class);
		List<CorefMention> mentionsInSentence = new ArrayList<>();
		//find all coref mentions in the current sentence
		if(corefChainMap != null){
			for(Integer k : corefChainMap.keySet()){
				CorefChain cc = corefChainMap.get(k);
				List<CorefMention> corefMentions = cc.getMentionsInTextualOrder();
				for(CorefMention cm : corefMentions){
					if(cm.sentNum == localSentNum){
						mentionsInSentence.add(cm);
					}
				}	
			}			
			for(CorefMention cm : mentionsInSentence){
				String kbLink = getLink(corefChainMap.get(cm.corefClusterID),d);
				// if we found a most popular link, then instantiate a KBArgument
				if((tokens.size() > 0) && tokens.size() > (cm.endIndex-2)){
					Integer startOffset = tokens.get(cm.startIndex-1).get(SentenceRelativeCharacterOffsetBeginAnnotation.class);
					Integer endOffset = tokens.get(cm.endIndex-2).get(SentenceRelativeCharacterOffsetEndAnnotation.class);
					if(cm.mentionSpan.split("\\s+").length < 6){
						Argument arg = new Argument(cm.mentionSpan,startOffset,endOffset);
						if(kbLink != null){
							KBArgument kbArg = new KBArgument(arg,kbLink);	
							corefArgs.add(kbArg);
						}
						// else instantiate an ordinary Argument
						else{
							corefArgs.add(arg);
						}
					}
				}
			}
		}
		List<Argument> nelArgs = NELArgumentIdentification.getInstance().identifyArguments(d,s);
		
		for(Argument nelArg: nelArgs){
			if(nelArg instanceof KBArgument){
				args.add(nelArg);
			}
		}
		
		for(Argument corefArg: corefArgs){
			if(corefArg instanceof KBArgument){
				if(!corefArg.containedInList(args)){
					args.add(corefArg);
				}
			}
		}
		
		for(Argument nelArg: nelArgs){
			if(!(nelArg instanceof KBArgument)){
				if(!nelArg.intersectsWithList(args)){
					args.add(nelArg);
				}
			}
		}
		
		for(Argument corefArg: corefArgs){
			if(!(corefArg instanceof KBArgument)){
				if(!corefArg.intersectsWithList(args)){
					args.add(corefArg);
				}
			}
		}
		
//		//first take all nelArgs then add non-intersecting coref args
//		args.addAll(corefArgs);
//		for(Argument nelArg: nelArgs){
//			boolean addNelArg = true;
//			for(Argument corefArg: corefArgs){
//				Integer corefStart = corefArg.getStartOffset();
//				Integer corefEnd = corefArg.getEndOffset();
//				Integer nelArgStart = nelArg.getStartOffset();
//				Integer nelArgEnd = nelArg.getEndOffset();
//				Interval<Integer> corefInterval = Interval.toInterval(corefStart, corefEnd);
//				Interval<Integer> nelInterval = Interval.toInterval(nelArgStart, nelArgEnd);
//				if(corefInterval.intersect(nelInterval) != null){
//					addNelArg = false;
//				}
//			}
//			if(addNelArg){
//				args.add(nelArg);
//			}
//		}
		
		//debug
//		System.out.println("Sentence " + s.get(SentGlobalID.class) + " arguments:");
//		for(Argument arg: args){
//			System.out.print(arg.getArgName());
//			if(arg instanceof KBArgument){
//				System.out.print("\t"+ ((KBArgument)arg).getKbId());
//			}
//			System.out.print("\n");
//		}
		
		
		return args;
	}

	// returns the most popular link in the coref cluster or null
	// if there are no links in the cluster
	private String getLink(CorefChain corefChain, Annotation doc) {
		Map<String,Integer> linkCount = new HashMap<>();
		
		List<CoreMap> sentences = doc.get(CoreAnnotations.SentencesAnnotation.class);
		
		for(CorefMention cm : corefChain.getMentionsInTextualOrder()){
			CoreMap sentence = sentences.get(cm.sentNum-1);
			List<Triple<Pair<Integer,Integer>,String,Float>> nelInfo = sentence.get(NamedEntityLinkingAnnotation.class);
			Set<String> applicableLinks = new HashSet<>();
			for(Triple<Pair<Integer,Integer>,String,Float> trip : nelInfo){
				Pair<Integer,Integer> nelTokens = trip.first;
				if(  (nelTokens.first == (cm.startIndex-1)) &&
					 (nelTokens.second == (cm.endIndex-1))){
					if(trip.third > .5){
						applicableLinks.add(trip.second);
					}
				}
			}
			if(applicableLinks.size() == 1){
				String key = applicableLinks.iterator().next();
				if(linkCount.containsKey(key)){
					linkCount.put(key, linkCount.get(key)+1);
				}
				else{
					linkCount.put(key, new Integer(1));
				}
			}
		}
		int max = 0;
		String argMax = null;
		
		for(String entityID : linkCount.keySet()){
			Integer occ = linkCount.get(entityID);
			if(occ > max){
				max = occ;
				argMax = entityID;
			}
		}
		return argMax;
	}

	private int getLocalSentIndex(Annotation d, CoreMap s) {
		List<CoreMap> sentences = d.get(CoreAnnotations.SentencesAnnotation.class);
		String thisText = s.get(CoreAnnotations.TextAnnotation.class);
		int i =1;
		for(CoreMap sentence: sentences){
			String otherText = sentence.get(CoreAnnotations.TextAnnotation.class);
			if(thisText.equals(otherText)){
				return i;
			}
			i++;
		}
		return -1;
	}

}
