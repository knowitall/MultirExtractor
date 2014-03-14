package edu.washington.multir.corpus;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.stanford.nlp.dcoref.CorefChain;
import edu.stanford.nlp.dcoref.CorefCluster;
import edu.stanford.nlp.dcoref.CorefCoreAnnotations;
import edu.stanford.nlp.dcoref.Dictionaries;
import edu.stanford.nlp.dcoref.Mention;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.IntTuple;

public class DefaultCorpusInformationSpecificationWithNELAndCoref extends
		DefaultCorpusInformationSpecificationWithNEL {
	
	
	public DefaultCorpusInformationSpecificationWithNELAndCoref(){
		super();
		this.documentInformation.add(docCorefInformation);
	}
	
	private DocCorefInformation docCorefInformation = new DocCorefInformation();
	public static final class DocCorefInformation implements DocumentInformationI{

		@Override
		public void read(String s, Annotation doc) {
			if(!s.equals("NULL")){
			String[] lines = s.split("\n");
			Map<Integer,CorefChain> corefAnnotationMap = new HashMap<>();
			Map<Integer,Set<Mention>> corefMentionMap = new HashMap<>();
			
			List<CoreMap> sentences = doc.get(CoreAnnotations.SentencesAnnotation.class);
			//create mentions
			for(String line : lines){
				String[] values = line.split("\t");
				Integer corefClusterID = Integer.parseInt(values[1]);
				Integer mentionID = Integer.parseInt(values[2]);
				Integer sentID = Integer.parseInt(values[3]);
				Integer localSentID = Integer.parseInt(values[4]);
				Integer mentionTokenStartIndex = Integer.parseInt(values[5]);
				Integer mentionTokenEndIndex = Integer.parseInt(values[6]);
				Integer mentionTokenHeadIndex = Integer.parseInt(values[7]);
				String redundantPositionInfo = values[8];
				String mentionString = values[9];
				String mentionType = values[10];
				String mentionNumber = values[11];
				String mentionGender = values[12];
				String mentionAnimacy = values[13];
				boolean representativeMention = Boolean.parseBoolean(values[14]);
				
				//instantiate new mention
				Mention m = new Mention();
				m.number  = Dictionaries.Number.valueOf(mentionNumber);
				m.animacy = Dictionaries.Animacy.valueOf(mentionAnimacy);
				m.gender = Dictionaries.Gender.valueOf(mentionGender);
				m.mentionType = Dictionaries.MentionType.valueOf(mentionType);
				m.sentNum = localSentID -1;
				m.startIndex = mentionTokenStartIndex-1; //indexed by 1
				m.endIndex = mentionTokenEndIndex-1;  // indexed by 1
				m.headIndex = mentionTokenHeadIndex-1;
				m.mentionID = mentionID;
				m.corefClusterID = corefClusterID;
				
				
				//set original span
				CoreMap sentence = sentences.get(m.sentNum);
				List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
				
				
				List<CoreLabel> mentionSpan = tokens.subList(m.startIndex, m.endIndex);
				m.headWord = tokens.get(m.headIndex);
				m.originalSpan = mentionSpan;
				
				if(corefMentionMap.containsKey(corefClusterID)){
				   corefMentionMap.get(corefClusterID).add(m);	
				}
				else{
					Set<Mention> mentionSet = new HashSet<Mention>();
					mentionSet.add(m);
					corefMentionMap.put(corefClusterID,mentionSet);
				}
			}
			
			//create mentionClusters
			
			for(Integer k : corefMentionMap.keySet()){
				Set<Mention> mentions = corefMentionMap.get(k);
				CorefCluster corefCluster = new CorefCluster(k,mentions);
				
				//generate IntTuple positions Map
				Map<Mention,IntTuple> positionsMap = new HashMap<Mention,IntTuple>();
				for(Mention m : mentions){
					int[] pos = new int[2];
					pos[0] = m.sentNum;
					pos[1] = m.headIndex;
					IntTuple it = new IntTuple(pos);
					positionsMap.put(m, it);
				}
				
				CorefChain corefChain = new CorefChain(corefCluster,positionsMap);
				corefAnnotationMap.put(k,corefChain);
			}
			
			doc.set(CorefCoreAnnotations.CorefChainAnnotation.class, corefAnnotationMap);
			}
			else{
				doc.set(CorefCoreAnnotations.CorefChainAnnotation.class, null);
			}
		}

		@Override
		public String write(Annotation doc) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public String name() {
			return this.getClass().getSimpleName().toUpperCase();
		}
		
	}

}
