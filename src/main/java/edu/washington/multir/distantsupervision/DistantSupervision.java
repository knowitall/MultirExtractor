package edu.washington.multir.distantsupervision;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.Triple;
import edu.washington.multir.argumentidentification.ArgumentIdentification;
import edu.washington.multir.argumentidentification.RelationMatching;
import edu.washington.multir.argumentidentification.SententialInstanceGeneration;
import edu.washington.multir.corpus.Corpus;
import edu.washington.multir.corpus.CorpusInformationSpecification.SentDocNameInformation;
import edu.washington.multir.corpus.CorpusInformationSpecification.SentDocNameInformation.SentDocName;
import edu.washington.multir.corpus.CorpusInformationSpecification.SentGlobalIDInformation.SentGlobalID;
import edu.washington.multir.data.Argument;
import edu.washington.multir.data.KBArgument;
import edu.washington.multir.data.NegativeAnnotation;
import edu.washington.multir.knowledgebase.KnowledgeBase;
import edu.washington.multir.util.BufferedIOUtils;
import edu.washington.multir.util.FigerTypeUtils;

public class DistantSupervision {

	private ArgumentIdentification ai;
	private SententialInstanceGeneration sig;
	private RelationMatching rm;
	private NegativeExampleCollection nec;

	public DistantSupervision(ArgumentIdentification ai, SententialInstanceGeneration sig, RelationMatching rm, NegativeExampleCollection nec){
		this.ai = ai;
		this.sig = sig;
		this.rm =rm;
		this.nec = nec;
	}

