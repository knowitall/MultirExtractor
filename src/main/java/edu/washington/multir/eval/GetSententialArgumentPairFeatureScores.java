package edu.washington.multir.eval;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.Triple;
import edu.washington.multir.argumentidentification.ArgumentIdentification;
import edu.washington.multir.argumentidentification.SententialInstanceGeneration;
import edu.washington.multir.corpus.Corpus;
import edu.washington.multir.corpus.CorpusInformationSpecification;
import edu.washington.multir.data.Argument;
import edu.washington.multir.data.Extraction;
import edu.washington.multir.data.ExtractionAnnotation;
import edu.washington.multir.featuregeneration.FeatureGenerator;
import edu.washington.multir.sententialextraction.DocumentExtractor;

public class GetSententialArgumentPairFeatureScores {
	
	private static Set<String> targetRelations = null;
	private static Map<Integer,String> ftID2ftMap = new HashMap<Integer,String>();
	
	
	public static void main(String[] args)throws ParseException, ClassNotFoundException, InstantiationException, IllegalAccessException, NoSuchMethodException, SecurityException, IllegalArgumentException, InvocationTargetException, SQLException, IOException{
		
		Options options = new Options();
		options.addOption("cis",true,"corpusInformationSpecification algorithm class");
		options.addOption("ai",true,"argumentIdentification algorithm class");
		options.addOption("sig",true,"sententialInstanceGeneration algorithm class");
		options.addOption("fg",true,"featureGeneration algorithm class");
		CommandLineParser parser = new BasicParser();
		CommandLine cmd = parser.parse(options, args);
		List<String> remainingArgs = cmd.getArgList();
		
		ClassLoader cl = ClassLoader.getSystemClassLoader();
		CorpusInformationSpecification cis = null;
		ArgumentIdentification ai = null;
		SententialInstanceGeneration sig = null;
		FeatureGenerator fg = null;
		
		
		String corpusInformationSpecificationName = cmd.getOptionValue("cis");
		String argumentIdentificationName = cmd.getOptionValue("ai");
		String sententialInstanceGenerationName = cmd.getOptionValue("sig");
		String featureGenerationName = cmd.getOptionValue("fg");
		
		if(corpusInformationSpecificationName != null){
			String corpusInformationSpecificationClassPrefix = "edu.washington.multir.corpus.";
			Class<?> c = cl.loadClass(corpusInformationSpecificationClassPrefix+corpusInformationSpecificationName);
			cis = (CorpusInformationSpecification) c.newInstance();
		}
		else{
			throw new IllegalArgumentException("corpusInformationSpecification Class Argument is invalid");
		}
		
		if(argumentIdentificationName != null){
			String argumentIdentificationClassPrefix = "edu.washington.multir.argumentidentification.";
			Class<?> c = cl.loadClass(argumentIdentificationClassPrefix+argumentIdentificationName);
			Method m = c.getMethod("getInstance");
			ai = (ArgumentIdentification) m.invoke(null);
		}
		else{
			throw new IllegalArgumentException("argumentIdentification Class Argument is invalid");
		}
		
		if(sententialInstanceGenerationName != null){
			String sententialInstanceClassPrefix = "edu.washington.multir.argumentidentification.";
			Class<?> c = cl.loadClass(sententialInstanceClassPrefix+sententialInstanceGenerationName);
			Method m = c.getMethod("getInstance");
			sig = (SententialInstanceGeneration) m.invoke(null);
		}
		else{
			throw new IllegalArgumentException("sententialInstanceGeneration Class Argument is invalid");
		}
		
		if(featureGenerationName != null){
			String featureGenerationClassPrefix = "edu.washington.multir.featuregeneration.";
			Class<?> c = cl.loadClass(featureGenerationClassPrefix+featureGenerationName);
			fg = (FeatureGenerator) c.newInstance();
		}
		else{
			throw new IllegalArgumentException("argumentIdentification Class Argument is invalid");
		}
		
		
		//remaining args are
		// 0 - TestCorpusDerbyDatabase
		// 1 - annotations input file
		// 2 - evaluation relations file
		
		String testCorpusDatabasePath = remainingArgs.get(0);
		String multirModelPath = remainingArgs.get(1);
		String evaluationRelationsFilePath = remainingArgs.get(2);
		
		
		loadTargetRelations(evaluationRelationsFilePath);
		
		//load test corpus
		Corpus c = new Corpus(testCorpusDatabasePath,cis,true);
		DocumentExtractor de = new DocumentExtractor(multirModelPath,fg,ai,sig);
		
		Map<String,Integer> ft2ftIdMap = de.getMapping().getFt2ftId();
		for(String f : ft2ftIdMap.keySet()){
			Integer k = ft2ftIdMap.get(f);
			ftID2ftMap.put(k, f);
		}
		
		Argument arg1 = new Argument("Mike Eruzione",171,184);
		Argument arg2 = new Argument("United States",201,214);
		Set<Integer> sentIds = new HashSet<Integer>();
		sentIds.add(27352779);
		Map<Integer,Pair<CoreMap,Annotation>> sentDocPairs = c.getAnnotationPairsForEachSentence(sentIds);
		Pair<CoreMap,Annotation> p = sentDocPairs.get(27352779);
		int rel = 1;

		Map<Integer,Double> featureScores = de.getFeatureScores(arg1, arg2, p.first, p.second,rel);
		for(Integer i : featureScores.keySet()){
			String feature = ftID2ftMap.get(i);
			System.out.println(feature + "\t" + featureScores.get(i));
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
}
