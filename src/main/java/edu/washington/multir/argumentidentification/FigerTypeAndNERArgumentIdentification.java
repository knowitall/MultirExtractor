package edu.washington.multir.argumentidentification;

import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.util.CoreMap;
import edu.washington.multir.data.Argument;

/**
 * Implements <code>ArgumentIdentification</code> method <code>identifyArguments</code>
 * to get the union of all Arguments that either have a FIGER type or an NER type.
 * @author jgilme1
 *
 */
public class FigerTypeAndNERArgumentIdentification implements
		ArgumentIdentification {

	
	private static FigerTypeAndNERArgumentIdentification instance = null;
	
	private FigerTypeAndNERArgumentIdentification(){}
	public static FigerTypeAndNERArgumentIdentification getInstance(){
		if(instance == null) instance = new FigerTypeAndNERArgumentIdentification();
		return instance;
		}
	@Override
	public List<Argument> identifyArguments(Annotation d, CoreMap s) {
		List<Argument> args = new ArrayList<Argument>();
		List<Argument> figerTypeArgs = FigerTypeArgumentIdentification.getInstance().identifyArguments(d, s);
		List<Argument> nerArgs = NERArgumentIdentification.getInstance().identifyArguments(d, s);
		
		args.addAll(figerTypeArgs);
		for(Argument nerArg: nerArgs){
			if(!nerArg.intersectsWithList(figerTypeArgs)){
				args.add(nerArg);
			}
		}
		return args;
	}

}