	public void run(String outputFileName,KnowledgeBase kb, Corpus c) throws SQLException, IOException{
    	long start = System.currentTimeMillis();
		//PrintWriter dsWriter = new PrintWriter(new FileWriter(new File(outputFileName)));
    	PrintWriter dsWriter = new PrintWriter(BufferedIOUtils.getBufferedWriter(new File(outputFileName)));
		Iterator<Annotation> di = c.getDocumentIterator();
		int count =0;
		long startms = System.currentTimeMillis();
		long timeSpentInQueries = 0;
		while(di.hasNext()){
			Annotation d = di.next();
			List<CoreMap> sentences = d.get(CoreAnnotations.SentencesAnnotation.class);

			List<NegativeAnnotation> documentNegativeExamples = new ArrayList<>();
			List<Pair<Triple<KBArgument,KBArgument,String>,Integer>> documentPositiveExamples = new ArrayList<>();
			for(CoreMap sentence : sentences){
				int sentGlobalID = sentence.get(SentGlobalID.class);
				
				//argument identification
				List<Argument> arguments =  ai.identifyArguments(d,sentence);
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
				List<NegativeAnnotation> negativeExampleAnnotations =
						findNegativeExampleAnnotations(sententialInstances,distantSupervisionAnnotations,
								kb,sentGlobalID);
				
				documentNegativeExamples.addAll(negativeExampleAnnotations);
				documentPositiveExamples.addAll(dsAnnotationWithSentIDs);				
			}
			writeDistantSupervisionAnnotations(documentPositiveExamples,dsWriter);
			writeNegativeExampleAnnotations(nec.filter(documentNegativeExamples,documentPositiveExamples,kb,sentences),dsWriter);
			count++;
			if( count % 1000 == 0){
				long endms = System.currentTimeMillis();
				System.out.println(count + " documents processed");
				System.out.println("Time took = " + (endms-startms));
				startms = endms;
				System.out.println("Time spent in querying db = " + timeSpentInQueries);
				timeSpentInQueries = 0;
//				System.out.println("query counts = " + PersonCountrySententialInstanceGeneration.queryCount );
//				PersonCountrySententialInstanceGeneration.queryCount = 0;
				
			}
		}
		dsWriter.close();
    	long end = System.currentTimeMillis();
    	System.out.println("Distant Supervision took " + (end-start) + " millisseconds");
	}
	
//	public void runTypeBased(String outputFileName,KnowledgeBase kb, Corpus c) throws SQLException, IOException{
//    	long start = System.currentTimeMillis();
//		//PrintWriter dsWriter = new PrintWriter(new FileWriter(new File(outputFileName)));
//    	PrintWriter dsWriter = new PrintWriter(BufferedIOUtils.getBufferedWriter(new File(outputFileName)));
//		Iterator<Annotation> di = c.getDocumentIterator();
//		int count =0;
//		while(di.hasNext()){
//			Annotation d = di.next();
//			Map<Pair<String,String>,Integer> typeCount = new HashMap<Pair<String,String>,Integer>();
//			List<Pair<Triple<KBArgument,KBArgument,String>,Integer>> documentNegativeExamples = new ArrayList<>();
//			List<CoreMap> sentences = d.get(CoreAnnotations.SentencesAnnotation.class);
//			for(CoreMap sentence : sentences){
//				int sentGlobalID = sentence.get(SentGlobalID.class);
//				
//				//argument identification
//				List<Argument> arguments =  ai.identifyArguments(d,sentence);
//				//sentential instance generation
//				List<Pair<Argument,Argument>> sententialInstances = sig.generateSententialInstances(arguments, sentence);
//				//relation matching
//				List<Triple<KBArgument,KBArgument,String>> distantSupervisionAnnotations = 
//						rm.matchRelations(sententialInstances,kb);
//				distantSupervisionAnnotationCount += distantSupervisionAnnotations.size();
//				//adding sentence IDs
//				List<Pair<Triple<KBArgument,KBArgument,String>,Integer>> dsAnnotationWithSentIDs = new ArrayList<>();
//				for(Triple<KBArgument,KBArgument,String> trip : distantSupervisionAnnotations){
//					Integer i = new Integer(sentGlobalID);
//					Pair<Triple<KBArgument,KBArgument,String>,Integer> p = new Pair<>(trip,i);
//					dsAnnotationWithSentIDs.add(p);
//				}
//				
//				updateTypeCount(distantSupervisionAnnotations,typeCount,sentence);
//				List<Pair<Triple<KBArgument,KBArgument,String>,Integer>> negativeExampleAnnotations =
//						findNegativeExampleAnnotationsFromDoc(sententialInstances,distantSupervisionAnnotations,
//								kb,sentGlobalID);
//				documentNegativeExamples.addAll(negativeExampleAnnotations);
//				//writeArguments(arguments,argumentWriter);
//				writeDistantSupervisionAnnotations(dsAnnotationWithSentIDs,dsWriter);
//			}
//			//negative example annotations
//			Collections.shuffle(documentNegativeExamples);
//			writeDistantSupervisionAnnotations(filterDistantSupervisionsByTypeCount(documentNegativeExamples,typeCount,sentences),dsWriter);
//			count++;
//			if( count % 1000 == 0){
//				System.out.println(count + " documents processed");
//			}
//		}
////		Collections.shuffle(globalNegativeExampleAnnotations);
////		writeDistantSupervisionAnnotations(globalNegativeExampleAnnotations.subList(0,(int)Math.floor(positiveToNegativeRatio*distantSupervisionAnnotationCount)),dsWriter);
//		
//		dsWriter.close();
//    	long end = System.currentTimeMillis();
//    	System.out.println("Distant Supervision took " + (end-start) + " millisseconds");
//	}
	
	

	



//	private  List<Pair<Triple<KBArgument, KBArgument, String>,Integer>> findNegativeExampleAnnotations(
//			List<Pair<Argument, Argument>> sententialInstances,
//			List<Triple<KBArgument, KBArgument, String>> distantSupervisionAnnotations,
//			KnowledgeBase KB, Integer sentGlobalID) {
//		
//		Map<String,List<String>> entityMap = KB.getEntityMap();
//		Map<String,List<String>> relationMap = KB.getEntityPairRelationMap();
//		List<Pair<Triple<KBArgument,KBArgument,String>,Integer>> negativeExampleAnnotations = new ArrayList<>();
//		if(negativeExampleFlag){			
//			for(Pair<Argument,Argument> p : sententialInstances){
//				//check that at least one argument is not in distantSupervisionAnnotations
//				Argument arg1 = p.first;
//				Argument arg2 = p.second;
//				boolean canBeNegativeExample = true;
//				for(Triple<KBArgument,KBArgument,String> t : distantSupervisionAnnotations){
//					Argument annotatedArg1 = t.first;
//					Argument annotatedArg2 = t.second;
//					
//					//if sententialInstance is a distance supervision annotation
//					//then it is not a negative example candidate
//					if( (arg1.getStartOffset() == annotatedArg1.getStartOffset()) &&
//						(arg1.getEndOffset() == annotatedArg1.getEndOffset()) &&
//						(arg2.getStartOffset() == annotatedArg2.getStartOffset()) &&
//						(arg2.getEndOffset() == annotatedArg2.getEndOffset())){
//						canBeNegativeExample = false;
//						break;
//					}
//				}
//				if(canBeNegativeExample){
//					//look for KBIDs, select a random pair
//					List<String> arg1Ids = new ArrayList<>();
//					if(arg1 instanceof KBArgument){
//						   arg1Ids.add(((KBArgument) arg1).getKbId());
//					}
//					else{
//						if(entityMap.containsKey(arg1.getArgName())){
//							arg1Ids = entityMap.get(arg1.getArgName());
//						}						
//					}
//
//					List<String> arg2Ids = new ArrayList<>();
//					if(arg2 instanceof KBArgument){
//						arg2Ids.add(((KBArgument) arg2).getKbId());
//					}
//					else{
//						if(entityMap.containsKey(arg2.getArgName())){
//							arg2Ids = entityMap.get(arg2.getArgName());
//						}
//					}
//					if( (!arg1Ids.isEmpty()) && (!arg2Ids.isEmpty())){
//						//check that no pair of entities represented by these
//						//argument share a relation:
//						if(noRelationsHold(arg1Ids,arg2Ids,relationMap)){
//							Collections.shuffle(arg1Ids);
//							Collections.shuffle(arg2Ids);
//							String arg1Id = arg1Ids.get(0);
//							String arg2Id = arg2Ids.get(0);
//							if((!arg1Id.equals("null")) && (!arg2Id.equals("null"))){
//								KBArgument kbarg1 = new KBArgument(arg1,arg1Id);
//								KBArgument kbarg2 = new KBArgument(arg2,arg2Id);
//								Triple<KBArgument,KBArgument,String> t = new Triple<>(kbarg1,kbarg2,"NA");
//								Pair<Triple<KBArgument,KBArgument,String>,Integer> negativeAnnotationPair = new Pair<>(t,sentGlobalID);
//								if(!containsNegativeAnnotation(negativeExampleAnnotations,t)) negativeExampleAnnotations.add(negativeAnnotationPair);
//							}
//						}
//					}
//				}
//			}
//		}
//		//Collections.shuffle(negativeExampleAnnotations);
//		globalNegativeExampleAnnotations.addAll(negativeExampleAnnotations);
//		if(globalNegativeExampleAnnotations.size() > NEGATIVE_EXAMPLE_FLUSH_CONSTANT){
//			negativeExampleAnnotations = globalNegativeExampleAnnotations;
//			Collections.shuffle(negativeExampleAnnotations);
//			int oldCount = distantSupervisionAnnotationCount;
//			distantSupervisionAnnotationCount =0;
//			globalNegativeExampleAnnotations = new ArrayList<>();
//			int toIndex = Math.min(negativeExampleAnnotations.size(), (int)Math.floor(positiveToNegativeRatio*oldCount));
//			return negativeExampleAnnotations.subList(0,toIndex);
//		}
//		else{
//			return new ArrayList<>();
//		}
//	}
	
