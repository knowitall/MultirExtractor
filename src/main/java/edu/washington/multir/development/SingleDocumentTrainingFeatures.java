package edu.washington.multir.development;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.Triple;
import edu.washington.multir.argumentidentification.ArgumentIdentification;
import edu.washington.multir.argumentidentification.NERArgumentIdentification;
import edu.washington.multir.argumentidentification.NERSententialInstanceGeneration;
import edu.washington.multir.argumentidentification.SententialInstanceGeneration;
import edu.washington.multir.corpus.Corpus;
import edu.washington.multir.corpus.CorpusInformationSpecification;
import edu.washington.multir.corpus.DefaultCorpusInformationSpecification;
import edu.washington.multir.corpus.DefaultCorpusInformationSpecification.TokenOffsetInformation.SentenceRelativeCharacterOffsetBeginAnnotation;
import edu.washington.multir.corpus.DefaultCorpusInformationSpecification.TokenOffsetInformation.SentenceRelativeCharacterOffsetEndAnnotation;
import edu.washington.multir.data.Argument;
import edu.washington.multir.data.KBArgument;
import edu.washington.multir.featuregeneration.DefaultFeatureGenerator;
import edu.washington.multir.featuregeneration.FeatureGenerator;
import edu.washington.multir.preprocess.CorpusPreprocessing;

/**
 * Debugging App for comparing features generated at test time
 * vs train time
 * @author jgilme1
 *
 */
public class SingleDocumentTrainingFeatures {
	
	
	private static ArgumentIdentification ai;
	private static SententialInstanceGeneration sig;
	private static FeatureGenerator fg;

