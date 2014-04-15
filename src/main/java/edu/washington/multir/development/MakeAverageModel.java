package edu.washington.multir.development;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.washington.multir.multiralgorithm.DenseVector;
import edu.washington.multir.multiralgorithm.Mappings;
import edu.washington.multir.multiralgorithm.Model;
import edu.washington.multir.multiralgorithm.Parameters;

public class MakeAverageModel {
	
	
	private static Map<Integer,Map<Integer,Double>> relFeatureScoreMap;
	private static Mappings m;
	private static Model model;
	
	public static void main (String[] args) throws IOException{
		
		List<File> randomizedModels = new ArrayList<>();
		
		for(int i =0; i < (args.length-1); i++){
			randomizedModels.add(new File(args[i]));
		}
		run(randomizedModels,new File(args[args.length-1]));
	}
	
	
	public static void run(List<File> randomizedModels, File modelFile) throws IOException{
		
		String dir1 = randomizedModels.get(0).getAbsolutePath();
		initializeMapping(dir1);
		System.out.println("Initialized maps");

		int size =0;
		for(int i =0; i < randomizedModels.size(); i++){
			String randomDir = randomizedModels.get(0).getAbsolutePath();
			collectValuesFromRandomDir(randomDir);
			System.out.println(i+1 + " random dirs added");
			size++;
		}
		
		//get average
		writeNewModel(modelFile.getAbsolutePath(),size);
		System.out.println("Wrote new model");
	}

	private static void writeNewModel(String newModelDir, int size) throws IOException {
		String newMappingFile = newModelDir + "/mapping";
		String newParamsFile = newModelDir + "/params";
		m.write(newMappingFile);
		
		Parameters p = new Parameters();
		p.model = model;
		p.init();
		
		for(Integer rel : relFeatureScoreMap.keySet()){
			Map<Integer,Double> ftScoreMap = relFeatureScoreMap.get(rel);
			DenseVector d = p.relParameters[rel];
			for(int dIndex =0; dIndex < d.vals.length; dIndex++){
				//assign average feature score
				d.vals[dIndex] = (ftScoreMap.get(dIndex)/(double)size);
			}
		}
		
		p.serialize(newParamsFile);
	}

	private static void collectValuesFromRandomDir(String randomDir) throws IOException {
		
		String parameterFile = randomDir + "/params";
		String mappingFile = randomDir + "/mapping";
		Mappings thisMapping = new Mappings();
		thisMapping.read(mappingFile);
		
		Map<Integer,String> relIdToRelString = new HashMap<>();
		Map<Integer,String> ftId2FtString = new HashMap<>();
		
		for(String rel: thisMapping.getRel2RelID().keySet()){
			relIdToRelString.put(thisMapping.getRel2RelID().get(rel), rel);
		}
		for(String ft: thisMapping.getFt2ftId().keySet()){
			ftId2FtString.put(thisMapping.getFt2ftId().get(ft),ft);
		}
		
		Parameters p = new Parameters();
		p.model = model;
		p.init();
		FileInputStream fis = new FileInputStream(new File(parameterFile));
		p.deserialize(fis);
		fis.close();
		
		for(int rel = 0; rel < p.relParameters.length; rel++){
			int globalRel = m.getRelationID(relIdToRelString.get(rel), false);
				for(int feat = 0; feat < p.relParameters[rel].vals.length; feat++){
					int globalFeat = m.getFeatureID(ftId2FtString.get(feat), false);
					Double prev = relFeatureScoreMap.get(globalRel).get(globalFeat);
					Double newScore = prev + p.relParameters[rel].vals[feat];
					Map<Integer,Double> featScoreMap = relFeatureScoreMap.get(globalRel);
					featScoreMap.put(globalFeat, newScore);
				}
		}
		
	}

	private static void initializeMapping(String dir1) throws IOException {
		relFeatureScoreMap = new HashMap<Integer,Map<Integer,Double>>();
		m = new Mappings();
		String mappingFile = dir1+"/mapping";
		Mappings dir1Mapping = new Mappings();
		dir1Mapping.read(mappingFile);
		

		
		List<String> relStrings = new ArrayList<>(dir1Mapping.getRel2RelID().keySet());
		Collections.sort(relStrings);
		m.getRelationID("NA", true);
		relFeatureScoreMap.put(0, new HashMap<Integer,Double>());
		int relCount =0;
		for(String rel : relStrings){
			if(rel.equals("NA")) continue;
			m.getRelationID(rel, true);
			relCount++;
			relFeatureScoreMap.put(relCount, new HashMap<Integer,Double>());
		}
		
		
		List<String> featureStrings = new ArrayList<>(dir1Mapping.getFt2ftId().keySet());
		Collections.sort(featureStrings);
		
		int featCount =0;
		for(String feat: featureStrings){
			m.getFeatureID(feat, true);
			for(int rel =0 ; rel <=relCount; rel++){
				relFeatureScoreMap.get(rel).put(new Integer(featCount), new Double(0.0));
			}
			featCount++;
		}
		
		String modelFile = dir1 + "/model";
		model = new Model();
		model.read(modelFile);

		
	}

}
