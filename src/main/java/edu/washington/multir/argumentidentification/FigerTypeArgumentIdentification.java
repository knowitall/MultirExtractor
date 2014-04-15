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

/**
 * Implements <code>ArgumentIdentification</code> method <code>identifyArguments</code>
 * to get all arguments that have a corresponding FIGER type.
 * @author jgilme1
 *
 */
public class FigerTypeArgumentIdentification implements ArgumentIdentification {

	
	private static FigerTypeArgumentIdentification instance = null;
	
	private FigerTypeArgumentIdentification(){}
	public static FigerTypeArgumentIdentification getInstance(){
		if(instance == null) instance = new FigerTypeArgumentIdentification();
		return instance;
		}
	
	@Override
	public List<Argument> identifyArguments(Annotation d, CoreMap s) {
		List<Argument> nelArguments = NELArgumentIdentification.getInstance().identifyArguments(d, s);
		List<Argument> args = new ArrayList<>();
		List<CoreLabel> tokens = s.get(CoreAnnotations.TokensAnnotation.class);
		List<Triple<Pair<Integer,Integer>,String,String>> notableTypeData = s.get(FreebaseNotableTypeAnnotation.class);
		for(Argument a : nelArguments){
			if(a instanceof KBArgument){
				KBArgument kbarg = (KBArgument)a;
				Set<String> figerTypes = FigerTypeUtils.getFigerTypes(kbarg,notableTypeData,tokens);
				boolean add = false;
				if(figerTypes != null){
					for(String typ : figerTypes){
						if(!typ.equals("O")){
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
