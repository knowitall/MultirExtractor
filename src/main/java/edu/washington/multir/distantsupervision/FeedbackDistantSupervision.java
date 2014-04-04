package edu.washington.multir.distantsupervision;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.cli.ParseException;
import org.apache.commons.lang.builder.HashCodeBuilder;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.Triple;
import edu.washington.multir.argumentidentification.ArgumentIdentification;
import edu.washington.multir.argumentidentification.NELArgumentIdentification;
import edu.washington.multir.argumentidentification.SententialInstanceGeneration;
import edu.washington.multir.corpus.Corpus;
import edu.washington.multir.corpus.CorpusInformationSpecification;
import edu.washington.multir.corpus.CorpusInformationSpecification.SentGlobalIDInformation.SentGlobalID;
import edu.washington.multir.corpus.DefaultCorpusInformationSpecification.TokenOffsetInformation.SentenceRelativeCharacterOffsetBeginAnnotation;
import edu.washington.multir.corpus.DefaultCorpusInformationSpecification.TokenOffsetInformation.SentenceRelativeCharacterOffsetEndAnnotation;
import edu.washington.multir.corpus.DefaultCorpusInformationSpecificationWithNEL.SentNamedEntityLinkingInformation.NamedEntityLinkingAnnotation;
import edu.washington.multir.data.Argument;
import edu.washington.multir.data.KBArgument;
import edu.washington.multir.featuregeneration.FeatureGenerator;
import edu.washington.multir.knowledgebase.KnowledgeBase;
import edu.washington.multir.sententialextraction.DocumentExtractor;
import edu.washington.multir.util.CLIUtils;
import edu.washington.multir.util.CorpusUtils;
import edu.washington.multir.util.EvaluationUtils;
import edu.washington.multir.util.ModelUtils;

public class FeedbackDistantSupervision {
	
	private static long newMidCount =0;
	private static final String MID_BASE = "MID";
	private static final long SCORE_THRESHOLD = 150000000;
	private static Map<String,List<String>> idToAliasMap = null;
	
