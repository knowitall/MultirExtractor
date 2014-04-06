package edu.washington.multir.eval;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
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

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.Triple;
import edu.washington.multir.argumentidentification.ArgumentIdentification;
import edu.washington.multir.argumentidentification.FigerAndNERTypeSignaturePERPERSententialInstanceGeneration;
import edu.washington.multir.argumentidentification.SententialInstanceGeneration;
import edu.washington.multir.corpus.Corpus;
import edu.washington.multir.corpus.CorpusInformationSpecification;
import edu.washington.multir.corpus.CorpusInformationSpecification.SentDocNameInformation.SentDocName;
import edu.washington.multir.corpus.CorpusInformationSpecification.SentGlobalIDInformation.SentGlobalID;
import edu.washington.multir.data.Argument;
import edu.washington.multir.data.Extraction;
import edu.washington.multir.data.ExtractionAnnotation;
import edu.washington.multir.featuregeneration.DefaultFeatureGeneratorConcatFIGER;
import edu.washington.multir.featuregeneration.DefaultFeatureGeneratorIndepFIGER;
import edu.washington.multir.featuregeneration.DefaultFeatureGeneratorWithFIGER;
import edu.washington.multir.featuregeneration.FeatureGenerator;
import edu.washington.multir.sententialextraction.DocumentExtractor;
import edu.washington.multir.util.CLIUtils;
import edu.washington.multir.util.EvaluationUtils;
import edu.washington.multir.util.FigerTypeUtils;
import edu.washington.multir.util.ModelUtils;

/**
 * This class is designed for a more accurate evaluation of 
 * extractors. The input is a set of previously annotated extractions
 * and extractor configuration. If the extractor extracts extractions
 * that are not in the input annotations then an exception is thrown
 * and the diff must be annotated and combined with the previous annotation
 * input. This ensures that we will return true precision of the extractor.
 * Rather than recall we will produce yield as the number of correct
 * extractions produced by the extractor.
 * @author jgilme1
 *
 */
public class MultiModelManualEvaluation {
	
	private static Set<String> targetRelations = null;
	private static boolean verbose = false;
	
	public static void main (String[] args) throws ParseException, ClassNotFoundException, InstantiationException, IllegalAccessException, NoSuchMethodException, SecurityException, IllegalArgumentException, InvocationTargetException, SQLException, IOException{

		
		List<String> arguments  = new ArrayList<String>();
		for(String arg: args){
			arguments.add(arg);
		}
		
		CorpusInformationSpecification cis = CLIUtils.loadCorpusInformationSpecification(arguments);
		FeatureGenerator fg = CLIUtils.loadFeatureGenerator(arguments);
		ArgumentIdentification ai = CLIUtils.loadArgumentIdentification(arguments);
		List<SententialInstanceGeneration> sigs = CLIUtils.loadSententialInstanceGenerationList(arguments);
		List<String> modelPaths = CLIUtils.loadFilePaths(arguments);
		
		
		String annotationsInputFilePath = arguments.get(0);
		String evaluationRelationsFilePath = arguments.get(1);
		
		targetRelations = EvaluationUtils.loadTargetRelations(evaluationRelationsFilePath);
		
		//load test corpus
		Corpus c = CLIUtils.loadCorpus(arguments, cis);
		
		//if corpus object is full corpus, we may specify to look at train or test
		//partition of it based on a input file representing the names of the test documents
		if(arguments.size() == 4){
			String corpusSetting = arguments.get(2);
			String pathToTestDocumentFile = arguments.get(3);
			
			if(!corpusSetting.equals("train") && !corpusSetting.equals("test")){
				throw new IllegalArgumentException("This argument must be train or test");
			}
			File f = new File(pathToTestDocumentFile);
			if(!f.exists() || !f.isFile()){
				throw new IllegalArgumentException("File at " + pathToTestDocumentFile + " does not exist or is not a file");
			}
			
			if(corpusSetting.equals("train")){
				c.setCorpusToTrain(pathToTestDocumentFile);
			}
			else{
				c.setCorpusToTest(pathToTestDocumentFile);
			}
		}
		
		

		if(fg instanceof DefaultFeatureGeneratorWithFIGER | fg instanceof DefaultFeatureGeneratorConcatFIGER | fg instanceof DefaultFeatureGeneratorIndepFIGER){
			FigerTypeUtils.init();
		}
		
		long start = System.currentTimeMillis();
		List<Extraction> extractions = getMultiModelExtractions(c,ai,fg,sigs,modelPaths);
		long end = System.currentTimeMillis();
		System.out.println("Got Extractions in " + (end-start));
		
		start = end;
		List<ExtractionAnnotation> annotations = EvaluationUtils.loadAnnotations(annotationsInputFilePath);
		end = System.currentTimeMillis();
		System.out.println("Got Annotations in " + (end-start));

		start = end;

		List<Extraction> diffExtractions = EvaluationUtils.getDiff(extractions,annotations);
		end = System.currentTimeMillis();
		System.out.println("Got diff in " + (end-start));

		boolean useFixedSet = false;
		if (useFixedSet) {
		for (int i = extractions.size()-1; i > -1; i--) {
			if (diffExtractions.contains(extractions.get(i))) {
				System.out.println("removing");
				extractions.remove(i);
			}
		}
		diffExtractions.clear();
		}

		
		//if there is a diff then don't evaluate algorithm yet
		if(diffExtractions.size() > 0){
			//output diff
			String diffOutputName = annotationsInputFilePath + ".diff";
			EvaluationUtils.writeExtractions(diffExtractions,diffOutputName);
			throw new IllegalStateException("inputAnnotations do not include all of the extractions, tag the diff at "
					+ diffOutputName + " and merge with annotations");
		}
		else{
			EvaluationUtils.eval(extractions,annotations,targetRelations);
			EvaluationUtils.relByRelEvaluation(extractions,annotations,targetRelations);
		}
		
		if(fg instanceof DefaultFeatureGeneratorWithFIGER | fg instanceof DefaultFeatureGeneratorConcatFIGER | fg instanceof DefaultFeatureGeneratorIndepFIGER){
			FigerTypeUtils.close();
		}
	}

