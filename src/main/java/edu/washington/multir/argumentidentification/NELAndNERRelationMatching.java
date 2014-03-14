package edu.washington.multir.argumentidentification;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.Triple;
import edu.washington.multir.data.Argument;
import edu.washington.multir.data.KBArgument;
import edu.washington.multir.knowledgebase.KnowledgeBase;
import edu.washington.multir.util.FigerTypeUtils;
import edu.washington.multir.util.GuidMidConversion;
import edu.washington.multir.util.TypeConstraintUtils;

public class NELAndNERRelationMatching implements RelationMatching{


	
	private static NELAndNERRelationMatching instance = null;
	
	public static NELAndNERRelationMatching getInstance(){
		if(instance == null){
			instance = new NELAndNERRelationMatching();
		}
		return instance;
	}
	
	private NELAndNERRelationMatching(){}
		
	@Override
	public List<Triple<KBArgument, KBArgument, String>> matchRelations(
			List<Pair<Argument, Argument>> sententialInstances, KnowledgeBase KB,
			CoreMap sentence, Annotation doc) {

		List<Triple<KBArgument,KBArgument,String>> dsRelations = new ArrayList<>();

		String arg1Type = "OTHER";
		String arg2Type = "OTHER";
		if(sententialInstances.size() > 0){
			arg1Type = TypeConstraintUtils.getFigerOrNERType(sententialInstances.get(0).first,sentence,doc);
			arg2Type = TypeConstraintUtils.getFigerOrNERType(sententialInstances.get(0).second,sentence,doc);		
		}
		
		
		Map<String,List<String>> entityMap =KB.getEntityMap();
		Map<String,List<Pair<String,String>>> entityRelationMap =KB.getEntityPairRelationMap();
		
		for(Pair<Argument,Argument> sententialInstance : sententialInstances){
			Argument arg1 = sententialInstance.first;
			Argument arg2 = sententialInstance.second;
			
			
			//if both arguments have ids in the KB
			if((arg1 instanceof KBArgument) && (arg2 instanceof KBArgument)){
				//check if they have a relation
				KBArgument kbArg1 = (KBArgument)arg1;
				KBArgument kbArg2 = (KBArgument)arg2;
				List<String> relations = KB.getRelationsBetweenArgumentIds(kbArg1.getKbId(),kbArg2.getKbId());
				for(String relation : relations){
					Triple<KBArgument,KBArgument,String> dsRelation = new Triple<>(kbArg1,kbArg2,relation);
					dsRelations.add(dsRelation);
				}
			}
			
			else if((!(arg1 instanceof KBArgument)) && (!(arg2 instanceof KBArgument))){
				Set<String> relationsFound = new HashSet<String>();
				if(entityMap.containsKey(arg1.getArgName())){
					if(entityMap.containsKey(arg2.getArgName())){
						List<String> arg1Ids = entityMap.get(arg1.getArgName());
						List<String> arg2Ids = entityMap.get(arg2.getArgName());
						for(String arg1Id : arg1Ids){
							if(TypeConstraintUtils.meetsArgTypeConstraint(arg1Id,arg1Type)){
								for(String arg2Id: arg2Ids){
									if(TypeConstraintUtils.meetsArgTypeConstraint(arg2Id,arg2Type)){		
										List<String> relations = KB.getRelationsBetweenArgumentIds(arg1Id,arg2Id);
										for(String rel : relations){
											if(!relationsFound.contains(rel)){
												KBArgument kbarg1 = new KBArgument(arg1,arg1Id);
												KBArgument kbarg2 = new KBArgument(arg2,arg2Id);
												Triple<KBArgument,KBArgument,String> t = 
														new Triple<>(kbarg1,kbarg2,rel);
												dsRelations.add(t);
												relationsFound.add(rel);
											}
										}
									}
								}
							}
						}
					}
				}
			}
			
			else if((arg1 instanceof KBArgument) && (!(arg2 instanceof KBArgument))){
				Set<String> relationsFound = new HashSet<String>();
				KBArgument kbArg1 = (KBArgument)arg1;
				if(entityMap.containsKey(arg2.getArgName())){
					List<String> arg2Ids = entityMap.get(arg2.getArgName());
					for(String arg2Id: arg2Ids){
						if(TypeConstraintUtils.meetsArgTypeConstraint(arg2Id,arg2Type)){
							List<String> relations = KB.getRelationsBetweenArgumentIds(kbArg1.getKbId(),arg2Id);
							for(String rel : relations){
								if(!relationsFound.contains(rel)){
									KBArgument kbarg2 = new KBArgument(arg2,arg2Id);
									Triple<KBArgument,KBArgument,String> t = 
											new Triple<>(kbArg1,kbarg2,rel);
									dsRelations.add(t);
									relationsFound.add(rel);
								}
							}
						}
					}
				}				
			}

			else if((!(arg1 instanceof KBArgument)) && (arg2 instanceof KBArgument)){
				Set<String> relationsFound = new HashSet<String>();
				KBArgument kbArg2 = (KBArgument)arg2;
				if(entityMap.containsKey(arg1.getArgName())){
					List<String> arg1Ids = entityMap.get(arg1.getArgName());
					for(String arg1Id: arg1Ids){
						if(TypeConstraintUtils.meetsArgTypeConstraint(arg1Id,arg1Type)){
							List<String> relations = KB.getRelationsBetweenArgumentIds(arg1Id,kbArg2.getKbId());
							for(String rel : relations){
								if(!relationsFound.contains(rel)){
									KBArgument kbarg1 = new KBArgument(arg1,arg1Id);
									Triple<KBArgument,KBArgument,String> t = 
											new Triple<>(kbarg1,kbArg2,rel);
									dsRelations.add(t);
									relationsFound.add(rel);
								}
							}
						}
					}
				}				
			}
			
			
		}
		return dsRelations;
	}



}
