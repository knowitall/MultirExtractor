package edu.washington.multir.distantsupervision;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.cli.ParseException;
import org.apache.commons.lang.builder.HashCodeBuilder;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.Triple;
import edu.washington.multir.argumentidentification.ArgumentIdentification;
import edu.washington.multir.argumentidentification.FigerAndNERTypeSignaturePERPERSententialInstanceGeneration;
import edu.washington.multir.argumentidentification.NELArgumentIdentification;
import edu.washington.multir.argumentidentification.SententialInstanceGeneration;
import edu.washington.multir.corpus.Corpus;
import edu.washington.multir.corpus.CorpusInformationSpecification;
import edu.washington.multir.corpus.CorpusInformationSpecification.SentDocNameInformation.SentDocName;
import edu.washington.multir.corpus.CorpusInformationSpecification.SentGlobalIDInformation.SentGlobalID;
import edu.washington.multir.data.Argument;
import edu.washington.multir.data.Extraction;
import edu.washington.multir.data.KBArgument;
import edu.washington.multir.featuregeneration.FeatureGenerator;
import edu.washington.multir.knowledgebase.KnowledgeBase;
import edu.washington.multir.sententialextraction.DocumentExtractor;
import edu.washington.multir.util.CLIUtils;
import edu.washington.multir.util.EvaluationUtils;
import edu.washington.multir.util.ModelUtils;

public class FeedbackDistantSupervision {
	
	private static long newMidCount =0;
	private static final String MID_BASE = "MID";
	private static final long SCORE_THRESHOLD = 150000000;
	
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
					List<DS> newAnnotations = modelDataMap.get(modelPath).newAnnotations;
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
								DS ds = new DS(p.first,p.second,"","",sentNum,rel);
	
	
								if(isTrueNegative(kb,doc,sentence,p.first,p.second)){
									ds =  new DS(p.first,p.second,"","",sentNum,"NA");
									System.out.println(getDSString(ds)+"\t"+extrScoreTriple.third);
									newAnnotations.add(ds);
								}
								
								// if extraction not a true negative and score greater than threshold treat as new positive.
								else if(extrScoreTriple.third > SCORE_THRESHOLD){
									System.out.println(getDSString(ds)+"\t"+extrScoreTriple.third);
									newAnnotations.add(ds);
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
		}

		
		
		//output new negatives into new DS files
		for(String modelPath: modelPaths){
			String outputFileName = modelDataMap.get(modelPath).dsOutputPath;
			BufferedWriter bw = new BufferedWriter(new FileWriter(new File(outputFileName)));
			for(DS ds : modelDataMap.get(modelPath).newAnnotations){
				bw.write(getDSString(ds)+"\n");
			}
			
			
			bw.close();
		}
		
		
		
	}
	
	
	private static boolean isTrueNegative(KnowledgeBase KB, Annotation doc, CoreMap sentence, Argument arg1, Argument arg2) {
		List<Argument> nelArgs = NELArgumentIdentification.getInstance().identifyArguments(doc, sentence);
		
		String arg1Link = "null";
		String arg2Link = "null";
		
		boolean print = false;
		if(arg1.getArgName().contains("Musharraf") && arg2.getArgName().contains("Pakistan")){
			print = true;
		}
		if(arg1.getArgName().contains("Chakvetadze") && arg2.getArgName().contains("Russia")){
			print = true;
		}
		if(arg1.getArgName().contains("Michelle Obama") && arg2.getArgName().contains("Chicago")){
			print = true;
		}
		
		for(Argument nelArg: nelArgs){
			if(arg1.isContainedIn(nelArg) || arg1.equals(nelArg)){
				KBArgument kbNelArg = (KBArgument)nelArg;
				arg1Link = kbNelArg.getKbId();
			}
			if(arg2.isContainedIn(nelArg) || arg2.equals(nelArg)){
				KBArgument kbNelArg = (KBArgument)nelArg;
				arg2Link = kbNelArg.getKbId();
			}
		}
		
		
		if(print){
			System.out.println("arg1  = " + arg1.getArgName());
			System.out.println("link1 = " + arg1Link);
			System.out.println("arg2  = " + arg2.getArgName());
			System.out.println("link2 = " + arg2Link);
		}
		if((!arg1Link.equals("null")) && (!arg2Link.equals("null"))){
			
			if(print){
				Map<String,List<Pair<String,String>>> m =KB.getEntityPairRelationMap();
				List<Pair<String,String>> relations = m.get(arg1Link);
				for(Pair<String,String> relation : relations){
					System.out.println(relation.first + "\t" + relation.second);
				}
			}
			
			if(KB.hasRelationWith(arg1Link, arg2Link)){
				if(print) System.out.println("Returning false because the entities have a relation");
				return false;
			}
			else{
				if(print) System.out.println("Returning true because the entities have no relation");
				return true;
			}
		}
		else{
			if(print) System.out.println("Returning false because one of the links returned null");
			return false;
		}
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
		private List<DS> newAnnotations;
		private Map<Integer,String> ftID2ftMap;
		
		
		public PartitionData(SententialInstanceGeneration sig, String dsOutputPath, DocumentExtractor de, Map<Integer,String> ftID2ftMap){
			this.sig = sig;
			this.dsOutputPath = dsOutputPath;
			this.de = de;
			this.ftID2ftMap = ftID2ftMap;
			newAnnotations = new ArrayList<>();
		}
	}

}