	public static void main(String[] args) throws SQLException, IOException, InterruptedException{
		CorpusInformationSpecification cis = new DefaultCorpusInformationSpecification();
		Corpus c = new Corpus(args[0],cis,true);
		ai = NERArgumentIdentification.getInstance();
		sig = NERSententialInstanceGeneration.getInstance();
		fg = new DefaultFeatureGenerator();
		
		
		Annotation traind = c.getDocument("XIN_ENG_20021028.0184.LDC2007T07");
		Annotation testd = CorpusPreprocessing.getTestDocument("/homes/gws/jgilme1/XIN_ENG_20021028.0184.LDC2007T07.sgm");
		compareDocuments(traind,testd);
		
		
		
	}
	
	
	public static void compareDocuments(Annotation doc1, Annotation doc2){
		
		List<CoreMap> doc1Sentences  = doc1.get(CoreAnnotations.SentencesAnnotation.class);
		List<CoreMap> doc2Sentences = doc2.get(CoreAnnotations.SentencesAnnotation.class);
		
		
		System.out.println("Doc 1 number of sentences  =" + doc1Sentences.size());
		System.out.println("Doc 2 number of sentences = " + doc2Sentences.size());
		
		for(int i =0; i < doc1Sentences.size(); i++){
			CoreMap doc1s = doc1Sentences.get(i);
			CoreMap doc2s = doc2Sentences.get(i);
			
			StringBuilder doc1TokenString = new StringBuilder();
			StringBuilder doc2TokenString = new StringBuilder();
			StringBuilder doc1NER = new StringBuilder();
			StringBuilder doc2NER = new StringBuilder();
			StringBuilder doc1Offsets = new StringBuilder();
			StringBuilder doc2Offsets = new StringBuilder();
			StringBuilder doc1POS = new StringBuilder();
			StringBuilder doc2POS = new StringBuilder();
			StringBuilder doc1Dependency = new StringBuilder();
			StringBuilder doc2Dependency = new StringBuilder();
			
			
			for(CoreLabel doc1Tok : doc1s.get(CoreAnnotations.TokensAnnotation.class)){
				String token = doc1Tok.value();
				String ner = doc1Tok.get(CoreAnnotations.NamedEntityTagAnnotation.class);
				Integer begOffset = doc1Tok.get(SentenceRelativeCharacterOffsetBeginAnnotation.class);
				Integer endOffset = doc1Tok.get(SentenceRelativeCharacterOffsetEndAnnotation.class);
				String pos = doc1Tok.get(CoreAnnotations.PartOfSpeechAnnotation.class);
				doc1TokenString.append(token);
				doc1TokenString.append(" ");
				doc1NER.append(ner);
				doc1NER.append(" ");
				doc1Offsets.append(begOffset);
				doc1Offsets.append(":");
				doc1Offsets.append(endOffset);
				doc1Offsets.append(" ");
				doc1POS.append(pos);
				doc1POS.append(" ");
			}
			
			
			for(CoreLabel doc2Tok : doc2s.get(CoreAnnotations.TokensAnnotation.class)){
				String token = doc2Tok.value();
				String ner = doc2Tok.get(CoreAnnotations.NamedEntityTagAnnotation.class);
				Integer begOffset = doc2Tok.get(SentenceRelativeCharacterOffsetEndAnnotation.class);
				Integer endOffset = doc2Tok.get(SentenceRelativeCharacterOffsetEndAnnotation.class);
				String pos = doc2Tok.get(CoreAnnotations.PartOfSpeechAnnotation.class);
				doc2TokenString.append(token);
				doc2TokenString.append(" ");
				doc2NER.append(ner);
				doc2NER.append(" ");
				doc2Offsets.append(begOffset);
				doc2Offsets.append(":");
				doc2Offsets.append(endOffset);
				doc2Offsets.append(" ");
				doc2POS.append(pos);
				doc2POS.append(" ");
			}
			
			System.err.println("Sentence number" + i);
			System.err.println(doc1s.get(CoreAnnotations.TextAnnotation.class));

			try{
			assert doc1TokenString.toString().trim().equals(doc2TokenString.toString().trim()) : "Tokens are equal";
			}
			catch(java.lang.AssertionError e){
				System.err.println("Tokens are not equal");
				System.err.println(doc1TokenString.toString().trim());
				System.err.println(doc2TokenString.toString().trim());
			}
			
			try{
			 assert doc1NER.toString().trim().equals(doc2NER.toString().trim()) : "NER is equal";
			}
			catch(java.lang.AssertionError e){
				System.err.println("NER is not equal");
				System.err.println(doc1NER.toString().trim());
				System.err.println(doc2NER.toString().trim());
			}
			
			try{
				assert doc1POS.toString().trim().equals(doc2POS.toString().trim()) : "POS is equal";
			}
			catch(java.lang.AssertionError e){
				System.err.println("POS is not equal");
				System.err.println(doc1POS.toString().trim());
				System.err.println(doc2POS.toString().trim());
			}
			
			
			List<Triple<Integer,String,Integer>> doc1Dep = 
					doc1s.get(DefaultCorpusInformationSpecification.SentDependencyInformation.DependencyAnnotation.class);
			List<Triple<Integer,String,Integer>> doc2Dep = 
					doc2s.get(DefaultCorpusInformationSpecification.SentDependencyInformation.DependencyAnnotation.class);
			
			Set<Triple<Integer,String,Integer>> doc1DepSet = new HashSet<>();
			Set<Triple<Integer,String,Integer>> doc2DepSet = new HashSet<>();
			doc1DepSet.addAll(doc1Dep);
			doc2DepSet.addAll(doc2Dep);
			
			Collections.sort(doc1Dep);
			Collections.sort(doc2Dep);
			
			StringBuilder doc1DepString = new StringBuilder();
			StringBuilder doc2DepString = new StringBuilder();
			
			for(Triple<Integer,String,Integer> t : doc1Dep){
				StringBuilder depString = new StringBuilder();
				depString.append(t.first);
				depString.append(" ");
				depString.append(t.second);
				depString.append(" ");
				depString.append(t.third);
				depString.append("| ");
				doc1DepString.append(depString.toString());
			}
			
			for(Triple<Integer,String,Integer> t : doc2Dep){
				StringBuilder depString = new StringBuilder();
				depString.append(t.first);
				depString.append(" ");
				depString.append(t.second);
				depString.append(" ");
				depString.append(t.third);
				depString.append("| ");
				doc2DepString.append(depString.toString());
			}
			
			try{
				assert doc1DepSet.equals(doc2DepSet) : "Dependencies are equal";
			}
			catch(java.lang.AssertionError e){
				System.err.println("Dependency is not equal");
				System.err.println(doc1DepString.toString().trim());
				System.err.println(doc2DepString.toString().trim());
			}
			
			
			if(i ==11){
				//get test time features
				List<Argument> args = ai.identifyArguments(doc2, doc2s);
				System.out.println("ARguments");
				for(Argument arg: args){
					System.out.println(arg.getArgName());
				}
				List<Pair<Argument,Argument>> sigs = sig.generateSententialInstances(args, doc2s);
				for(Pair<Argument,Argument> inst : sigs){
					Argument arg1 = inst.first;
					Argument arg2 = inst.second;
					String arg1ID = null;
					String arg2ID = null;
					if(arg1 instanceof KBArgument){
						arg1ID = ((KBArgument)arg1).getKbId();
					}
					if(arg2 instanceof KBArgument){
						arg2ID = ((KBArgument)arg2).getKbId();
					}
					List<String> features =
					fg.generateFeatures(arg1.getStartOffset(), 
							arg1.getEndOffset(), arg2.getStartOffset(), arg2.getEndOffset(),
							arg1ID, arg2ID,doc2s, doc2);
					
					System.out.println(arg1.getArgName() + "\t" + arg2.getArgName());
					for(String feature: features){
						System.out.print("\t" + feature);
					}
					System.out.println();
				}
			}
		}		
	}
}
