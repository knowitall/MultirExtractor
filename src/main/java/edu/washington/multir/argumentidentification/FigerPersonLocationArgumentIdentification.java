package edu.washington.multir.argumentidentification;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.Triple;
import edu.washington.multir.corpus.SentFreebaseNotableTypeInformation.FreebaseNotableTypeAnnotation;
import edu.washington.multir.data.Argument;
import edu.washington.multir.data.KBArgument;
import edu.washington.multir.util.FigerTypeUtils;

public class FigerPersonLocationArgumentIdentification implements
		ArgumentIdentification {

	private static FigerPersonLocationArgumentIdentification instance = null;
	
	private FigerPersonLocationArgumentIdentification(){}
	public static FigerPersonLocationArgumentIdentification getInstance(){
		if(instance == null) instance = new FigerPersonLocationArgumentIdentification();
		return instance;
		}
	
	
	private static List<String> validFigerTypes;
	
	static{
		validFigerTypes = new ArrayList<String>();
		validFigerTypes.add("/person");
		validFigerTypes.add("/location");
	}
	@Override
	public List<Argument> identifyArguments(Annotation d, CoreMap s) {
		List<Argument> figerArguments = FigerTypeArgumentIdentification.getInstance().identifyArguments(d, s);
		List<Argument> args = new ArrayList<Argument>();
		
		List<Triple<Pair<Integer,Integer>,String,String>> notableTypeData = s.get(FreebaseNotableTypeAnnotation.class);
		List<CoreLabel> tokens = s.get(CoreAnnotations.TokensAnnotation.class);
		
		for(Argument figerArgument: figerArguments){
			if(figerArgument instanceof KBArgument){
				KBArgument kbarg = (KBArgument)figerArgument;
				Set<String> figerTypes = FigerTypeUtils.getFigerTypes(kbarg, notableTypeData, tokens);
				for(String figerType:figerTypes){
					if(validFigerTypes.contains(figerType)){
						args.add(figerArgument);
					}
				}
			}
		}
		
		
		return args;
	}

}
