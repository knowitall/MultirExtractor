package edu.washington.multir.featuregeneration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.Triple;
import edu.washington.multir.corpus.DefaultCorpusInformationSpecification.TokenOffsetInformation.SentenceRelativeCharacterOffsetBeginAnnotation;
import edu.washington.multir.corpus.DefaultCorpusInformationSpecification.TokenOffsetInformation.SentenceRelativeCharacterOffsetEndAnnotation;
import edu.washington.multir.corpus.DefaultCorpusInformationSpecificationWithNEL.SentNamedEntityLinkingInformation.NamedEntityLinkingAnnotation;
import edu.washington.multir.corpus.SentFreebaseNotableTypeInformation.FreebaseNotableTypeAnnotation;
import edu.washington.multir.data.Argument;
import edu.washington.multir.data.KBArgument;
import edu.washington.multir.util.FigerTypeUtils;
import edu.washington.multir.util.GuidMidConversion;

public class DefaultFeatureGeneratorIndepFIGER implements FeatureGenerator {

	@Override
	public List<String> generateFeatures(Integer arg1StartOffset,
			Integer arg1EndOffset, Integer arg2StartOffset,
			Integer arg2EndOffset, String arg1Id, String arg2Id,
			CoreMap sentence, Annotation document) {
		if (!FigerTypeUtils.isConnected()) {
			FigerTypeUtils.init();	
		}
		DefaultFeatureGenerator dfg = new DefaultFeatureGenerator();
		List<String> originalMultirFeatures = dfg.generateFeatures(arg1StartOffset, arg1EndOffset, arg2StartOffset, arg2EndOffset, arg1Id, arg2Id, sentence, document);
		List<String> features = new ArrayList<String>();
//		StringBuilder figerConcatenationString = new StringBuilder();
//		figerConcatenationString.append("(");
		
		
		Set<String> arg1FigerTypes = null;// new HashSet<String>();
		Set<String> arg2FigerTypes = null;// new HashSet<String>();
		List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
//		List<Triple<Pair<Integer,Integer>,String,String>> notableTypeData = sentence.get(FreebaseNotableTypeAnnotation.class);
		List<Triple<Pair<Integer,Integer>,String,Float>> nelData =sentence.get(NamedEntityLinkingAnnotation.class);
		
		//ignore arg ids as some of them come from NER DS or are null because of NER Argument IDentification
		if (arg1Id==null) {
		//System.out.println("origArg1 = "+arg1Id );
			arg1Id = getLinkId(nelData,arg1StartOffset,arg1EndOffset,tokens);
//		System.out.println(nelData.isEmpty());
//		System.out.println("arg1 now = "+arg1Id);
		}
		if (arg2Id==null) {
			arg2Id = getLinkId(nelData,arg2StartOffset,arg2EndOffset,tokens);
		}
		
		if(arg1Id != null && !arg1Id.equals("null")){
			//get figer typer
//			arg1FigerTypes = FigerTypeUtils.getFigerTypes(new KBArgument(new Argument("",arg1StartOffset,arg1EndOffset),arg1Id), notableTypeData, tokens);
//			String longestString = "O";
//			try {
			arg1FigerTypes = FigerTypeUtils.getFigerTypesFromID(GuidMidConversion.convertBackward(arg1Id));
//			}catch(Exception e){System.out.println("error a1id="+(arg1Id.equals("null")?"NULL":arg1Id));}
//			if (arg1FigerTypes.isEmpty()) {
				System.out.println("arg1 has no figer types " +arg1Id);
//			}
//			System.out.println(arg1Id+"\t"+arg1FigerTypes);
			for(String ftype: arg1FigerTypes){
				features.add("1:"+ftype);
			}
		}
		else{
//			System.out.println("no arg1Id " );
		}
		if(arg2Id != null && !arg2Id.equals("null")){
			//get figer typer
//			arg2FigerTypes = FigerTypeUtils.getFigerTypes(new KBArgument(new Argument("",arg2StartOffset,arg2EndOffset),arg2Id), notableTypeData, tokens);
			arg2FigerTypes = FigerTypeUtils.getFigerTypesFromID(GuidMidConversion.convertBackward(arg2Id));
			for(String ftype: arg2FigerTypes){
				features.add("2:"+ftype);
			}
		}
		else{
		}
		
		features.addAll(originalMultirFeatures);
		return features;
	}

	private String getLinkId(List<Triple<Pair<Integer, Integer>, String, Float>> nelData, Integer startOffset, Integer endOffset,
			List<CoreLabel> tokens) {
		
		String link = null;
		double linkScore = 0.0;
		for(Triple<Pair<Integer,Integer>,String,Float> nelDataTrip : nelData){
			if(tokens.get(nelDataTrip.first.first).get(SentenceRelativeCharacterOffsetBeginAnnotation.class).equals(startOffset) 
					&& 
			   tokens.get(nelDataTrip.first.second-1).get(SentenceRelativeCharacterOffsetEndAnnotation.class).equals(endOffset)
			   		&&
			   	(nelDataTrip.third > .5)){
				
				if(nelDataTrip.third > linkScore){
					link = nelDataTrip.second;
					linkScore = nelDataTrip.third;
				}
			}
		}
		
		return link;
	}

}
