package edu.washington.multir.corpus;

import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.ErasureUtils;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.Triple;

/**
 * Extra SentInformationI Class used to represent Named Entity Links
 * @author jgilme1
 *
 */
public class SentNamedEntityLinkingInformation implements SentInformationI{

	public static final class NamedEntityLinkingAnnotation implements CoreAnnotation<List<Triple<Pair<Integer,Integer>,String,Float>>>{
		@Override
		public Class<List<Triple<Pair<Integer,Integer>,String,Float>>> getType() {
		      return ErasureUtils.uncheckedCast(List.class);	
		}
	}
	@Override
	public void read(String s, CoreMap c) {	
		String[] nelStringInformation = s.split("\\s+");
		List<Triple<Pair<Integer,Integer>,String,Float>> nelInformation = new ArrayList<>();

		int numGroups = (nelStringInformation.length > 0) ? nelStringInformation.length/5 : 0;
		int currIndex = 0;
		for(int group = 1; group <= numGroups; group++){
			currIndex = (group-1)*5;
			Integer tokenStart = Integer.parseInt(nelStringInformation[currIndex]);
			Integer tokenEnd = Integer.parseInt(nelStringInformation[currIndex+1]);
			String entityID = nelStringInformation[currIndex+3].replaceAll("_+", "_");
			Float confidence = Float.parseFloat(nelStringInformation[currIndex+4]);
			Pair<Integer,Integer> tokenOffsets = new Pair<>(tokenStart,tokenEnd);
			Triple<Pair<Integer,Integer>,String,Float> nelAnnotation = new Triple<>(tokenOffsets,entityID,confidence);
			nelInformation.add(nelAnnotation);
		}
		
		c.set(NamedEntityLinkingAnnotation.class, nelInformation);

	}
	@Override
	public String write(CoreMap c) {
		StringBuilder sb = new StringBuilder();
		List<Triple<Pair<Integer,Integer>,String,Float>> nelInformation = c.get(NamedEntityLinkingAnnotation.class);
		for(Triple<Pair<Integer,Integer>,String,Float> triple : nelInformation){
			sb.append(String.valueOf(triple.first));
			sb.append(" ");
			sb.append(triple.second);
			sb.append(" ");
			sb.append(String.valueOf(triple.third));
			sb.append(" ");
		}
		if(sb.length() > 1){
			sb.deleteCharAt(sb.length()-1);
		}
		return sb.toString().trim();
	}
	@Override
	public String name() {
		return this.getClass().getSimpleName().toUpperCase();
	}
}
