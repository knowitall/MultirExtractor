package edu.washington.multir.eval;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.stanford.nlp.util.Pair;
import edu.washington.multir.multiralgorithm.DenseVector;
import edu.washington.multir.multiralgorithm.Mappings;
import edu.washington.multir.multiralgorithm.Model;
import edu.washington.multir.multiralgorithm.Parameters;
import edu.washington.multir.util.ModelUtils;

public class PrintFeatureScoresForRelation {
	
	
	
	
	public static void main(String[] args) throws IOException{
		
		String pathToMultirDir = args[0];
		String rel = args[1];
		String outputFile = args[2];
		
		//load in mapping and parameters
		Mappings m = new Mappings();
		m.read(pathToMultirDir+"/mapping");
		
		Model model = new Model();
		model.read(pathToMultirDir + "/model");
		
		Parameters params = new Parameters();
		params.model = model;
		params.deserialize(pathToMultirDir + "/params");
		
		Integer relKey = m.getRelationID(rel, false);
		
		Map<Integer,String> idToFeatureMap = ModelUtils.getFeatureIDToFeatureMap(m);
		BufferedWriter bw = new BufferedWriter(new FileWriter(new File(outputFile)));
		
		
		double[] featureScores = params.relParameters[relKey].vals;
		List<Pair<Integer,Double>> featureScorePairs = new ArrayList<Pair<Integer,Double>>();
		for(int i =0; i < featureScores.length; i++){
			featureScorePairs.add(new Pair<Integer,Double>(i,featureScores[i]));
		}
		
		//sort scores from highest to lowest
		Collections.sort(featureScorePairs, new Comparator<Pair<Integer,Double>>(){

			@Override
			public int compare(Pair<Integer, Double> arg0,
					Pair<Integer, Double> arg1) {
				return arg1.second.compareTo(arg0.second);
			}
			
		});
		
		
		//print scores with feature strings:
		
		for(Pair<Integer,Double> p : featureScorePairs){
			String featureString = idToFeatureMap.get(p.first);
			bw.write(featureString + "\t" + p.second + "\n");
		}

		bw.close();
		
	}

}
