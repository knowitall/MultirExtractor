package edu.washington.multir.argumentidentification;

import java.util.ArrayList;
import java.util.List;


import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.Triple;
import edu.washington.multir.data.Argument;
import edu.washington.multir.data.KBArgument;
import edu.washington.multir.knowledgebase.KnowledgeBase;

/**
 * Implements <code>RelationMatching</code> method <code>matchRelations</code>
 * to use the NEL links of arguments to do a quick check of any relations
 * that hold between them in the <code>KnowledgeBase</code>
 * @author jgilme1
 *
 */
public class NELRelationMatching implements RelationMatching {

	
	private static NELRelationMatching instance = null;
	
	public static NELRelationMatching getInstance(){
		if(instance == null){
			instance = new NELRelationMatching();
		}
		return instance;
	}
	
	private NELRelationMatching(){}
	
	@Override
	public List<Triple<KBArgument, KBArgument, String>> matchRelations(
			List<Pair<Argument, Argument>> sententialInstances, KnowledgeBase KB,
			CoreMap sentence, Annotation doc) {

		List<Triple<KBArgument,KBArgument,String>> dsRelations = new ArrayList<>();
				
		for(Pair<Argument,Argument> sententialInstance : sententialInstances){
			Argument arg1 = sententialInstance.first;
			Argument arg2 = sententialInstance.second;
			//if both arguments have ids in the KB
			if((arg1 instanceof KBArgument) && (arg2 instanceof KBArgument)){
				//check if they have a relation
				KBArgument kbArg1 = (KBArgument)arg1;
				KBArgument kbArg2 = (KBArgument)arg2;
				List<String> relations = KB.getRelationsBetweenArgumentIds(kbArg1.getKbId(), kbArg2.getKbId());
				for(String rel: relations){
					Triple<KBArgument,KBArgument,String> dsRelation = new Triple<>(kbArg1,kbArg2,rel);
					dsRelations.add(dsRelation);
				}
			}
		}
		return dsRelations;
	}

}