	public static void main(String[] args) throws ClassNotFoundException, InstantiationException, IllegalAccessException, ParseException, NoSuchMethodException, SecurityException, IllegalArgumentException, InvocationTargetException, SQLException, IOException{
		
		
		//load in k partitioned models and sigs
		List<String> arguments  = new ArrayList<String>();
		for(String arg: args){
			arguments.add(arg);
		}
		CorpusInformationSpecification cis = CLIUtils.loadCorpusInformationSpecification(arguments);
		FeatureGenerator fg = CLIUtils.loadFeatureGenerator(arguments);
		ArgumentIdentification ai = CLIUtils.loadArgumentIdentification(arguments);
		List<SententialInstanceGeneration> sigs = CLIUtils.loadSententialInstanceGenerationList(arguments);
		List<String> modelPaths = CLIUtils.loadFilePaths(arguments);
		List<String> outputDSFiles = CLIUtils.loadOutputFilePaths(arguments);
		
		if(!( (sigs.size() == modelPaths.size()) && (outputDSFiles.size() == modelPaths.size()))){
			throw new IllegalArgumentException("Size of inputDS, outputDS, modelPaths, and siglist must all be equal");
		}
		
		String corpusDatabase = arguments.get(0);
		String targetRelationPath = arguments.get(1);
		Corpus trainCorpus = new Corpus(corpusDatabase,cis,true);		
		Set<String> targetRelations = EvaluationUtils.loadTargetRelations(targetRelationPath);
		KnowledgeBase kb = new KnowledgeBase(arguments.get(2),arguments.get(3),arguments.get(1));
		Map<String,List<String>> entityMap = kb.getEntityMap();
		
		
		if(arguments.size() == 6){
			String corpusSetting = arguments.get(4);
			String pathToTestDocumentFile = arguments.get(5);
			
			if(!corpusSetting.equals("train") && !corpusSetting.equals("test")){
				throw new IllegalArgumentException("This argument must be train or test");
			}
			File f = new File(pathToTestDocumentFile);
			if(!f.exists() || !f.isFile()){
				throw new IllegalArgumentException("File at " + pathToTestDocumentFile + " does not exist or is not a file");
			}
			
			if(corpusSetting.equals("train")){
				trainCorpus.setCorpusToTrain(pathToTestDocumentFile);
			}
			else{
				trainCorpus.setCorpusToTest(pathToTestDocumentFile);
			}
		}

		//create PartitionData Map from model name
		Map<String,PartitionData> modelDataMap = new HashMap<>();
		
		idToAliasMap = kb.getIDToAliasMap();
		
		
		
		for(int i =0; i < modelPaths.size(); i++){
			String modelPath = modelPaths.get(i);
			SententialInstanceGeneration sig = sigs.get(i);
			DocumentExtractor de = new DocumentExtractor(modelPath, fg,ai,sig);
			Map<Integer,String> ftId2FtMap = ModelUtils.getFeatureIDToFeatureMap(de.getMapping());
			modelDataMap.put(modelPath,new PartitionData(sig,outputDSFiles.get(i),de,ftId2FtMap));
			
		}

		
		
		//Run extractor over corpus, get new extractions
		Iterator<Annotation> di =trainCorpus.getDocumentIterator();
		int docCount =0;
		while(di.hasNext()){
			Annotation doc = di.next();
			List<CoreMap> sentences = doc.get(CoreAnnotations.SentencesAnnotation.class);
			for(CoreMap sentence : sentences){
				for(String modelPath: modelPaths){
					SententialInstanceGeneration sig = modelDataMap.get(modelPath).sig;
					DocumentExtractor de = modelDataMap.get(modelPath).de;
					Map<Integer,String> ftID2ftMap = modelDataMap.get(modelPath).ftID2ftMap;
					List<DS> newNegativeAnnotations = modelDataMap.get(modelPath).newNegativeAnnotations;
					List<DS> newPositiveAnnotations = modelDataMap.get(modelPath).newPositiveAnnotations;
					//argument identification
					List<Argument> sentenceArgs =  ai.identifyArguments(doc,sentence);
					//sentential instance generation
					List<Pair<Argument,Argument>> sententialInstances = sig.generateSententialInstances(sentenceArgs, sentence);
					for(Pair<Argument,Argument> p : sententialInstances){
						Pair<Triple<String,Double,Double>,Map<Integer,Map<Integer,Double>>> extrResult = 
						de.extractFromSententialInstanceWithAllFeatureScores(p.first, p.second, sentence, doc);
						if(extrResult != null){
							Integer sentNum = sentence.get(SentGlobalID.class);				
							Triple<String,Double,Double> extrScoreTriple = extrResult.first;
							String rel = extrScoreTriple.first;
							
							if(!rel.equals("NA")){
								String arg1Id = getArgId(doc,sentence,p.first);
								String arg2Id = getArgId(doc,sentence,p.second);
								if(arg1Id == null){
									arg1Id = getNextMid();
								}
								if(arg2Id == null){
									arg2Id = getNextMid();
								}
								DS ds = new DS(p.first,p.second,arg1Id,arg2Id,sentNum,rel);
	
									
								if(isTrueNegative(kb,doc,sentence,p.first,p.second,rel,arg1Id,arg2Id)){
									ds =  new DS(p.first,p.second,arg1Id,arg2Id,sentNum,rel);
									ds.score = extrScoreTriple.third;
									newNegativeAnnotations.add(ds);
								}
								
//								// if extraction not a true negative and score greater than threshold treat as new positive.
//								else if(extrScoreTriple.third > SCORE_THRESHOLD){
//									
//									System.out.println(getDSString(ds)+"\t"+extrScoreTriple.third);
//									newAnnotations.add(ds);
//								}
								
								// if extraction not a true negative and score greater than threshold treat as new positive.
								else if (!isProbablyNegative(kb,p.first,p.second,arg1Id,arg2Id,rel)){
									
									ds.score = extrScoreTriple.third;
									newPositiveAnnotations.add(ds);
								}
							}
						}
					}
				}
			}
			docCount++;
			if(docCount % 1000 == 0){
				System.out.println(docCount + " docs processed");
			}
//			if(docCount == 30000){
//				break;
//			}
		}

		
		
		//output new negatives into new DS files
		for(String modelPath: modelPaths){
			String outputFileName = modelDataMap.get(modelPath).dsOutputPath;
			BufferedWriter bw = new BufferedWriter(new FileWriter(new File(outputFileName)));
			
			
			List<List<DS>> relationSpecificNegativeInstances = new ArrayList<>();
			List<DS> allNegativeExtractions = modelDataMap.get(modelPath).newNegativeAnnotations;
			for(DS ds : allNegativeExtractions){
				
				int foundIndex = -1;
				for(int i =0 ; i < relationSpecificNegativeInstances.size(); i++){
					List<DS> lds = relationSpecificNegativeInstances.get(i);
					DS ds1 = lds.get(0);
					if(ds.relation.equals(ds1.relation)){
						foundIndex = i;
					}
				}
				if(foundIndex == -1){
					List<DS> newDsList = new ArrayList<>();
					newDsList.add(ds);
					relationSpecificNegativeInstances.add(newDsList);
				}
				else{
					List<DS> relationSpecificDsList = relationSpecificNegativeInstances.get(foundIndex);
					relationSpecificDsList.add(ds);
				}
			}
			
			for(List<DS> relationSpecificDsList : relationSpecificNegativeInstances ){
				Collections.sort(relationSpecificDsList, new Comparator<DS>(){

					@Override
					public int compare(DS arg0, DS arg1) {
						if(arg0.score > arg1.score){
							return -1;
						}
						else if(arg0.score < arg1.score){
							return 1;
						}
						else{
							return 0;
						}
					}
					
				});				
				List<DS> topNegativeExtractions = relationSpecificDsList.subList(0, Math.max(0,(int)Math.ceil((double)relationSpecificDsList.size()/2.0)));
				for(DS ds : topNegativeExtractions){
					ds.relation = "NA";
					bw.write(getDSString(ds)+"\n");
				}		
			}
			
			
			//make  a List<List<DS>> for each separate positive relation
			List<List<DS>> relationSpecificPositiveInstances = new ArrayList<>();
			List<DS> allPositiveExtractions = modelDataMap.get(modelPath).newPositiveAnnotations;
			

			for(DS ds : allPositiveExtractions){
				
				int foundIndex = -1;
				for(int i =0 ; i < relationSpecificPositiveInstances.size(); i++){
					List<DS> lds = relationSpecificPositiveInstances.get(i);
					DS ds1 = lds.get(0);
					if(ds.relation.equals(ds1.relation)){
						foundIndex = i;
					}
				}
				if(foundIndex == -1){
					List<DS> newDsList = new ArrayList<>();
					newDsList.add(ds);
					relationSpecificPositiveInstances.add(newDsList);
				}
				else{
					List<DS> relationSpecificDsList = relationSpecificPositiveInstances.get(foundIndex);
					relationSpecificDsList.add(ds);
				}
			}
			
			for(List<DS> relationSpecificDsList : relationSpecificPositiveInstances ){
				Collections.sort(relationSpecificDsList, new Comparator<DS>(){

					@Override
					public int compare(DS arg0, DS arg1) {
						if(arg0.score > arg1.score){
							return -1;
						}
						else if(arg0.score < arg1.score){
							return 1;
						}
						else{
							return 0;
						}
					}
					
				});				
				List<DS> topPositiveExtractions = relationSpecificDsList.subList(0, Math.max(0,(int)Math.ceil((double)relationSpecificDsList.size()/10.0)));
				collapseArgumentPairs(topPositiveExtractions);
				for(DS ds : topPositiveExtractions){
					bw.write(getDSString(ds)+"\n");
				}		
			}
			bw.close();
		}
	}

