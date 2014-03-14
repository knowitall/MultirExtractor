package edu.washington.multir.util;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.Triple;
import edu.washington.multir.corpus.DefaultCorpusInformationSpecification.TokenOffsetInformation.SentenceRelativeCharacterOffsetEndAnnotation;
import edu.washington.multir.corpus.SentFreebaseNotableTypeInformation.FreebaseNotableTypeAnnotation;
import edu.washington.multir.data.Argument;
import edu.washington.multir.data.KBArgument;

public class TypeConstraintUtils {
	
	private static Map<String,Set<String>> argIdToFigerTypeMap = new HashMap<>();
	
	public static String getNERType(Argument arg, List<CoreLabel> tokens){
		for(CoreLabel tok : tokens){
			Integer endOffset = tok.get(SentenceRelativeCharacterOffsetEndAnnotation.class);
			if(endOffset.equals(arg.getEndOffset())){
				return tok.get(CoreAnnotations.NamedEntityTagAnnotation.class);
			}
		}
		return null;
	}
	
	public static  String getFigerType(KBArgument kbarg, 
			List<Triple<Pair<Integer,Integer>,String,String>> notableTypeData, 
			List<CoreLabel> tokens){
		Set<String> figerTypes = FigerTypeUtils.getFigerTypes(kbarg,notableTypeData,tokens);
		String longestString = "O";
		for(String ftype: figerTypes){
			if(ftype.length() > longestString.length()){
				longestString = ftype;
			}
		}
		return longestString;
	}
	
	
	public static String translateFigerTypeToTypeString(String figerType){
		if(figerType.startsWith("/person")){
			return GeneralType.PERSON;
		}
		if(figerType.startsWith("/organization")){
			return GeneralType.ORGANIZATION;
		}
		if(figerType.startsWith("/location")){
			return GeneralType.LOCATION;
		}
		return GeneralType.OTHER;
	}
	
	public static String translateNERTypeToTypeString(String nerType){
		
		if(nerType.equals(GeneralType.PERSON) || nerType.equals(GeneralType.LOCATION) || nerType.equals(GeneralType.ORGANIZATION)){
			return nerType;
		}
		else{
			return GeneralType.OTHER;
		}
	}

	public static String getFigerOrNERType(Argument first, CoreMap sentence,
			Annotation doc) {
		String type = GeneralType.OTHER;
		List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
		if(first instanceof KBArgument){
			List<Triple<Pair<Integer,Integer>,String,String>> notableTypeData = sentence.get(FreebaseNotableTypeAnnotation.class);
			return translateFigerTypeToTypeString(getFigerType((KBArgument)first,notableTypeData,tokens));
		}
		else{
			return translateNERTypeToTypeString(getNERType(first,tokens));
		}
	}
	
	//query type databse for types of candidat argID and make sure they are compatible
	//with the signature provided in the SententialInstances
	public static boolean meetsArgTypeConstraint(String argID, String argType) {
		
		if(argIdToFigerTypeMap.size() == 1000000){
			argIdToFigerTypeMap = new HashMap<>();
		}
		
		Set<String> figerTypes =  new HashSet<String>();
		if(argIdToFigerTypeMap.containsKey(argID)){
			figerTypes = argIdToFigerTypeMap.get(argID);
		}
		else{
		    figerTypes = FigerTypeUtils.getFigerTypesFromID(GuidMidConversion.convertBackward(argID));
		    argIdToFigerTypeMap.put(argID, figerTypes);
		}
		Set<String> generalTypes = new HashSet<String>();
		for(String fType: figerTypes){
			generalTypes.add(translateFigerTypeToTypeString(fType));
		}
		
		if(!argType.equals(GeneralType.OTHER)){			
			for(String gType : generalTypes){
				if(gType.equals(argType)){
					return true;
				}
			}
			return false;
		}
		
		else{
			if(generalTypes.size() > 0){
				for(String gType: generalTypes){
					if(!gType.equals(GeneralType.OTHER)){
						return false;
					}
				}
				return true;
			}
			else{
				return false;
			}
		}
	}
	
	public static class GeneralType{
		public static final String ORGANIZATION = "ORGANIZATION";
		public static final String PERSON = "PERSON";
		public static final String LOCATION = "LOCATION";
		public static final String OTHER = "OTHER";
	}
	
}
