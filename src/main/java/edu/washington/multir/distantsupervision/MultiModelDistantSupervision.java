package edu.washington.multir.distantsupervision;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.Triple;
import edu.washington.multir.argumentidentification.ArgumentIdentification;
import edu.washington.multir.argumentidentification.RelationMatching;
import edu.washington.multir.argumentidentification.SententialInstanceGeneration;
import edu.washington.multir.corpus.Corpus;
import edu.washington.multir.corpus.CorpusInformationSpecification.SentGlobalIDInformation.SentGlobalID;
import edu.washington.multir.data.Argument;
import edu.washington.multir.data.KBArgument;
import edu.washington.multir.data.NegativeAnnotation;
import edu.washington.multir.data.TypeSignatureRelationMap;
import edu.washington.multir.knowledgebase.KnowledgeBase;
import edu.washington.multir.util.BufferedIOUtils;
import edu.washington.multir.util.TypeConstraintUtils;

public class MultiModelDistantSupervision {
	private List<SententialInstanceGeneration> sigList;
	private List<String> outputPaths;
	private ArgumentIdentification ai;
	private RelationMatching rm;
	private NegativeExampleCollection nec;
	private List<PrintWriter> writers;
	private Boolean useNewNegativeExampleCollection;

	public MultiModelDistantSupervision(ArgumentIdentification ai, List<String> outputPaths, List<SententialInstanceGeneration> sigList, 
			RelationMatching rm, NegativeExampleCollection nec, Boolean useNewNegativeExampleCollection){
		this.sigList = sigList;
		this.ai = ai;
		this.rm =rm;
		this.nec = nec;
		this.outputPaths=outputPaths;
		this.useNewNegativeExampleCollection = useNewNegativeExampleCollection;
		
		if(outputPaths.size()!=sigList.size()){
			throw new IllegalArgumentException("Number of SentenceInstanceGeneration specifications must equal number of output paths");
		}
	}

	public void run(KnowledgeBase kb, Corpus c) throws SQLException, IOException{
    	long start = System.currentTimeMillis();
		//PrintWriter dsWriter = new PrintWriter(new FileWriter(new File(outputFileName)));
    	
    	writers = new ArrayList<PrintWriter>();
    	for(int j =0; j < outputPaths.size(); j++){
    	  writers.add(new PrintWriter(BufferedIOUtils.getBufferedWriter(new File(outputPaths.get(j)))));
    	}
		Iterator<Annotation> di = c.getDocumentIterator();
		int count =0;
		long startms = System.currentTimeMillis();
		while(di.hasNext()){
			Annotation d = di.next();
			List<CoreMap> sentences = d.get(CoreAnnotations.SentencesAnnotation.class);
			List<List<Argument>> argumentList = new ArrayList<>();
			for(CoreMap sentence : sentences){
			  argumentList.add(ai.identifyArguments(d, sentence));
			}
			for(int j =0; j < sigList.size(); j++){
				SententialInstanceGeneration sig = sigList.get(j);
		    	PrintWriter dsWriter = writers.get(j);
				List<NegativeAnnotation> documentNegativeExamples = new ArrayList<>();
				List<Pair<Triple<KBArgument,KBArgument,String>,Integer>> documentPositiveExamples = new ArrayList<>();
				int sentIndex = 0;
				for(CoreMap sentence : sentences){
					int sentGlobalID = sentence.get(SentGlobalID.class);
					
					//argument identification
					List<Argument> arguments =  argumentList.get(sentIndex);
					//sentential instance generation
					List<Pair<Argument,Argument>> sententialInstances = sig.generateSententialInstances(arguments, sentence);
					//relation matching
					List<Triple<KBArgument,KBArgument,String>> distantSupervisionAnnotations = 
							rm.matchRelations(sententialInstances,kb,sentence,d);
					//adding sentence IDs
					List<Pair<Triple<KBArgument,KBArgument,String>,Integer>> dsAnnotationWithSentIDs = new ArrayList<>();
					for(Triple<KBArgument,KBArgument,String> trip : distantSupervisionAnnotations){
						Integer i = new Integer(sentGlobalID);
						Pair<Triple<KBArgument,KBArgument,String>,Integer> p = new Pair<>(trip,i);
						dsAnnotationWithSentIDs.add(p);
					}
					//negative example annotations
					List<NegativeAnnotation> negativeExampleAnnotations = null;
					if(useNewNegativeExampleCollection){
					  negativeExampleAnnotations =
							  findTrueNegativeExampleAnnotations(sententialInstances,distantSupervisionAnnotations,
									  kb,sentGlobalID, sentence, d, true);
					}
					else{
					  negativeExampleAnnotations =
							  findNegativeExampleAnnotations(sententialInstances,distantSupervisionAnnotations,
									  kb,sentGlobalID, sentence, d, true);
					}
					
					documentNegativeExamples.addAll(negativeExampleAnnotations);
					documentPositiveExamples.addAll(dsAnnotationWithSentIDs);
					sentIndex++;
				}
				DistantSupervision.writeDistantSupervisionAnnotations(documentPositiveExamples,dsWriter);
				DistantSupervision.writeNegativeExampleAnnotations(nec.filter(documentNegativeExamples,documentPositiveExamples,kb,sentences),dsWriter);
				count++;
				if( count % 1000 == 0){
					long endms = System.currentTimeMillis();
					System.out.println(count + " documents processed");
					System.out.println("Time took = " + (endms-startms));
				}
			}
		}
		
		for(int j =0; j < writers.size(); j++){
			writers.get(j).close();
		}
    	long end = System.currentTimeMillis();
    	System.out.println("Distant Supervision took " + (end-start) + " millisseconds");
	}
	
