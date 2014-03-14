package edu.washington.multir.argumentidentification;

import java.util.ArrayList;
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
import edu.washington.multir.corpus.CorpusInformationSpecification.SentGlobalIDInformation.SentGlobalID;
import edu.washington.multir.corpus.SentFreebaseNotableTypeInformation.FreebaseNotableTypeAnnotation;
import edu.washington.multir.data.Argument;
import edu.washington.multir.data.KBArgument;
import edu.washington.multir.util.FigerTypeUtils;
import edu.washington.multir.util.GuidMidConversion;

public class PersonCountryArgumentIdentification implements
		ArgumentIdentification {
	private static PersonCountryArgumentIdentification instance = null;
	
	private PersonCountryArgumentIdentification(){}
	public static PersonCountryArgumentIdentification getInstance(){
		if(instance == null) instance = new PersonCountryArgumentIdentification();
		return instance;
		}
	
	@Override
	public List<Argument> identifyArguments(Annotation d, CoreMap s) {
		System.out.println("Sentence " + s.get(SentGlobalID.class));
		List<Argument> nelArguments = NELArgumentIdentification.getInstance().identifyArguments(d, s);
		List<Argument> args = new ArrayList<>();
		System.out.println("Tokens:");
		List<CoreLabel> tokens = s.get(CoreAnnotations.TokensAnnotation.class);
		int tokCount =0;
		for(CoreLabel tok: tokens){
			System.out.println(tokCount + " " + tok.get(CoreAnnotations.TextAnnotation.class));
			tokCount++;
		}
		List<Triple<Pair<Integer,Integer>,String,String>> notableTypeData = s.get(FreebaseNotableTypeAnnotation.class);
		System.out.println("Notable type data");
		for(Triple<Pair<Integer,Integer>,String,String> trip : notableTypeData){
			System.out.println(trip.first + " " + trip.second + " " + trip.third);
		}
		for(Argument a : nelArguments){
			if(a instanceof KBArgument){
				KBArgument kbarg = (KBArgument)a;
				System.out.println("Candidate argument = " + kbarg.getArgName() + " " + kbarg.getKbId());
				Set<String> figerTypes = FigerTypeUtils.getFigerTypes(kbarg,notableTypeData,tokens);
				boolean add = false;
				if(figerTypes != null){
					for(String typ : figerTypes){
						if(typ.equals("/person") || typ.equals("/location/country")){
							System.out.println("adding argument " + a.getArgName() + " " + typ + " " + kbarg.getKbId());
							add = true;
						}
					}
					if(add){
						args.add(a);
					}
				}
			}
		}

		return args;
	}


}
