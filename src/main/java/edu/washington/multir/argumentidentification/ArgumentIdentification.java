package edu.washington.multir.argumentidentification;

import java.util.List;

import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.util.CoreMap;
import edu.washington.multir.data.Argument;

/**
 * The interface <code>ArgumentIdentification</code> must be implemented
 * in order to run <code>DistantSupervision</code> and to extract new
 * extractions from <code>DocumentExtractor</code>
 * @author jgilme1
 *
 */
public interface ArgumentIdentification {
	
	/**
	 * 
	 * @param d
	 * @param s
	 * @return the list of <code>Argument</code> from the sentence.
	 */
	List<Argument> identifyArguments(Annotation d, CoreMap s);
}
