package edu.washington.multir.util;

import java.util.HashMap;
import java.util.Map;

import edu.washington.multir.multiralgorithm.Mappings;

public class ModelUtils {
	
	
	public static Map<Integer,String> getFeatureIDToFeatureMap(Mappings m){
		Map<Integer,String> ftID2ftMap = new HashMap<>();
		Map<String,Integer> ft2ftIdMap = m.getFt2ftId();
		for(String f : ft2ftIdMap.keySet()){
			Integer k = ft2ftIdMap.get(f);
			ftID2ftMap.put(k, f);
		}
		return ftID2ftMap;
	}

}
