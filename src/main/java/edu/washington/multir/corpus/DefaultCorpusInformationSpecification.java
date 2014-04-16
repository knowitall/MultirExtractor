package edu.washington.multir.corpus;

import java.util.ArrayList;
import java.util.List;
import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.ErasureUtils;
import edu.stanford.nlp.util.Triple;

/**
 * This class extends CustomCorpusInformationSpecification and adds
 * the necessary information for the current baseline Multir System
 * @author jgilme1
 *
 */
public class DefaultCorpusInformationSpecification extends
		CustomCorpusInformationSpecification {

	//adds custom sentenceInformation and tokenInformation
	//to the corpus representation
	public DefaultCorpusInformationSpecification(){
		super();
		sentenceInformation.add(sentenceOffsetInformationInstance);
	    sentenceInformation.add(sentenceDependencyInformationInstance);
		tokenInformation.add(tokenNERInformationInstance);
		tokenInformation.add(tokenOffsetInformationinstance);
		tokenInformation.add(tokenPOSInformationInstance);
		tokenInformation.add(chunkInformationInstance);
		
	}
	private TokenNERInformation tokenNERInformationInstance = new TokenNERInformation();
	public static final class TokenNERInformation implements TokenInformationI{
		private TokenNERInformation(){}
		
		@Override
		public void read(String line, List<CoreLabel> tokens) {
			String [] tokenValues = line.split("\\s+");
			if(tokenValues.length == tokens.size()){
				for(int i =0; i < tokens.size(); i++){
					CoreLabel token = tokens.get(i);
					String tokenValue = tokenValues[i];
					token.set(CoreAnnotations.NamedEntityTagAnnotation.class, tokenValue);
				}
			}
			else{
				for(CoreLabel token: tokens){
					token.set(CoreAnnotations.NamedEntityTagAnnotation.class,null);
				}
			}
		}

		@Override
		public String write(List<CoreLabel> tokens) {
			StringBuilder sb = new StringBuilder();
			for(CoreLabel token: tokens){
				String tokenValue = token.get(CoreAnnotations.NamedEntityTagAnnotation.class);
				if(tokenValue != null){
					sb.append(tokenValue);
				}
				else{
					return "";
				}
				sb.append(" ");
			}
			return sb.toString().trim();
		}

		@Override
		public String name() {
			return "TOKENNERINFORMATION";
		}

	}
	
	private TokenOffsetInformation tokenOffsetInformationinstance = new TokenOffsetInformation();
	public static final class TokenOffsetInformation implements TokenInformationI{
		
		public static final class SentenceRelativeCharacterOffsetBeginAnnotation implements CoreAnnotation<Integer>{
			@Override
			public Class<Integer> getType() {
				return Integer.class;
			}
		}
		
		public static final class SentenceRelativeCharacterOffsetEndAnnotation implements CoreAnnotation<Integer>{
			@Override
			public Class<Integer> getType() {
				return Integer.class;
			}
		}
		
		@Override
		public void read(String line, List<CoreLabel> tokens) {
			String[] tokenValues = line.split("\\s+");
			if(tokenValues.length != tokens.size()){
				for(CoreLabel token : tokens){
					token.set(SentenceRelativeCharacterOffsetBeginAnnotation.class, null);
					token.set(SentenceRelativeCharacterOffsetEndAnnotation.class,null);
				}
			}
			else{
			  for(int i =0; i < tokens.size(); i++){
				  String tokenValue = tokenValues[i];
				  CoreLabel token = tokens.get(i);
				  String[] offsetValues = tokenValue.split(":");
				  if(offsetValues.length == 2){
					Integer start = Integer.parseInt(offsetValues[0]);
					Integer end = Integer.parseInt(offsetValues[1]);
					token.set(SentenceRelativeCharacterOffsetBeginAnnotation.class,start);
					token.set(SentenceRelativeCharacterOffsetEndAnnotation.class,end);
				  }
				  else{
					token.set(SentenceRelativeCharacterOffsetBeginAnnotation.class, null);
					token.set(SentenceRelativeCharacterOffsetEndAnnotation.class,null); 
				  }
			  }
			}
		}

		@Override
		public String write(List<CoreLabel> tokens) {
			StringBuilder sb = new StringBuilder();
			
			for(CoreLabel token : tokens){
				Integer start = token.get(SentenceRelativeCharacterOffsetEndAnnotation.class);
				Integer end = token.get(SentenceRelativeCharacterOffsetEndAnnotation.class);
				if(start != null && end != null){
					sb.append(String.valueOf(start));
					sb.append(String.valueOf(";"));
					sb.append(String.valueOf(end));
					sb.append(String.valueOf(" "));
				}
				else{
					sb.append("");
				}
			}
			
			return sb.toString().trim();

		}

		@Override
		public String name() {
			return this.getClass().getSimpleName().toUpperCase();
		}
		
	}
	
	private TokenPOSInformation tokenPOSInformationInstance = new TokenPOSInformation();
	public static final class TokenPOSInformation implements TokenInformationI{

		@Override
		public void read(String line, List<CoreLabel> tokens) {
			String [] tokenValues = line.split("\\s+");
			if(tokenValues.length != tokens.size()){
				for(CoreLabel token : tokens){
					token.set(CoreAnnotations.PartOfSpeechAnnotation.class,null);
				}
			}
			else{
				for(int i =0; i < tokens.size(); i++){
					String posTag = tokenValues[i];
					CoreLabel token = tokens.get(i);
					token.set(CoreAnnotations.PartOfSpeechAnnotation.class, posTag);
				}
			}
		}

		@Override
		public String write(List<CoreLabel> tokens) {
			StringBuilder sb = new StringBuilder();
			for(CoreLabel token : tokens){
				String pos = token.get(CoreAnnotations.PartOfSpeechAnnotation.class);
				if(pos != null){
					sb.append(pos);
					sb.append(" ");
				}
			}
			return sb.toString().trim();
		}

		@Override
		public String name() {
			return this.getClass().getSimpleName().toUpperCase();
		}
	}
	
	private TokenChunkInformation chunkInformationInstance = new TokenChunkInformation();
	public static final class TokenChunkInformation implements TokenInformationI{

		@Override
		public void read(String line, List<CoreLabel> tokens) {
			String [] tokenValues = line.split("\\s+");
			if(tokenValues.length != tokens.size()){
				for(CoreLabel token : tokens){
					token.set(CoreAnnotations.ChunkAnnotation.class,null);
				}
			}
			else{
				for(int i =0; i < tokens.size(); i++){
					String posTag = tokenValues[i];
					CoreLabel token = tokens.get(i);
					token.set(CoreAnnotations.ChunkAnnotation.class, posTag);
				}
			}
		}

		@Override
		public String write(List<CoreLabel> tokens) {
			StringBuilder sb = new StringBuilder();
			for(CoreLabel token : tokens){
				String pos = token.get(CoreAnnotations.ChunkAnnotation.class);
				if(pos != null){
					sb.append(pos);
					sb.append(" ");
				}
			}
			return sb.toString().trim();
		}

		@Override
		public String name() {
			return this.getClass().getSimpleName().toUpperCase();
		}
	}
	
	private SentDependencyInformation sentenceDependencyInformationInstance = new SentDependencyInformation();	
	public static final class SentDependencyInformation implements SentInformationI{

		public static final class DependencyAnnotation implements CoreAnnotation<List<Triple<Integer,String,Integer>>>{
			@Override
			public Class<List<Triple<Integer, String, Integer>>> getType() {
			      return ErasureUtils.uncheckedCast(List.class);	
			}
		}
		@Override
		public void read(String s, CoreMap c) {	
			String[] dependencyInformation = s.split("\\|");
			List<Triple<Integer,String,Integer>> dependencyParseInformation = new ArrayList<>();
			for(String dependencyInfo : dependencyInformation){
				String[] parts = dependencyInfo.split("\\s+");
				if(parts.length ==3){
					Integer governor = Integer.parseInt(parts[0]);
					String depType = parts[1];
					Integer dependent = Integer.parseInt(parts[2]);
					Triple<Integer,String,Integer> triple = new Triple<>(governor,depType,dependent);
					dependencyParseInformation.add(triple);
				}
			}
			c.set(DependencyAnnotation.class, dependencyParseInformation);
		}
		@Override
		public String write(CoreMap c) {
			StringBuilder sb = new StringBuilder();
			List<Triple<Integer,String,Integer>> dependencyParseInformation = c.get(DependencyAnnotation.class);
			for(Triple<Integer,String,Integer> triple : dependencyParseInformation){
				sb.append(String.valueOf(triple.first));
				sb.append(" ");
				sb.append(triple.second);
				sb.append(" ");
				sb.append(String.valueOf(triple.third));
				sb.append("|");
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
	
	private SentOffsetInformation sentenceOffsetInformationInstance = new SentOffsetInformation();
	public static final class SentOffsetInformation implements SentInformationI{

		public static final class SentStartOffset implements CoreAnnotation<Integer>{
			@Override
			public Class<Integer> getType() {
				return Integer.class;
			}
		}
		
		public static final class SentEndOffset implements CoreAnnotation<Integer>{
			@Override
			public Class<Integer> getType() {
				return Integer.class;
			}
		}
		
		@Override
		public void read(String s, CoreMap c) {
			String[] values = s.split("\\s+");
			if(values.length == 2){
				Integer startOffset = Integer.parseInt(values[0]);
				Integer endOffset = Integer.parseInt(values[1]);
				c.set(SentStartOffset.class, startOffset);
				c.set(SentEndOffset.class,endOffset);
			}
			else{
				c.set(SentStartOffset.class,null);
				c.set(SentEndOffset.class, null);
			}
		}

		@Override
		public String write(CoreMap c) {
				Integer start = c.get(SentStartOffset.class);
				Integer end = c.get(SentEndOffset.class);
				if(start == null || end == null){
					return "";
				}
				else{
					StringBuilder sb = new StringBuilder();
					sb.append(String.valueOf(start));
					sb.append(" ");
					sb.append(String.valueOf(end));
					return sb.toString();
				}
		}
		@Override
		public String name() {
			return this.getClass().getSimpleName().toUpperCase();
		}
	}
}
