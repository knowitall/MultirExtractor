package edu.washington.multir.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.stanford.nlp.util.Pair;
import edu.washington.multir.data.Extraction;
import edu.washington.multir.data.ExtractionAnnotation;

public class EvaluationUtils {
	
	
	public static void eval(List<Extraction> extractions,
			List<ExtractionAnnotation> annotations, Set<String> targetRelations) {
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

	
	public static void relByRelEvaluation(List<Extraction> extractions,
			List<ExtractionAnnotation> annotations,
			Set<String> targetRelations) {
		
		for(String rel: targetRelations){
			Set<String> relSubSet = new HashSet<>();
			relSubSet.add(rel);
			Pair<Double,Integer> precisionYieldPair = getPrecisionYield(extractions,annotations,relSubSet,false);
			System.out.println(rel);
			System.out.println("Precision = " + precisionYieldPair.first);
			System.out.println("Yield = " + precisionYieldPair.second);
			int count =0;
			for(Extraction e : extractions){
				if(e.getRelation().equals(rel)){
					count++;
				}
			}
			System.out.println("Extracted = " + count);
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
						List<Pair<String,Double>> featureScoreList = e.getFeatureScoreList();
						for(Pair<String,Double> p : featureScoreList){
							System.out.println(p.first + "\t" + p.second);
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
	
	public static List<Extraction> getUniqueList(List<Extraction> extrs) {
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
	
	public static List<Extraction> getDiff(List<Extraction> extractions,
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
	
	public  static Set<String> loadTargetRelations(String evaluationRelationsFilePath) throws IOException {
		BufferedReader br= new BufferedReader( new FileReader(new File(evaluationRelationsFilePath)));
		String nextLine;
		Set<String> targetRelations = new HashSet<String>();
		while((nextLine = br.readLine())!=null){
			targetRelations.add(nextLine.trim());
		}
		
		br.close();
		return targetRelations;
	}
	
	public static List<ExtractionAnnotation> loadAnnotations(
			String annotationsInputFilePath) throws IOException {
		List<ExtractionAnnotation> extrAnnotations = new ArrayList<ExtractionAnnotation>();
		List<Extraction> extrs = new ArrayList<Extraction>();
		BufferedReader br = new BufferedReader(new FileReader(new File(annotationsInputFilePath)));
		String nextLine;
		boolean duplicates = false;
		int lineCount =1;
		try{
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
				lineCount++;
			}
		}
		catch(Exception e){
			System.out.println("Error at line " + lineCount);
			throw e;
		}
		
		if(duplicates){
			br.close();
			throw new IllegalArgumentException("Annotations file contains multiple instances of the same extraction");
		}
		
		br.close();
		return extrAnnotations;
	}
	
	public static void writeExtractions(List<Extraction> diffExtractions,
			String diffOutputName) throws IOException {
		BufferedWriter bw = new BufferedWriter(new FileWriter(new File(diffOutputName)));
		for(Extraction e : diffExtractions){
			bw.write(e.toString()+"\n");
		}
		bw.close();
	}
	
	public static List<Pair<String,Double>> getFeatureScoreList(Map<Integer,Double> featureScores, Map<Integer,String> ftID2ftMap){
		
		List<Pair<String,Double>> featureScoreList = new ArrayList<>();
		for(Integer featureI: featureScores.keySet()){
			String featureString = ftID2ftMap.get(featureI);
			Double featureScore = featureScores.get(featureI);
			Pair<String,Double> featureScorePair = new Pair<>(featureString,featureScore);
			featureScoreList.add(featureScorePair);
		}
		return featureScoreList;
	}

}
