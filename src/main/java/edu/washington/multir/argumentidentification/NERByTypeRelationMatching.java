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
import edu.washington.multir.data.Argument;
import edu.washington.multir.data.KBArgument;
import edu.washington.multir.data.TypeSignatureRelationMap;
import edu.washington.multir.knowledgebase.KnowledgeBase;
import edu.washington.multir.util.TypeConstraintUtils;

/**
 * Default implementation of relation matching
 * @author jgilme1
 *
 */
public class NERByTypeRelationMatching implements RelationMatching {
	
	private static NERByTypeRelationMatching instance = null;
	
	public static NERByTypeRelationMatching getInstance(){
		if(instance == null){
			instance = new NERByTypeRelationMatching();
		}
		return instance;
	}
	private NERByTypeRelationMatching(){}

	@Override
	public List<Triple<KBArgument,KBArgument,String>> matchRelations(
			List<Pair<Argument,Argument>> sententialInstances,
			KnowledgeBase KB, CoreMap sentence, Annotation doc) {
		
		Map<String,List<String>> entityMap =KB.getEntityMap();
		List<Triple<KBArgument,KBArgument,String>> distantSupervisionAnnotations = new ArrayList<>();

		String arg1Type = "OTHER";
		String arg2Type = "OTHER";
		List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
		if(sententialInstances.size() > 0){
			arg1Type = TypeConstraintUtils.getNERType(sententialInstances.get(0).first, tokens);
			arg2Type = TypeConstraintUtils.getNERType(sententialInstances.get(0).second,tokens);		
		}
		
		List<String> typeAppropriateRelations = TypeSignatureRelationMap.getRelationsForTypeSignature(new Pair<String,String>(arg1Type,arg2Type));
		
		for(Pair<Argument,Argument> si : sententialInstances){
			Argument arg1 = si.first;
			Argument arg2 = si.second;
			String arg1Name = arg1.getArgName();
			String arg2Name = arg2.getArgName();
			Set<String> relationsFound = new HashSet<String>();
			
			if(entityMap.containsKey(arg1Name)){
				if(entityMap.containsKey(arg2Name)){
					List<String> arg1Ids = entityMap.get(arg1Name);
					List<String> arg2Ids = entityMap.get(arg2Name);
					for(String arg1Id : arg1Ids){
						for(String arg2Id: arg2Ids){
							List<String> relations = KB.getRelationsBetweenArgumentIds(arg1Id,arg2Id);
							for(String rel : relations){
								if(typeAppropriateRelations.contains(rel)){
									if(!relationsFound.contains(rel)){
										KBArgument kbarg1 = new KBArgument(arg1,arg1Id);
										KBArgument kbarg2 = new KBArgument(arg2,arg2Id);
										Triple<KBArgument,KBArgument,String> t = 
												new Triple<>(kbarg1,kbarg2,rel);
										distantSupervisionAnnotations.add(t);
										relationsFound.add(rel);
									}
								}
							}
						}
					}
				}
			}
		}
		return distantSupervisionAnnotations;
	}
}