package edu.washington.multir.distantsupervision;

import java.util.List;

import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.Triple;
import edu.washington.multir.data.KBArgument;
import edu.washington.multir.data.NegativeAnnotation;
import edu.washington.multir.knowledgebase.KnowledgeBase;

public abstract class NegativeExampleCollection {
		
	protected double negativeToPositiveRatio;
	
	public abstract List<NegativeAnnotation> filter(
			List<NegativeAnnotation> negativeExamples,
			List<Pair<Triple<KBArgument,KBArgument,String>,Integer>> positiveExamples,
			KnowledgeBase kb, List<CoreMap> sentences);
}
