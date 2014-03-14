package edu.washington.multir.distantsupervision;

import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.Triple;
import edu.washington.multir.data.KBArgument;
import edu.washington.multir.data.NegativeAnnotation;
import edu.washington.multir.knowledgebase.KnowledgeBase;

public class NegativeExampleCollectionByRatioAndByType extends NegativeExampleCollection{

	private NegativeExampleCollectionByRatio necbr;
	private NegativeExampleCollectionByType necbt;
	
	public static NegativeExampleCollection getInstance(double ratio) {
		NegativeExampleCollectionByRatioAndByType nec = new NegativeExampleCollectionByRatioAndByType();
		nec.negativeToPositiveRatio = ratio;
		nec.necbr = new NegativeExampleCollectionByRatio();
		nec.necbr.negativeToPositiveRatio = ratio;
		nec.necbt = new NegativeExampleCollectionByType();
		nec.necbt.negativeToPositiveRatio = ratio;
		return nec;
	}
	
	@Override
	public List<NegativeAnnotation> filter(
			List<NegativeAnnotation> negativeExamples,
			List<Pair<Triple<KBArgument, KBArgument, String>, Integer>> positiveExamples,
			KnowledgeBase kb, List<CoreMap> sentences) {
		List<NegativeAnnotation> combinedNegativeExamples = new ArrayList<>();
		combinedNegativeExamples.addAll(necbt.filter(negativeExamples, positiveExamples, kb, sentences));
		List<NegativeAnnotation> combinedNegativeExamplesByType = necbr.filter(negativeExamples, positiveExamples, kb, sentences);
		for(NegativeAnnotation anno : combinedNegativeExamplesByType){
			List<String> negRels = anno.getNegativeRelations();
			for(String negRel: negRels){
				Triple<KBArgument,KBArgument,String> trip = new Triple<>(anno.getArg1(),anno.getArg2(),negRel);
				if(!DistantSupervision.containsNegativeAnnotation(combinedNegativeExamples, trip)){
					combinedNegativeExamples.add(anno);
				}
			}
		}
		return combinedNegativeExamples;
	}

}
