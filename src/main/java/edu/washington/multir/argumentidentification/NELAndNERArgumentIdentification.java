package edu.washington.multir.argumentidentification;

import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Interval;
import edu.washington.multir.data.Argument;

/**
 * Implements <code>ArgumentIdentification</code> method <code>identifyArguments</code>
 * to get the union of all Arguments that either have an NEL link or an NER type
 * @author jgilme1
 *
 */
public class NELAndNERArgumentIdentification implements ArgumentIdentification {

	private static NELAndNERArgumentIdentification instance = null;
	
	private NELAndNERArgumentIdentification(){}
	public static NELAndNERArgumentIdentification getInstance(){
		if(instance == null) instance = new NELAndNERArgumentIdentification();
		return instance;
		}
	@Override
	public List<Argument> identifyArguments(Annotation d, CoreMap s) {
		List<Argument> nerArguments = NERArgumentIdentification.getInstance().identifyArguments(d, s);
		List<Argument> nelArguments = NELArgumentIdentification.getInstance().identifyArguments(d, s);
		List<Argument> args = new ArrayList<Argument>();
		args.addAll(nelArguments);
		for(Argument nerArg : nerArguments){
			boolean intersects = false;
			for(Argument nelArg: nelArguments){
				Interval<Integer> nerArgInterval = Interval.toInterval(nerArg.getStartOffset(), nerArg.getEndOffset());
				Interval<Integer> nelArgInterval = Interval.toInterval(nelArg.getStartOffset(), nelArg.getEndOffset());
				if(nerArgInterval.intersect(nelArgInterval) !=null){
					intersects = true;
				}
			}
			if(!intersects){
				args.add(nerArg);
			}
		}
		return args;
	}

}
