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
import edu.washington.multir.util.FigerTypeUtils;


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
	private static Map<Integer,String> ftID2ftMap = new HashMap<Integer,String>();
	
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
		
		loadTargetRelations(evaluationRelationsFilePath);
		
		//load test corpus
		Corpus c = new Corpus(testCorpusDatabasePath,cis,true);
		DocumentExtractor de = new DocumentExtractor(multirModelPath,fg,ai,sig);
		
		Map<String,Integer> ft2ftIdMap = de.getMapping().getFt2ftId();
		for(String f : ft2ftIdMap.keySet()){
			Integer k = ft2ftIdMap.get(f);
			ftID2ftMap.put(k, f);
		}
		
		
		if(fg instanceof DefaultFeatureGeneratorWithFIGER | fg instanceof DefaultFeatureGeneratorConcatFIGER | fg instanceof DefaultFeatureGeneratorIndepFIGER){
			FigerTypeUtils.init();
		}
		
		long start = System.currentTimeMillis();
		List<Extraction> extractions = getExtractions(c,ai,sig,de);
		long end = System.currentTimeMillis();
		System.out.println("Got Extractions in " + (end-start));
		
		start = end;
		List<ExtractionAnnotation> annotations = loadAnnotations(annotationsInputFilePath);
		end = System.currentTimeMillis();
		System.out.println("Got Annotations in " + (end-start));

		start = end;

		List<Extraction> diffExtractions = getDiff(extractions,annotations);
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
			writeExtractions(diffExtractions,diffOutputName);
			throw new IllegalStateException("inputAnnotations do not include all of the extractions, tag the diff at "
					+ diffOutputName + " and merge with annotations");
		}
		else{
			eval(extractions,annotations);
		}
		
		if(fg instanceof DefaultFeatureGeneratorWithFIGER | fg instanceof DefaultFeatureGeneratorConcatFIGER | fg instanceof DefaultFeatureGeneratorIndepFIGER){
			FigerTypeUtils.close();
		}
	}

	private static void loadTargetRelations(String evaluationRelationsFilePath) throws IOException {
		BufferedReader br= new BufferedReader( new FileReader(new File(evaluationRelationsFilePath)));
		String nextLine;
		targetRelations = new HashSet<String>();
		while((nextLine = br.readLine())!=null){
			targetRelations.add(nextLine.trim());
		}
		
		br.close();
	}

	private static void eval(List<Extraction> extractions,
			List<ExtractionAnnotation> annotations) {
		System.out.println("evaluating...");
		
		//sort extractions
		Collections.sort(extractions, new Comparator<Extraction>(){
			@Override
			public int compare(Extraction e1, Extraction e2) {
				return e1.getScore().compareTo(e2.getScore());
			}
			
		});
		Collections.reverse(extractions);
		
		List<Pair<Double,Integer>> precisionYieldValues = new ArrayList<>();
		for(int i =1; i < extractions.size(); i++){
			Pair<Double,Integer> pr = getPrecisionYield(extractions.subList(0, i),annotations,targetRelations,false);
			precisionYieldValues.add(pr);
		}
		Pair<Double,Integer> pr = getPrecisionYield(extractions,annotations,targetRelations,true);
		
		System.out.println("Precision and Yield");
		for(Pair<Double,Integer> p : precisionYieldValues){
			System.out.println(p.first + "\t" + p.second);
		}
	}

	private static Pair<Double, Integer> getPrecisionYield(List<Extraction> subList,
			List<ExtractionAnnotation> annotations, Set<String> relationSet, boolean print) {
		
		int totalExtractions = 0;
		int correctExtractions = 0;
		
		for(Extraction e : subList){
			if(relationSet.contains(e.getRelation())){
				totalExtractions++;
				List<ExtractionAnnotation> matchingAnnotations = new ArrayList<>();
				for(ExtractionAnnotation ea : annotations){
					if(ea.getExtraction().equals(e)){
						matchingAnnotations.add(ea);
					}
				}
				if(matchingAnnotations.size() == 1){
					ExtractionAnnotation matchedAnnotation = matchingAnnotations.get(0);
					if(print){
						System.out.print(e + "\t" + e.getScore());
					}
					if(matchedAnnotation.getLabel()){
						correctExtractions++;
						if(print){
							System.out.print("\tCORRECT\n");
						}
					}
					else{
						if(print){
							System.out.print("\tINCORRECT\n");
						}
					}
					if(print){
						System.out.println("Features:");
						Map<Integer,Double> featureScores = e.getFeatureScores();
						for(Integer i : featureScores.keySet()){
							Double score = featureScores.get(i);
							String featName = ftID2ftMap.get(i);
							System.out.println(featName + "\t" + score);
						}
					}
				}
				else{
					StringBuilder errorStringBuilder = new StringBuilder();
					errorStringBuilder.append("There should be exactly 1 matching extraction in the annotation set\n");
					errorStringBuilder.append("There are " + matchingAnnotations.size() +" and they are listed below: ");
					for(ExtractionAnnotation ea : matchingAnnotations){
						errorStringBuilder.append(ea.getExtraction().toString()+"\n");
					}
					throw new IllegalArgumentException(errorStringBuilder.toString());
				}
			}
		}
		
		double precision = (totalExtractions == 0) ? 1.0 : ((double)correctExtractions /(double)totalExtractions);
		Pair<Double,Integer> p = new Pair<Double,Integer>(precision,correctExtractions);
		return p;
	}

	private static void writeExtractions(List<Extraction> diffExtractions,
			String diffOutputName) throws IOException {
		BufferedWriter bw = new BufferedWriter(new FileWriter(new File(diffOutputName)));
		for(Extraction e : diffExtractions){
			bw.write(e.toString()+"\n");
		}
		bw.close();
	}

	private static List<Extraction> getDiff(List<Extraction> extractions,
			List<ExtractionAnnotation> annotations) {
		
		List<Extraction> extrsNotInAnnotations = new ArrayList<Extraction>();
		
		
		for(Extraction e : extractions){
			boolean inAnnotation = false;
			for(ExtractionAnnotation ea : annotations){
				Extraction annoExtraction = ea.getExtraction();
				if(annoExtraction.equals(e)){
					inAnnotation = true;
				}
			}
			if(!inAnnotation){
				extrsNotInAnnotations.add(e);
			}
		}
		
		
		return extrsNotInAnnotations;
	}

	private static List<ExtractionAnnotation> loadAnnotations(
			String annotationsInputFilePath) throws IOException {
		List<ExtractionAnnotation> extrAnnotations = new ArrayList<ExtractionAnnotation>();
		List<Extraction> extrs = new ArrayList<Extraction>();
		BufferedReader br = new BufferedReader(new FileReader(new File(annotationsInputFilePath)));
		String nextLine;
		boolean duplicates = false;
		while((nextLine = br.readLine())!=null){
			ExtractionAnnotation extrAnnotation = ExtractionAnnotation.deserialize(nextLine);
			if(extrs.contains(extrAnnotation.getExtraction())){
				System.err.println("DUPLICATE ANNOTATION: " + nextLine);
				duplicates = true;
			}
			else{
			  extrs.add(extrAnnotation.getExtraction());
			  extrAnnotations.add(extrAnnotation);
			}
		}
		
		if(duplicates){
			br.close();
			throw new IllegalArgumentException("Annotations file contains multiple instances of the same extraction");
		}
		
		br.close();
		return extrAnnotations;
	}

	private static List<Extraction> getExtractions(Corpus c,
			ArgumentIdentification ai, SententialInstanceGeneration sig,
			DocumentExtractor de) throws SQLException {
		List<Extraction> extrs = new ArrayList<Extraction>();
		Iterator<Annotation> docs = c.getDocumentIterator();
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
							e.setFeatureScores(featureScores);
							extrs.add(e);
						}
					}
				}
				sentenceCount++;
			}
		}
		return getUniqueList(extrs);
	}

	private static List<Extraction> getUniqueList(List<Extraction> extrs) {
		List<Extraction> uniqueList = new ArrayList<Extraction>();
		
		for(Extraction extr: extrs){
			boolean unique = true;
			for(Extraction extr1: uniqueList){
				if(extr.equals(extr1)){
					unique =false;
				}
			}
			if(unique){
			 uniqueList.add(extr);
			}
		}
		return uniqueList;
	}
}
