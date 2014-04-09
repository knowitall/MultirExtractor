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
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.cli.ParseException;
import org.apache.commons.lang.StringUtils;
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
import edu.washington.multir.util.FigerTypeUtils;
import edu.washington.multir.util.GuidMidConversion;
import edu.washington.multir.util.ModelUtils;

public class FeedbackDistantSupervision {
	
	private static long newMidCount =0;
	private static final String MID_BASE = "MID";
	private static final long SCORE_THRESHOLD = 150000000;
	private static Map<String,List<String>> idToAliasMap = null;
	private static boolean print = false;
	
	public static void main(String[] args) throws ClassNotFoundException, InstantiationException, IllegalAccessException, ParseException, NoSuchMethodException, SecurityException, IllegalArgumentException, InvocationTargetException, SQLException, IOException{
		
		FigerTypeUtils.init();
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
								else if (!isProbablyNegative(kb,p.first,p.second,arg1Id,arg2Id,rel,sentence)){
									
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
//			if(docCount == 100000){
//				break;
//			}
		}

		FigerTypeUtils.close();

		
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
				List<DS> topNegativeExtractions = relationSpecificDsList.subList(0, Math.max(0,(int)Math.ceil((double)relationSpecificDsList.size())));
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

				collapseArgumentPairs(relationSpecificDsList);
				List<DSEntityPair> dsEntityPairs = getEntityPairs(relationSpecificDsList);
				//group argument pairs by entity ids, and take top 10% of those mention pairs by average score
				
				Collections.sort(dsEntityPairs, new Comparator<DSEntityPair>(){

					@Override
					public int compare(DSEntityPair arg0, DSEntityPair arg1) {
						if(arg0.avgScore > arg1.avgScore){
							return -1;
						}
						else if(arg0.avgScore < arg1.avgScore){
							return 1;
						}
						else{
							return 0;
						}
					}
					
				});
				
				List<DSEntityPair> topPositiveExtractions = dsEntityPairs.subList(0, Math.max(0,(int)Math.ceil((double)dsEntityPairs.size()/10.0)));
				for(DSEntityPair dsEntityPair : topPositiveExtractions){
					for(DS ds : dsEntityPair.mentions){
					  bw.write(getDSString(ds)+"\n");
					}
				}		
			}
			bw.close();
		}
	}

	private static List<DSEntityPair> getEntityPairs(
			List<DS> relationSpecificDsList) {
		
		Map<String,List<DS>> idsToDSListMap = new HashMap<>();
		List<DSEntityPair> dsEntityPairs = new ArrayList<>();
		for(DS ds : relationSpecificDsList){
			
			String key = ds.arg1ID+":"+ds.arg2ID;
			if(idsToDSListMap.containsKey(key)){
				idsToDSListMap.get(key).add(ds);
			}
			else{
				List<DS> newDSList = new ArrayList<>();
				newDSList.add(ds);
				idsToDSListMap.put(key,newDSList);
			}
		}
		
		
		for(String key: idsToDSListMap.keySet()){
			List<DS> dsList = idsToDSListMap.get(key);
			String[] values = key.split(":");
			String arg1Id = values[0];
			String arg2Id = values[1];
			//prevent single instance entity pairs
			if(dsList.size()>1) dsEntityPairs.add(new DSEntityPair(arg1Id,arg2Id,dsList));
		}
		return dsEntityPairs;
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
			Argument second, String arg1Id, String arg2Id, String rel, CoreMap sentence) {

		if(print)System.out.println("Is Probably NEgative? for " + first.getArgName() + " " + second.getArgName());
		
		if((!arg1Id.startsWith("MID")) && (!arg2Id.startsWith("MID"))){
			if(kb.hasRelationWith(arg1Id, arg2Id)){
				if(print) System.out.println("Returning false because mids have relation");
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
		//assume arg1 link to be true and allow arg2link to be false		
		if(!arg1Id.startsWith("MID")){
			List<String> arg2Names = new ArrayList<>();
			if(!kb.getEntityMap().containsKey(second.getArgName())) {
				if(print)System.out.println("Returning false because " + second.getArgName() + " does not have map to id in entity map");
				return false;
			}
			arg2Names.add(second.getArgName());
			List<String> arg2Aliases = idToAliasMap.get(arg2Id);
			if(arg2Aliases!=null){
				arg2Names.addAll(arg2Aliases);
			}
			List<String> arg2Ids = new ArrayList<>();
			for(String arg2Name: arg2Names){
				List<String> ids = kb.getEntityMap().get(arg2Name);
				if(ids != null){
					arg2Ids.addAll(ids);
				}
			}
			if(arg2Ids.size() == 0){
				if(print) System.out.println("Returning false because " + second.getArgName() + " does not have map to id in entity map");
				return false;
			}
			
			for(String a2Id: arg2Ids){
				List<String> relations = kb.getRelationsBetweenArgumentIds(arg1Id, a2Id);
				if(relations.size()>0){
					if(print) System.out.println("Returning false because candidate pair " + arg1Id + " " + a2Id + " has relation");
					return false;
				}
			}
			
			if(stringsSimilar(kb,arg1Id,second.getArgName(),rel)) {
				if(print) System.out.println("Returning false becasue " + arg1Id + " has a similar string to " + second.getArgName());
				return false;
			}
			if(typesDoNotMatch(kb,arg1Id,second,rel,sentence)) {
				if(print) System.out.println("Returning false types for " + arg1Id + " do not match type of " + second.getArgName());
				return false;
			}

		}
		//assume arg2 link to be true and allow arg1link to be false		
		if(!arg2Id.startsWith("MID")){
			List<String> arg1Names = new ArrayList<>();
			if(!kb.getEntityMap().containsKey(first.getArgName())) {
				if(print)System.out.println("Returning false because " + first.getArgName() + " does not have map to id in entity map");
				return false;
			}
			arg1Names.add(first.getArgName());
			List<String> arg1Aliases = idToAliasMap.get(arg1Id);
			if(arg1Aliases!=null){
				arg1Names.addAll(arg1Aliases);
			}
			List<String> arg1Ids = new ArrayList<>();
			for(String arg1Name: arg1Names){
				List<String> ids = kb.getEntityMap().get(arg1Name);
				if(ids != null){
					arg1Ids.addAll(ids);
				}
			}
			if(arg1Ids.size() == 0){
				if(print)System.out.println("Returning false because " + first.getArgName() + " does not have map to id in entity map");
				return false;
			}
			
			for(String a1Id: arg1Ids){
				List<String> relations = kb.getRelationsBetweenArgumentIds(a1Id, arg2Id);
				if(relations.size()>0){
					if(print) System.out.println("Returning false because candidate pair " + a1Id + " " + arg2Id + " has relation");
					return false;
				}
				if(stringsSimilar(kb,a1Id,second.getArgName(),rel)) {
					if(print) System.out.println("Returning false becasue " + a1Id + " has a similar string to " + second.getArgName());

					return false;
				}
				if(typesDoNotMatch(kb,a1Id,second,rel,sentence)) {
					if(print) System.out.println("Returning false types for " + a1Id + " do not match type of " + second.getArgName());

					return false;
				}

			}			
		}
		//assume both are false
		if(print) System.out.println(first.getArgName() +"\t" + second.getArgName());
		if(kb.getEntityMap().containsKey(first.getArgName())){
			if(kb.getEntityMap().containsKey(second.getArgName())){
				List<String> arg1Ids = kb.getEntityMap().get(first.getArgName());
				List<String> arg2Ids = kb.getEntityMap().get(second.getArgName());
				if(print) System.out.println(arg1Ids.size() + " " + arg2Ids.size());
				for(String a1Id : arg1Ids){
					for(String a2Id: arg2Ids){
						if(print) System.out.println("Comparing ids " + a1Id + " and " + a2Id);
						List<String> relations = kb.getRelationsBetweenArgumentIds(a1Id,a2Id);
						if(relations.size()>0){
							if(print) System.out.println("Returning false because candidate pair " + a1Id + " " + a2Id + " has relation");
							return false;
						}
						if(stringsSimilar(kb,a1Id,second.getArgName(),rel)) {
							if(print) System.out.println("Returning false becasue " + a1Id + " has a similar string to " + second.getArgName());

							return false;
						}
						if(typesDoNotMatch(kb,a1Id,second,rel,sentence)) {
							if(print) System.out.println("Returning false types for " + a1Id + " do not match type of " + second.getArgName());

							return false;
						}
					}
				}
			}
		}

		
		if(print) System.out.println("Arg pair is probably negative");
		return true;
		
	}



	//if notable type matches for any a1Id rel a2 with second argument
	private static boolean typesDoNotMatch(KnowledgeBase kb, String a1Id, Argument second, String rel, CoreMap sentence) {
	
		List<Pair<String,String>> rels = kb.getEntityPairRelationMap().get(a1Id);
		List<Pair<String,String>> targetRels = new ArrayList<>();
		
		if(rels != null){
			Set<String> kbFreebaseTypes = new HashSet<>();
			for(Pair<String,String> p : rels){
				if(p.first.equals(rel)){
					targetRels.add(p);
				}
			}
			if(targetRels.size()>0){
				List<String> arg2Ids = new ArrayList<>();
				for(Pair<String,String> p : targetRels){
					arg2Ids.add(p.second);
				}
				
				for(String arg2Id: arg2Ids){
					kbFreebaseTypes.addAll(FigerTypeUtils.getFreebaseTypesFromID(GuidMidConversion.convertBackward(arg2Id)));
				}
				
				String fbType = CorpusUtils.getFreebaseNotableType(second.getStartOffset(), second.getEndOffset(), sentence);
				if(fbType != null){
					if(!kbFreebaseTypes.contains(fbType)){
						if(print) System.out.println("Type " + fbType + " from " + second.getArgName() + " not in " + a1Id + " relevant types");
						return true;
					}
				}
			}

		}
		
		
		
		return false;
	}

	private static boolean stringsSimilar(KnowledgeBase kb,String a1Id, String argName,
			String rel) {
				
		if(print)System.out.println(a1Id + " " + argName);
		
		List<Pair<String,String>> rels = kb.getEntityPairRelationMap().get(a1Id);
		List<Pair<String,String>> targetRels = new ArrayList<>();
		
		if(rels != null){
			for(Pair<String,String> p : rels){
				if(p.first.equals(rel)){
					targetRels.add(p);
				}
			}
			
			List<String> arg2Ids = new ArrayList<>();
			for(Pair<String,String> p : targetRels){
				arg2Ids.add(p.second);
			}
			
			List<String> arg2Strings = new ArrayList<>();
			for(String id : arg2Ids){
				List<String> aliases = idToAliasMap.get(id);
				for(String alias: aliases){
					if(!arg2Strings.contains(alias)){
						arg2Strings.add(alias);
					}
				}
			}
			
			//if any arg2String in kb contains any token in relation string arg2 then strings are similar
			
			String[] argTokens = argName.split("\\s+");
			for(String arg2String : arg2Strings){
				for(String argToken: argTokens){
					if(arg2String.contains(argToken)){
						if(print)System.out.println(arg2String + " contains " + argToken);
						return true;
					}
				}
			}
			
			//if any token in arg2String in kb is similar enough to any token in arg2 then strings are similar
			for(String arg2String: arg2Strings){
				for(String arg2Token: arg2String.split("\\s+")){
					if(arg2Token.length() >2){
						for(String argToken: argTokens){
							if(argToken.length()>2){
								Integer maxLength = Math.max(arg2Token.length(), argToken.length());
								Integer editDistance = StringUtils.getLevenshteinDistance(argToken, arg2Token);
								Double ratio = (double)editDistance/(double)maxLength;
								//strings are too similar
								if(ratio <= .33){
									if(print)System.out.println(argToken + " is too similar to  " + arg2Token + " with edit score of " + ratio);
									return true;
								}
							}
						}
					}
				}
			}
		}
		
		if(print) System.out.println("Arguments are not similar enough");
		return false;
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
				if(print) System.out.println("Returning false for True Negative because the entities have a relation");
				return false;
			}
			else{
				if(KB.participatesInRelationAsArg1(arg1Id, extractionRel)){
					if(isProbablyNegative(KB,arg1,arg2,arg1Id,arg2Id,extractionRel,sentence)){
						if(print) System.out.println("Returning true for True Negative because is Probably Negative rreturned true");
						return true;
					}
				}
			}
		}
		if(print) System.out.println("Returning false for True NEgative");
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
	
	private static class DSEntityPair{
		private String arg1Id;
		private String arg2Id;
		private List<DS> mentions;
		private double avgScore;
		
		
		public DSEntityPair(String arg1Id, String arg2Id, List<DS> mentions){
			this.arg1Id = arg1Id;
			this.arg2Id = arg2Id;
			this.mentions = mentions;
			
			double sum = 0.0;
			for(DS mention: mentions){
				sum += mention.score;
			}
			avgScore = (sum/mentions.size());
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