	private static void collapseArgumentPairs(List<DS> topPositiveExtractions) {

		//create map from argument pair names to pairs of ids
		Map<Pair<String,String>,Pair<String,String>> nameIdMap = new HashMap<>();
		
		
		//find valid mid pairs and put in map
		for(DS ds : topPositiveExtractions){
			String arg1Name = ds.arg1.getArgName();
			String arg2Name = ds.arg2.getArgName();
			Pair<String,String> idPair = new Pair<String,String>(ds.arg1ID,ds.arg2ID);
			if((!idPair.first.startsWith("MID")) && (!idPair.second.startsWith("MID"))){
				Pair<String,String> namePair = new Pair<>(arg1Name,arg2Name);
				if(!nameIdMap.containsKey(namePair)){
					nameIdMap.put(namePair,idPair);
				}
			}
		}
		
		//find arg pairs without valid ids in map and make them up
		for(DS ds : topPositiveExtractions){
			String arg1Name = ds.arg1.getArgName();
			String arg2Name = ds.arg2.getArgName();
			Pair<String,String> idPair = new Pair<String,String>(ds.arg1ID,ds.arg2ID);
			Pair<String,String> namePair = new Pair<>(arg1Name,arg2Name);
		    if(!nameIdMap.containsKey(namePair)){
					nameIdMap.put(namePair,idPair);
			}
		}
		
		//assign all idential name pairs the idential id pair
		for(DS ds : topPositiveExtractions){
			String arg1Name = ds.arg1.getArgName();
			String arg2Name = ds.arg2.getArgName();
			Pair<String,String> namePair = new Pair<>(arg1Name,arg2Name);
			Pair<String,String> idPair = nameIdMap.get(namePair);
			ds.arg1ID = idPair.first;
			ds.arg2ID = idPair.second;
		}
		
	}

