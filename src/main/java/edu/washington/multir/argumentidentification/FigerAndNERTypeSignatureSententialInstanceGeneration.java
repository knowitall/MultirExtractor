package edu.washington.multir.argumentidentification;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Interval;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.Triple;
import edu.washington.multir.corpus.CorpusInformationSpecification.SentGlobalIDInformation.SentGlobalID;
import edu.washington.multir.corpus.DefaultCorpusInformationSpecification.TokenOffsetInformation.SentenceRelativeCharacterOffsetEndAnnotation;
import edu.washington.multir.corpus.SentFreebaseNotableTypeInformation.FreebaseNotableTypeAnnotation;
import edu.washington.multir.data.Argument;
import edu.washington.multir.data.KBArgument;
import edu.washington.multir.util.FigerTypeUtils;
import edu.washington.multir.util.TypeConstraintUtils;

public abstract class FigerAndNERTypeSignatureSententialInstanceGeneration implements
		SententialInstanceGeneration {

	private String arg1Type;
	private String arg2Type;
	public FigerAndNERTypeSignatureSententialInstanceGeneration(String arg1Type, String arg2Type){
		this.arg1Type = arg1Type;
		this.arg2Type = arg2Type;
	}
	
	@Override
	public List<Pair<Argument, Argument>> generateSententialInstances(
			List<Argument> arguments, CoreMap sentence) {
		
		List<Pair<Argument,Argument>> sententialInstances = new ArrayList<>();
		List<Triple<Pair<Integer,Integer>,String,String>> notableTypeData = sentence.get(FreebaseNotableTypeAnnotation.class);
		List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
//		System.out.println("Sentence " +sentence.get(SentGlobalID.class));
//		System.out.println("Target Arg1Type = " + this.arg1Type + " target Arg2 Type = " + this.arg2Type);
		for(int i =0; i < arguments.size(); i++){
			for(int j = 0; j < arguments.size(); j++){
				if(j != i){
					Argument arg1 = arguments.get(i);
					Argument arg2 = arguments.get(j);
					Interval<Integer> arg1Interval = Interval.toInterval(arg1.getStartOffset(), arg1.getEndOffset());
					Interval<Integer> arg2Interval = Interval.toInterval(arg2.getStartOffset(), arg2.getEndOffset());
					if(arg1Interval.intersect(arg2Interval) == null){
						
						boolean makePair = true;
						if((arg1 instanceof KBArgument) && (arg2 instanceof KBArgument)){
							KBArgument kbArg1 = (KBArgument)arg1;
							KBArgument kbArg2 = (KBArgument)arg2;
							if(kbArg1.getKbId().equals(kbArg2.getKbId())){
								makePair=false;
							}
						}
						if(makePair){

							String arg1Type = "OTHER";
							String arg2Type = "OTHER";
//							System.out.println("SI Candidate");
//							System.out.println("Argument 1 = " + arg1.getArgName() + " Argument 2 = " + arg2.getArgName());
							if(arg1 instanceof KBArgument){
								KBArgument kbarg = (KBArgument)arg1;
								arg1Type = TypeConstraintUtils.translateFigerTypeToTypeString(TypeConstraintUtils.getFigerType(kbarg,notableTypeData,tokens));
								//System.out.println("Argument 1 Type from Figer = " + arg1Type);
							}
							else{
								arg1Type = TypeConstraintUtils.translateNERTypeToTypeString(TypeConstraintUtils.getNERType(arg1,tokens));
								//System.out.println("Argument 1 Type from NER = " + arg1Type);

							}
							
							if(arg2 instanceof KBArgument){
								KBArgument kbarg = (KBArgument)arg2;
								arg2Type = TypeConstraintUtils.translateFigerTypeToTypeString(TypeConstraintUtils.getFigerType(kbarg,notableTypeData,tokens));
								//System.out.println("Argument 2 Type from Figer = " + arg2Type);

							}
							else{
								arg2Type = TypeConstraintUtils.translateNERTypeToTypeString(TypeConstraintUtils.getNERType(arg2,tokens));
								//System.out.println("Argument 2 Type from NER = " + arg2Type);

							}
							
							if(arg1Type.equals(this.arg1Type) && arg2Type.equals(this.arg2Type)){
								//System.out.println("NEW SI");
								//System.out.println("Argument 1 = " + arg1.getArgName() + " sentence " + sentence.get(SentGlobalID.class) + " " + arg1Type);
								Pair<Argument,Argument> p = new Pair<>(arg1,arg2);
								sententialInstances.add(p);
							}
						}
					}
				}
			}
		}
		return sententialInstances;

	}	
}