	private  List<NegativeAnnotation> findNegativeExampleAnnotations(
			List<Pair<Argument, Argument>> sententialInstances,
			List<Triple<KBArgument, KBArgument, String>> distantSupervisionAnnotations,
			KnowledgeBase KB, Integer sentGlobalID, CoreMap sentence, Annotation doc, boolean useTypeConstraints) {
		
		Map<String,List<String>> entityMap = KB.getEntityMap();
		List<NegativeAnnotation> negativeExampleAnnotations = new ArrayList<>();
		
		
		String arg1Type = "OTHER";
		String arg2Type = "OTHER";
		if(useTypeConstraints){
			if(sententialInstances.size() > 0){
				arg1Type = TypeConstraintUtils.getFigerOrNERType(sententialInstances.get(0).first,sentence,doc);
				arg2Type = TypeConstraintUtils.getFigerOrNERType(sententialInstances.get(0).second,sentence,doc);		
			}
		}
		
		
		for(Pair<Argument,Argument> p : sententialInstances){
			//check that at least one argument is not in distantSupervisionAnnotations
			Argument arg1 = p.first;
			Argument arg2 = p.second;
			boolean canBeNegativeExample = true;
			for(Triple<KBArgument,KBArgument,String> t : distantSupervisionAnnotations){
				Argument annotatedArg1 = t.first;
				Argument annotatedArg2 = t.second;
				
				//if sententialInstance is a distance supervision annotation
				//then it is not a negative example candidate
				if( (arg1.getStartOffset() == annotatedArg1.getStartOffset()) &&
					(arg1.getEndOffset() == annotatedArg1.getEndOffset()) &&
					(arg2.getStartOffset() == annotatedArg2.getStartOffset()) &&
					(arg2.getEndOffset() == annotatedArg2.getEndOffset())){
					canBeNegativeExample = false;
					break;
				}
			}
			if(canBeNegativeExample){
				//look for KBIDs, select a random pair
				List<String> arg1Ids = new ArrayList<>();
				if(arg1 instanceof KBArgument){
					   arg1Ids.add(((KBArgument) arg1).getKbId());
				}
				else{
					if(entityMap.containsKey(arg1.getArgName())){
						List<String> candidateArg1Ids = entityMap.get(arg1.getArgName());
						if(useTypeConstraints){
							boolean add = true;
							for(String candidateArg1Id : candidateArg1Ids){
								if(!TypeConstraintUtils.meetsArgTypeConstraint(candidateArg1Id, arg1Type)){
									add = false;
								}
							}
							if(add){
								arg1Ids = candidateArg1Ids;
							}
						}
						else{
							arg1Ids = candidateArg1Ids;
						}
					}
				}

				List<String> arg2Ids = new ArrayList<>();
				if(arg2 instanceof KBArgument){
					arg2Ids.add(((KBArgument) arg2).getKbId());
				}
				else{
					if(entityMap.containsKey(arg2.getArgName())){
						List<String> candidateArg2Ids = entityMap.get(arg2.getArgName());
						if(useTypeConstraints){
							boolean add = true;
							for(String candidateArg2Id : candidateArg2Ids){
								if(!TypeConstraintUtils.meetsArgTypeConstraint(candidateArg2Id, arg2Type)){
									add = false;
								}
							}
							if(add){
								arg2Ids = candidateArg2Ids;
							}
						}
						else{
							arg2Ids = candidateArg2Ids;
						}
						
						
					}
				}
				if( (!arg1Ids.isEmpty()) && (!arg2Ids.isEmpty())){
					//check that no pair of entities represented by these
					//argument share a relation:
					if(KB.noRelationsHold(arg1Ids,arg2Ids)){
						String arg1Id = arg1Ids.get(0);
						String arg2Id = arg2Ids.get(0);
						if((!arg1Id.equals("null")) && (!arg2Id.equals("null"))){
							KBArgument kbarg1 = new KBArgument(arg1,arg1Id);
							KBArgument kbarg2 = new KBArgument(arg2,arg2Id);
							List<String> annoRels = new ArrayList<String>();
							annoRels.add("NA");
							if(annoRels.size()>0){
								NegativeAnnotation negAnno = new NegativeAnnotation(kbarg1,kbarg2,sentGlobalID,annoRels);
								negativeExampleAnnotations.add(negAnno);
							}
						}
					}
				}
			}
		}
		return negativeExampleAnnotations;
	}