	private static boolean isProbablyNegative(KnowledgeBase kb, Argument first,
			Argument second, String arg1Id, String arg2Id, String rel) {

		boolean print = false;
		if(first.getArgName().contains("Al Gore") && second.getArgName().contains("U.S.")){
			print = true;
		}
		if(first.getArgName().contains("Chakvetadze") && second.getArgName().contains("Russia")){
			print = true;
		}
		if(first.getArgName().contains("Michelle Obama") && second.getArgName().contains("Chicago")){
			print = true;
		}

		
		
		if((!arg1Id.startsWith("MID")) && (!arg2Id.startsWith("MID"))){
			if(kb.hasRelationWith(arg1Id, arg2Id)){
				return false;
			}
		}
		if(!arg1Id.startsWith("MID")){
			if(kb.getEntityPairRelationMap().containsKey(arg1Id)){
				List<Pair<String,String>> arg1Rels = kb.getEntityPairRelationMap().get(arg1Id);
				List<String> arg2Ids = new ArrayList<>();
				for(Pair<String,String> p : arg1Rels){
					if(p.first.equals(rel)){
						arg2Ids.add(p.second);
					}
				}
				List<String> arg2PossibleStrings = new ArrayList<String>();
				
				for(String argId : arg2Ids){
					if(idToAliasMap.containsKey(argId)){
						arg2PossibleStrings.addAll(idToAliasMap.get(argId));
					}
				}
				
				String arg2String = second.getArgName().toLowerCase();
				
				for(String candidateAlias: arg2PossibleStrings){
					String lowerCandidate = candidateAlias.toLowerCase();
					if(print)System.out.println("Comparing " + arg2String +" and  freebase arg2 " +lowerCandidate);
					if(lowerCandidate.contains(arg2String) || lowerCandidate.equals(arg2String) || arg2String.contains(lowerCandidate)){
						if(print)System.out.println("They are similar so  has candidate is true");
						return false;
					}
				}
			}
		}
		//check strings too
		Map<String,List<String>> entityMap = kb.getEntityMap();
		if(print) System.out.println(first.getArgName() +"\t" + second.getArgName());
		if(entityMap.containsKey(first.getArgName())){
			if(entityMap.containsKey(second.getArgName())){
				List<String> arg1Ids = entityMap.get(first.getArgName());
				List<String> arg2Ids = entityMap.get(second.getArgName());
				if(print) System.out.println(arg1Ids.size() + " " + arg2Ids.size());
				for(String a1Id : arg1Ids){
					for(String a2Id: arg2Ids){
						if(print) System.out.println("Comparing ids " + a1Id + " and " + a2Id);
						List<String> relations = kb.getRelationsBetweenArgumentIds(a1Id,a2Id);
						if(relations.size()>0){
							return false;
						}
					}
				}
			}
		}

		return true;
		
	}

