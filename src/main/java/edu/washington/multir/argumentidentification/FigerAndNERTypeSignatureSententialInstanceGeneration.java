package edu.washington.multir.argumentidentification;

import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Interval;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.Triple;
import edu.washington.multir.corpus.SentFreebaseNotableTypeInformation.FreebaseNotableTypeAnnotation;
import edu.washington.multir.data.Argument;
import edu.washington.multir.data.KBArgument;
import edu.washington.multir.util.TypeConstraintUtils;


/**
 * Abstract class that implements <code>SententialInstanceGeneration<code>. Classes
 * that extend this class use their constructor to populate the instance variables
 * <code>arg1Type</code> and <code>arg2Type</code>.
 * This code is used to build separate models based on argument types.
 * @author jgilme1
 */
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
		//iterate over all argument pairs
		for(int i =0; i < arguments.size(); i++){
			for(int j = 0; j < arguments.size(); j++){
				if(j != i){
					Argument arg1 = arguments.get(i);
					Argument arg2 = arguments.get(j);
					Interval<Integer> arg1Interval = Interval.toInterval(arg1.getStartOffset(), arg1.getEndOffset());
					Interval<Integer> arg2Interval = Interval.toInterval(arg2.getStartOffset(), arg2.getEndOffset());
					if(arg1Interval.intersect(arg2Interval) == null){
						
						//check if links are to the same entity, if so ignore pair
						boolean makePair = true;
						if((arg1 instanceof KBArgument) && (arg2 instanceof KBArgument)){
							KBArgument kbArg1 = (KBArgument)arg1;
							KBArgument kbArg2 = (KBArgument)arg2;
							if(kbArg1.getKbId().equals(kbArg2.getKbId())){
								makePair=false;
							}
						}
						
						if(makePair){
							//deafult argument types to OTHER but search FreebaseNotableType data
							//and NER data for types
							String arg1Type = "OTHER";
							String arg2Type = "OTHER";
							if(arg1 instanceof KBArgument){
								KBArgument kbarg = (KBArgument)arg1;
								arg1Type = TypeConstraintUtils.translateFigerTypeToTypeString(TypeConstraintUtils.getFigerType(kbarg,notableTypeData,tokens));
							}
							else{
								arg1Type = TypeConstraintUtils.translateNERTypeToTypeString(TypeConstraintUtils.getNERType(arg1,tokens));

							}
							
							if(arg2 instanceof KBArgument){
								KBArgument kbarg = (KBArgument)arg2;
								arg2Type = TypeConstraintUtils.translateFigerTypeToTypeString(TypeConstraintUtils.getFigerType(kbarg,notableTypeData,tokens));

							}
							else{
								arg2Type = TypeConstraintUtils.translateNERTypeToTypeString(TypeConstraintUtils.getNERType(arg2,tokens));

							}
							
							//if types match arg1Type and arg2Type consider as a sentential instance
							if(arg1Type.equals(this.arg1Type) && arg2Type.equals(this.arg2Type)){
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