	private  List<NegativeAnnotation> findTrueNegativeExampleAnnotations(
			List<Pair<Argument, Argument>> sententialInstances,
			List<Triple<KBArgument, KBArgument, String>> distantSupervisionAnnotations,
			KnowledgeBase KB, Integer sentGlobalID, CoreMap sentence, Annotation doc, boolean useTypeConstraints) {
		
		Map<String,List<String>> entityMap = KB.getEntityMap();
		List<NegativeAnnotation> negativeExampleAnnotations = new ArrayList<>();
		
		
		String arg1Type = "OTHER";
		String arg2Type = "OTHER";
		if(useTypeConstraints){
			if(sententialInstances.size() > 0){
				arg1Type = TypeConstraintUtils.getFigerOrNERType(sententialInstances.get(0).first,sentence,doc);
				arg2Type = TypeConstraintUtils.getFigerOrNERType(sententialInstances.get(0).second,sentence,doc);		
			}
		}
		
		List<String> targetRelations = TypeSignatureRelationMap.getRelationsForTypeSignature(new Pair<String,String>(arg1Type,arg2Type));
		if(targetRelations != null){
			
			for(Pair<Argument,Argument> p : sententialInstances){
				//check that at least one argument is not in distantSupervisionAnnotations
				Argument arg1 = p.first;
				Argument arg2 = p.second;
				boolean canBeNegativeExample = true;
				for(Triple<KBArgument,KBArgument,String> t : distantSupervisionAnnotations){
					Argument annotatedArg1 = t.first;
					Argument annotatedArg2 = t.second;
					
					//if sententialInstance is a distance supervision annotation
					//then it is not a negative example candidate
					if( (arg1.getStartOffset() == annotatedArg1.getStartOffset()) &&
						(arg1.getEndOffset() == annotatedArg1.getEndOffset()) &&
						(arg2.getStartOffset() == annotatedArg2.getStartOffset()) &&
						(arg2.getEndOffset() == annotatedArg2.getEndOffset())){
						canBeNegativeExample = false;
						break;
					}
				}
				if(canBeNegativeExample){
					//look for KBIDs, select a random pair
					List<String> arg1Ids = new ArrayList<>();
					if(arg1 instanceof KBArgument){
						   arg1Ids.add(((KBArgument) arg1).getKbId());
					}
					else{
						if(entityMap.containsKey(arg1.getArgName())){
							List<String> candidateArg1Ids = entityMap.get(arg1.getArgName());
							if(useTypeConstraints){
								boolean add = true;
								for(String candidateArg1Id : candidateArg1Ids){
									if(!TypeConstraintUtils.meetsArgTypeConstraint(candidateArg1Id, arg1Type)){
										add = false;
									}
								}
								if(add){
									arg1Ids = candidateArg1Ids;
								}
							}
							else{
								arg1Ids = candidateArg1Ids;
							}
						}
					}
	
					List<String> arg2Ids = new ArrayList<>();
					if(arg2 instanceof KBArgument){
						arg2Ids.add(((KBArgument) arg2).getKbId());
					}
					else{
						if(entityMap.containsKey(arg2.getArgName())){
							List<String> candidateArg2Ids = entityMap.get(arg2.getArgName());
							if(useTypeConstraints){
								boolean add = true;
								for(String candidateArg2Id : candidateArg2Ids){
									if(!TypeConstraintUtils.meetsArgTypeConstraint(candidateArg2Id, arg2Type)){
										add = false;
									}
								}
								if(add){
									arg2Ids = candidateArg2Ids;
								}
							}
							else{
								arg2Ids = candidateArg2Ids;
							}
							
							
						}
					}
					if( (!arg1Ids.isEmpty()) && (!arg2Ids.isEmpty())){
						//check that no pair of entities represented by these
						//argument share a relation:
						List<String> trueNegativeRelations= KB.getTrueNegativeRelations(arg1Ids,arg2Ids,arg2Type,KB,targetRelations);
						String arg1Id = arg1Ids.get(0);
						String arg2Id = arg2Ids.get(0);
						if((!arg1Id.equals("null")) && (!arg2Id.equals("null"))){
							KBArgument kbarg1 = new KBArgument(arg1,arg1Id);
							KBArgument kbarg2 = new KBArgument(arg2,arg2Id);
							List<String> annoRels = new ArrayList<String>();
							for(String relation: trueNegativeRelations){
								annoRels.add("N-"+relation);
							}
							if(annoRels.size()>0){
								NegativeAnnotation negAnno = new NegativeAnnotation(kbarg1,kbarg2,sentGlobalID,annoRels);
								negativeExampleAnnotations.add(negAnno);
							}
						}
					}
				}
			}
		}
		return negativeExampleAnnotations;
	}	
	
	
		
	protected static boolean containsNegativeAnnotation(
			List<Pair<Triple<KBArgument, KBArgument, String>,Integer>> negativeExampleAnnotations,
			Triple<KBArgument, KBArgument, String> t) {
		for(Pair<Triple<KBArgument,KBArgument,String>,Integer> p : negativeExampleAnnotations){
			Triple<KBArgument,KBArgument,String> trip = p.first;
			if( (trip.first.getStartOffset() == t.first.getStartOffset()) &&
				(trip.first.getEndOffset() == t.first.getEndOffset()) &&
				(trip.second.getStartOffset() == t.second.getStartOffset()) &&
				(trip.second.getEndOffset() == t.second.getEndOffset()) ){
				return true;
			}
		}	
		return false;
	}
	
	public static class DistantSupervisionAnnotation{
		KBArgument arg1;
		KBArgument arg2;
		String rel;
		Integer sentID;
	}
}