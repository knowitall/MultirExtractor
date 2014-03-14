package edu.washington.multir.corpus;

import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.ErasureUtils;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.Triple;

public class SentFreebaseNotableTypeInformation implements SentInformationI{

	public static final class FreebaseNotableTypeAnnotation implements CoreAnnotation<List<Triple<Pair<Integer,Integer>,String,String>>>{
		@Override
		public Class<List<Triple<Pair<Integer, Integer>, String,String>>> getType() {
			return ErasureUtils.uncheckedCast(List.class);
		}
	}
	@Override
	public void read(String s, CoreMap c) {
		List<Triple<Pair<Integer,Integer>,String,String>> notableTypeData = new ArrayList<>();
		String[] data = s.split("\\s+");
		for(int i = 3; i < data.length; i+=4){
			Integer tokenStart = Integer.parseInt(data[i-3]);
			Integer tokenEnd = Integer.parseInt(data[i-2]);
			String type = data[i-1];
			String mid = data[i];
			type = type.replaceAll("__", "_");
			Pair<Integer,Integer> offsetPair = new Pair<>(tokenStart,tokenEnd);
			Triple<Pair<Integer,Integer>,String,String> t = new Triple<>(offsetPair,type,mid);
			notableTypeData.add(t);
		}
		c.set(FreebaseNotableTypeAnnotation.class, notableTypeData);
	}

	@Override
	public String write(CoreMap c) {
		List<Triple<Pair<Integer,Integer>,String,String>> notableTypeData = c.get(FreebaseNotableTypeAnnotation.class);
		StringBuilder sb = new StringBuilder();
		for(Triple<Pair<Integer,Integer>,String,String> notableType : notableTypeData){
			sb.append(notableType.first.first);
			sb.append(" ");
			sb.append(notableType.first.second);
			sb.append(" ");
			sb.append(notableType.second);
			sb.append(" ");
			sb.append(notableType.third);
			sb.append(" ");
		}
		if(sb.length() > 0){
			sb.setLength(sb.length()-1);
		}
		return sb.toString();
	}

	@Override
	public String name() {
		return this.getClass().getSimpleName().toUpperCase();
	}

}