	public static void writeNegativeExampleAnnotations(
			List<NegativeAnnotation> filter, PrintWriter dsWriter) {
		
		List<Pair<Triple<KBArgument,KBArgument,String>,Integer>> dsFormatList = new ArrayList<>();
		for(NegativeAnnotation negAnno: filter){
			List<String> rels = negAnno.getNegativeRelations();
			for(String negRel: rels){
				Triple<KBArgument,KBArgument,String> trip = new Triple<>(negAnno.getArg1(),negAnno.getArg2(),negRel);
				Pair<Triple<KBArgument,KBArgument,String>,Integer> p = new Pair<>(trip,negAnno.getSentNum());
				dsFormatList.add(p);
			}
		}
		
		writeDistantSupervisionAnnotations(dsFormatList, dsWriter);

		
	}

	private  List<NegativeAnnotation> findNegativeExampleAnnotations(
			List<Pair<Argument, Argument>> sententialInstances,
			List<Triple<KBArgument, KBArgument, String>> distantSupervisionAnnotations,
			KnowledgeBase KB, Integer sentGlobalID) {
		
		Map<String,List<String>> entityMap = KB.getEntityMap();
		List<NegativeAnnotation> negativeExampleAnnotations = new ArrayList<>();
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
						arg1Ids = entityMap.get(arg1.getArgName());
					}						
				}

				List<String> arg2Ids = new ArrayList<>();
				if(arg2 instanceof KBArgument){
					arg2Ids.add(((KBArgument) arg2).getKbId());
				}
				else{
					if(entityMap.containsKey(arg2.getArgName())){
						arg2Ids = entityMap.get(arg2.getArgName());
					}
				}
				if( (!arg1Ids.isEmpty()) && (!arg2Ids.isEmpty())){
					//check that no pair of entities represented by these
					//argument share a relation:
					if(KB.noRelationsHold(arg1Ids,arg2Ids)){
						Collections.shuffle(arg1Ids);
						Collections.shuffle(arg2Ids);
						String arg1Id = arg1Ids.get(0);
						String arg2Id = arg2Ids.get(0);
						if((!arg1Id.equals("null")) && (!arg2Id.equals("null"))){
							KBArgument kbarg1 = new KBArgument(arg1,arg1Id);
							KBArgument kbarg2 = new KBArgument(arg2,arg2Id);
							Triple<KBArgument,KBArgument,String> t = new Triple<>(kbarg1,kbarg2,"NA");
							Pair<Triple<KBArgument,KBArgument,String>,Integer> negativeAnnotationPair = new Pair<>(t,sentGlobalID);
							List<String> annoRels = new ArrayList<String>();
							annoRels.add(t.third);
							NegativeAnnotation negAnno = new NegativeAnnotation(t.first,t.second,sentGlobalID,annoRels);
							if(!containsNegativeAnnotation(negativeExampleAnnotations,t)) negativeExampleAnnotations.add(negAnno);
						}
					}
				}
			}
		}
		return negativeExampleAnnotations;
	}
	
	
	protected static boolean containsNegativeAnnotation(
			List<NegativeAnnotation> negativeExampleAnnotations,
			Triple<KBArgument, KBArgument, String> t) {
		for(NegativeAnnotation anno : negativeExampleAnnotations){
			KBArgument annoArg1 = anno.getArg1();
			KBArgument annoArg2 = anno.getArg2();

			if( (annoArg1.getStartOffset() == t.first.getStartOffset()) &&
				(annoArg1.getEndOffset() == t.first.getEndOffset()) &&
				(annoArg2.getStartOffset() == t.second.getStartOffset()) &&
				(annoArg2.getEndOffset() == t.second.getEndOffset()) ){
				return true;
			}
		}	
		return false;
	}

	/**
	 * Write out distant supervision annotation information
	 * @param distantSupervisionAnnotations
	 * @param dsWriter
	 * @param sentGlobalID
	 */
	public static void writeDistantSupervisionAnnotations(
			List<Pair<Triple<KBArgument, KBArgument, String>,Integer>> distantSupervisionAnnotations, PrintWriter dsWriter) {
		for(Pair<Triple<KBArgument,KBArgument,String>,Integer> dsAnno : distantSupervisionAnnotations){
			Triple<KBArgument,KBArgument,String> trip = dsAnno.first;
			Integer sentGlobalID = dsAnno.second;
			KBArgument arg1 = trip.first;
			KBArgument arg2 = trip.second;
			String rel = trip.third;
			dsWriter.write(arg1.getKbId());
			dsWriter.write("\t");
			dsWriter.write(String.valueOf(arg1.getStartOffset()));
			dsWriter.write("\t");
			dsWriter.write(String.valueOf(arg1.getEndOffset()));
			dsWriter.write("\t");
			dsWriter.write(arg1.getArgName());
			dsWriter.write("\t");
			dsWriter.write(arg2.getKbId());
			dsWriter.write("\t");
			dsWriter.write(String.valueOf(arg2.getStartOffset()));
			dsWriter.write("\t");
			dsWriter.write(String.valueOf(arg2.getEndOffset()));
			dsWriter.write("\t");
			dsWriter.write(arg2.getArgName());
			dsWriter.write("\t");
			dsWriter.write(String.valueOf(sentGlobalID));
			dsWriter.write("\t");
			dsWriter.write(rel);
			dsWriter.write("\n");
		}
	}
	
	public static class DistantSupervisionAnnotation{
		KBArgument arg1;
		KBArgument arg2;
		String rel;
		Integer sentID;
	}
}