	private static List<Extraction> getMultiModelExtractions(Corpus c,
			ArgumentIdentification ai, FeatureGenerator fg, List<SententialInstanceGeneration> sigs, List<String> modelPaths) throws SQLException, IOException {
		
		List<Extraction> extrs = new ArrayList<Extraction>();
		for(int i =0; i < sigs.size(); i++){
			Iterator<Annotation> docs = c.getDocumentIterator();
			SententialInstanceGeneration sig = sigs.get(i);
			String modelPath = modelPaths.get(i);
			DocumentExtractor de = new DocumentExtractor(modelPath,fg,ai,sig);

			Map<String,Integer> rel2RelIdMap =de.getMapping().getRel2RelID();
			Map<Integer,String> ftID2ftMap = ModelUtils.getFeatureIDToFeatureMap(de.getMapping());
			
			int docCount = 0;
			while(docs.hasNext()){
				Annotation doc = docs.next();
				List<CoreMap> sentences = doc.get(CoreAnnotations.SentencesAnnotation.class);
				int sentenceCount =1;
				for(CoreMap sentence : sentences){				
					//argument identification
					List<Argument> arguments =  ai.identifyArguments(doc,sentence);
					//sentential instance generation
					List<Pair<Argument,Argument>> sententialInstances = sig.generateSententialInstances(arguments, sentence);
					for(Pair<Argument,Argument> p : sententialInstances){
						Pair<Triple<String,Double,Double>,Map<Integer,Map<Integer,Double>>> extrResult = 
						de.extractFromSententialInstanceWithAllFeatureScores(p.first, p.second, sentence, doc);
						if(extrResult != null){
							Triple<String,Double,Double> extrScoreTripe = extrResult.first;
							Map<Integer,Double> featureScores = extrResult.second.get(rel2RelIdMap.get(extrResult.first.first));
							String rel = extrScoreTripe.first;
							List<Pair<String,Double>> featureScoreList = EvaluationUtils.getFeatureScoreList(featureScores, ftID2ftMap);
							if(targetRelations.contains(rel)){
								if(!(rel.equals("/organization/organization/founders") && (sig instanceof FigerAndNERTypeSignaturePERPERSententialInstanceGeneration))){
									String docName = sentence.get(SentDocName.class);
									String senText = sentence.get(CoreAnnotations.TextAnnotation.class);
									Integer sentNum = sentence.get(SentGlobalID.class);
									Extraction e = new Extraction(p.first,p.second,docName,rel,sentNum,extrScoreTripe.third,senText);
									e.setFeatureScoreList(featureScoreList);
									extrs.add(e);
									if(verbose) logNegativeExtraction(p.first,p.second,sentence,extrResult,de,ftID2ftMap);
								}
							}
							else{
								if(verbose) logNegativeExtraction(p.first,p.second,sentence,extrResult,de,ftID2ftMap);
							}
						}
					}
				}
				docCount++;
				if(docCount % 100 == 0){
					System.out.println(docCount + " docs processed");
				}
			}
		}
		return EvaluationUtils.getUniqueList(extrs);

	}

	private static void logNegativeExtraction(
			Argument first,
			Argument second,
			CoreMap sentence,
			Pair<Triple<String, Double, Double>, Map<Integer,Map<Integer, Double>>> extrResult,
			DocumentExtractor de,
			Map<Integer,String> ftID2ftMap) {
		
		
		System.out.println(sentence.get(SentGlobalID.class) +"\t"+extrResult.first.first + "\t" + extrResult.first.third +"\t" + first.getArgName() + "\t" + second.getArgName() + "\t" + sentence.get(CoreAnnotations.TextAnnotation.class));
		
		Map<String,Integer> rel2RelIDMap = de.getMapping().getRel2RelID();
		
		for(String rel: rel2RelIDMap.keySet()){
			Integer intRel = rel2RelIDMap.get(rel);
			
			
			System.out.println("Features for rel: " + rel);
			Map<Integer,Double> featureMap = extrResult.second.get(intRel);
			for(Integer featureI : featureMap.keySet()){
				String feat = ftID2ftMap.get(featureI);
				Double score = featureMap.get(featureI);
				System.out.println(feat + "\t" + score);
			}
			
		}
	}
}
