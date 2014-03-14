package edu.washington.multir.argumentidentification;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.Triple;
import edu.washington.multir.corpus.SentFreebaseNotableTypeInformation.FreebaseNotableTypeAnnotation;
import edu.washington.multir.data.Argument;
import edu.washington.multir.data.KBArgument;
import edu.washington.multir.util.FigerTypeUtils;

public class FigerPersonLocationSententialInstanceGeneration implements
		SententialInstanceGeneration {

	private static FigerPersonLocationSententialInstanceGeneration instance = null;
	
	public static FigerPersonLocationSententialInstanceGeneration getInstance(){
		if(instance == null){
			instance = new FigerPersonLocationSententialInstanceGeneration();
		}
		return instance;
	}
	
	
	@Override
	public List<Pair<Argument, Argument>> generateSententialInstances(
			List<Argument> arguments, CoreMap sentence) {
		
		List<Triple<Pair<Integer,Integer>,String,String>> notableTypeData = sentence.get(FreebaseNotableTypeAnnotation.class);
		List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
		List<Pair<Argument,Argument>> sententialInstances = new ArrayList<>();
		for(int i =0; i < arguments.size(); i++){
			for(int j =0; j < arguments.size(); j++){
				if(i != j){
					Argument arg1 = arguments.get(i);
					Argument arg2 = arguments.get(j);
					
					if((arg1 instanceof KBArgument) && (arg2 instanceof KBArgument)){
						KBArgument kbArg1 = (KBArgument)arg1;
						KBArgument kbArg2 = (KBArgument)arg2;
						Set<String> arg1FigerTypes = FigerTypeUtils.getFigerTypes(kbArg1,notableTypeData,tokens);
						Set<String> arg2FigerTypes = FigerTypeUtils.getFigerTypes(kbArg2,notableTypeData,tokens);
						
						boolean add = false;
						
						if(arg1FigerTypes.contains("/person")  && arg2FigerTypes.contains("/location")){
							add = true;
						}
						if(add){
							sententialInstances.add(new Pair<Argument,Argument>(arg1,arg2));
						}
					}
				}
			}
		}
		
		return sententialInstances;

	}

}