	private static String getWeakMid(CoreMap s, Argument arg) {
		List<Triple<Pair<Integer,Integer>,String,Float>> nelAnnotation = s.get(NamedEntityLinkingAnnotation.class);
		List<CoreLabel> tokens = s.get(CoreAnnotations.TokensAnnotation.class);
		if(nelAnnotation != null){
			for(Triple<Pair<Integer,Integer>,String,Float> trip : nelAnnotation){
				String id = trip.second;
				Float conf = trip.third;
				//if token span has a link create a new argument
				if(!id.equals("null")){
					//get character offsets
					Integer startTokenOffset = trip.first.first;
					Integer endTokenOffset = trip.first.second;
					if(startTokenOffset >= 0 && startTokenOffset < tokens.size() && endTokenOffset >= 0 && endTokenOffset < tokens.size()){
						Integer startCharacterOffset = tokens.get(startTokenOffset).get(SentenceRelativeCharacterOffsetBeginAnnotation.class);
						Integer endCharacterOffset = tokens.get(endTokenOffset-1).get(SentenceRelativeCharacterOffsetEndAnnotation.class);
							
						//get argument string
						String sentText = s.get(CoreAnnotations.TextAnnotation.class);
						if(sentText != null && startCharacterOffset !=null && endCharacterOffset!=null){
							String argumentString = sentText.substring(startCharacterOffset, endCharacterOffset);
								
							//add argument to list
							KBArgument nelArgument = new KBArgument(new Argument(argumentString,startCharacterOffset,endCharacterOffset),id);
							if(arg.isContainedIn(nelArgument) || arg.equals(nelArgument)){
								return nelArgument.getKbId();
							}
						}
					}
				}
			}
		}
		return getNextMid();
	}


	private static String getArgId(Annotation doc, CoreMap sentence,
			Argument arg) {
		String argLink = null;
		List<Argument> nelArgs = NELArgumentIdentification.getInstance().identifyArguments(doc, sentence);
		for(Argument nelArg: nelArgs){
			KBArgument kbNelArg = (KBArgument)nelArg;
			if( arg.equals(nelArg) || (arg.isContainedIn(nelArg) && getLink(sentence,arg).equals(kbNelArg.getKbId()))){
				argLink = kbNelArg.getKbId();
			}
		}
		return argLink;
	}


	private static String getLink(CoreMap sentence, Argument arg) {
		List<Triple<Pair<Integer,Integer>,String,Float>> nelData = sentence.get(NamedEntityLinkingAnnotation.class);
		for(Triple<Pair<Integer,Integer>,String,Float> t : nelData){
			Pair<Integer,Integer> nelTokenOffset = t.first;
			Pair<Integer,Integer> argTokenOffset = CorpusUtils.getTokenOffsetsFromCharacterOffsets(arg.getStartOffset(), arg.getEndOffset(), sentence);
			if(nelTokenOffset.equals(argTokenOffset)){
				return t.second;
			}
		}
		return "null";
	}

