package edu.washington.multir.eval;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.Triple;
import edu.washington.multir.argumentidentification.ArgumentIdentification;
import edu.washington.multir.argumentidentification.SententialInstanceGeneration;
import edu.washington.multir.corpus.Corpus;
import edu.washington.multir.corpus.CorpusInformationSpecification;
import edu.washington.multir.corpus.CorpusInformationSpecification.SentDocNameInformation.SentDocName;
import edu.washington.multir.corpus.CorpusInformationSpecification.SentGlobalIDInformation.SentGlobalID;
import edu.washington.multir.data.Argument;
import edu.washington.multir.data.Extraction;
import edu.washington.multir.data.ExtractionAnnotation;
import edu.washington.multir.featuregeneration.DefaultFeatureGeneratorWithFIGER;
import edu.washington.multir.featuregeneration.DefaultFeatureGeneratorConcatFIGER;
import edu.washington.multir.featuregeneration.DefaultFeatureGeneratorIndepFIGER;
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
public class ManualEvaluation {
	
	private static Set<String> targetRelations = null;
	
	public static void main (String[] args) throws ParseException, ClassNotFoundException, InstantiationException, IllegalAccessException, NoSuchMethodException, SecurityException, IllegalArgumentException, InvocationTargetException, SQLException, IOException{

		
		List<String> arguments  = new ArrayList<String>();
		for(String arg: args){
			arguments.add(arg);
		}
		
		CorpusInformationSpecification cis = CLIUtils.loadCorpusInformationSpecification(arguments);
		FeatureGenerator fg = CLIUtils.loadFeatureGenerator(arguments);
		ArgumentIdentification ai = CLIUtils.loadArgumentIdentification(arguments);
		SententialInstanceGeneration sig = CLIUtils.loadSententialInformationGeneration(arguments);
		
		
		String testCorpusDatabasePath = arguments.get(0);
		String multirModelPath = arguments.get(1);
		String annotationsInputFilePath = arguments.get(2);
		String evaluationRelationsFilePath = arguments.get(3);
		
		targetRelations = EvaluationUtils.loadTargetRelations(evaluationRelationsFilePath);
		
		//load test corpus
		Corpus c = new Corpus(testCorpusDatabasePath,cis,true);
		DocumentExtractor de = new DocumentExtractor(multirModelPath,fg,ai,sig);
		
		//if corpus object is full corpus, we may specify to look at train or test
		//partition of it based on a input file representing the names of the test documents
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
		List<Extraction> extractions = getExtractions(c,ai,sig,de);
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
	
	private static List<Extraction> getExtractions(Corpus c,
			ArgumentIdentification ai, SententialInstanceGeneration sig,
			DocumentExtractor de) throws SQLException, IOException {
		List<Extraction> extrs = new ArrayList<Extraction>();
		Iterator<Annotation> docs = c.getDocumentIterator();
		Map<Integer,String> ftID2ftMap = ModelUtils.getFeatureIDToFeatureMap(de.getMapping());
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
					Pair<Triple<String,Double,Double>,Map<Integer,Double>> extrResult = 
					de.extractFromSententialInstanceWithFeatureScores(p.first, p.second, sentence, doc);
					if(extrResult != null){
						Triple<String,Double,Double> extrScoreTripe = extrResult.first;
						Map<Integer,Double> featureScores = extrResult.second;
						String rel = extrScoreTripe.first;
						if(targetRelations.contains(rel)){
							String docName = sentence.get(SentDocName.class);
							String senText = sentence.get(CoreAnnotations.TextAnnotation.class);
							Integer sentNum = sentence.get(SentGlobalID.class);
							Extraction e = new Extraction(p.first,p.second,docName,rel,sentNum,extrScoreTripe.third,senText);
							e.setFeatureScoreList(EvaluationUtils.getFeatureScoreList(featureScores, ftID2ftMap));
							extrs.add(e);
						}
					}
				}
				sentenceCount++;
			}
		}
		return EvaluationUtils.getUniqueList(extrs);
	}
}