	private static boolean isTrueNegative(KnowledgeBase KB, Annotation doc, CoreMap sentence, Argument arg1, Argument arg2, String extractionRel, String arg1Id, String arg2Id) {
		

		
		boolean print = false;
		if(arg1.getArgName().contains("Al Gore") && arg2.getArgName().contains("U.S.")){
			print = true;
		}
		if(arg1.getArgName().contains("Chakvetadze") && arg2.getArgName().contains("Russia")){
			print = true;
		}
		if(arg1.getArgName().contains("Michelle Obama") && arg2.getArgName().contains("Chicago")){
			print = true;
		}

		
		
		
		if(print){
			System.out.println("arg1  = " + arg1.getArgName());
			System.out.println("link1 = " + arg1Id);
			System.out.println("arg2  = " + arg2.getArgName());
			System.out.println("link2 = " + arg2Id);
		}

		if((!arg1Id.startsWith("MID")) && (!arg2Id.startsWith("MID"))){
			if(print){
				Map<String,List<Pair<String,String>>> m =KB.getEntityPairRelationMap();
				if(m.containsKey(arg1Id)){
					List<Pair<String,String>> relations = m.get(arg1Id);
					for(Pair<String,String> relation : relations){
						System.out.println(relation.first + "\t" + relation.second);
					}
				}
			}
			
			if(KB.hasRelationWith(arg1Id, arg2Id)){
				if(print) System.out.println("Returning false because the entities have a relation");
				return false;
			}
			else{
				if(KB.participatesInRelationAsArg1(arg1Id, extractionRel)){
					if(isProbablyNegative(KB,arg1,arg2,arg1Id,arg2Id,extractionRel)){
						if(print) System.out.println("Returning true because the entities have no relation and arg1 participates in extraction relation");
						return true;
					}
				}
			}
		}
		if(print) System.out.println("Returning false because one of the links returned null");
		return false;
	}


	//creates a fake mid, used for new negative examples from feedback
	private static String getNextMid() {
		return MID_BASE + newMidCount++;
	}


	private static String getDSString(DS ds) {
		StringBuilder sb = new StringBuilder();
		sb.append(ds.arg1ID);
		sb.append("\t");
		sb.append(ds.arg1.getStartOffset());
		sb.append("\t");
		sb.append(ds.arg1.getEndOffset());
		sb.append("\t");
		sb.append(ds.arg1.getArgName());
		sb.append("\t");
		sb.append(ds.arg2ID);
		sb.append("\t");
		sb.append(ds.arg2.getStartOffset());
		sb.append("\t");
		sb.append(ds.arg2.getEndOffset());
		sb.append("\t");
		sb.append(ds.arg2.getArgName());
		sb.append("\t");
		sb.append(ds.sentNum);
		sb.append("\t");
		sb.append(ds.relation);
		return sb.toString().trim();
	}
	
	private static class DS{
		private Argument arg1;
		private Argument arg2;
		private String arg1ID;
		private String arg2ID;
		private Integer sentNum;
		private String relation;
		private Double score;
		
		public DS(Argument arg1, Argument arg2, String arg1ID, String arg2ID, Integer sentNum,String relation){
			this.arg1=arg1;
			this.arg2=arg2;
			this.arg1ID=arg1ID;
			this.arg2ID=arg2ID;
			this.sentNum=sentNum;
			this.relation=relation;
		}
		
		@Override
		public boolean equals(Object other){
			DS ds = (DS)other;
			if((arg1.equals(ds.arg1)) &&
				(arg2.equals(ds.arg2)) &&
				(sentNum.equals(ds.sentNum))){
				return true;
			}
			else{
				return false;
			}
		}
		
		@Override
		public int hashCode(){
			return new HashCodeBuilder(37,41).append(arg1).
					append(arg2)
					.append(sentNum).toHashCode();
		}
		
		
	}


	private static class PartitionData{
		private SententialInstanceGeneration sig;
		private String dsOutputPath;
		private DocumentExtractor de;
		private List<DS> newNegativeAnnotations;
		private List<DS> newPositiveAnnotations;
		private Map<Integer,String> ftID2ftMap;
		
		
		public PartitionData(SententialInstanceGeneration sig, String dsOutputPath, DocumentExtractor de, Map<Integer,String> ftID2ftMap){
			this.sig = sig;
			this.dsOutputPath = dsOutputPath;
			this.de = de;
			this.ftID2ftMap = ftID2ftMap;
			newNegativeAnnotations = new ArrayList<>();
			newPositiveAnnotations = new ArrayList<>();

		}
	}

}
