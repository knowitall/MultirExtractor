package edu.washington.multir.featuregeneration;

import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Triple;
import edu.washington.multir.corpus.DefaultCorpusInformationSpecification.SentDependencyInformation.DependencyAnnotation;
import edu.washington.multir.corpus.DefaultCorpusInformationSpecification.TokenOffsetInformation.SentenceRelativeCharacterOffsetBeginAnnotation;
import edu.washington.multir.corpus.DefaultCorpusInformationSpecification.TokenOffsetInformation.SentenceRelativeCharacterOffsetEndAnnotation;

public class DefaultFeatureGeneratorMinusNER implements FeatureGenerator{

	@Override
	public List<String> generateFeatures(Integer arg1StartOffset,
			Integer arg1EndOffset, Integer arg2StartOffset,
			Integer arg2EndOffset, String arg1ID, String arg2ID, CoreMap sentence, Annotation document) {
		//System.out.println("Generating features...");
		
		List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
		
		//initialize arguments
		String[] tokenStrings = new String[tokens.size()];
		String[] posTags = new String[tokens.size()];
		
		//initialize dependency parents to -1
		int[] depParents = new int[tokens.size()];
		for(int i = 0; i < depParents.length; i ++){
			depParents[i] = -1;
		}
		
		
		String[] depTypes = new String[tokens.size()];
		
		
		String arg1ner = "";
		String arg2ner = "";
		int[] arg1Pos = new int[2];
		int[] arg2Pos = new int[2];

		//iterate over tokens
		for(int i =0; i < tokens.size(); i++){
			
			CoreLabel token = tokens.get(i);
			
			//set the tokenString value
			tokenStrings[i] =token.value();
			
			//set the pos value
			String pos = token.get(CoreAnnotations.PartOfSpeechAnnotation.class);
			if(pos == null){
				posTags[i] = "";
			}
			else{
				posTags[i] = pos;
			}
			
			int begOffset =token.get(SentenceRelativeCharacterOffsetBeginAnnotation.class);
			int endOffset = token.get(SentenceRelativeCharacterOffsetEndAnnotation.class);

			// if the token matches the argument set the ner and argPos values
			if(begOffset == arg1StartOffset){
				String ner = token.get(CoreAnnotations.NamedEntityTagAnnotation.class);
				if(ner != null){
					arg1ner = ner;
				}
				arg1Pos[0] = i;
			}
			
			if(endOffset == arg1EndOffset){
				arg1Pos[1] = i;
			}
			
			
			if(begOffset == arg2StartOffset){
				String ner = token.get(CoreAnnotations.NamedEntityTagAnnotation.class);
				if(ner != null){
					arg2ner = ner;
				}
				arg2Pos[0] = i;
			}
			
			if(endOffset == arg2EndOffset){
				arg2Pos[1] = i;
			}
		}
		
//		System.out.println("TOKENS: ");
//		for(String tok: tokenStrings){
//			System.out.print(tok + " ");
//		}
//		System.out.print("\n");
//		System.out.println("TOKEN SIZE = " + tokens.size());
		
		//dependency conversions..
		List<Triple<Integer,String,Integer>> dependencyData = sentence.get(DependencyAnnotation.class);
		if(dependencyData != null){
			for(Triple<Integer,String,Integer> dep : dependencyData){
				int parent = dep.first -1;
				String type = dep.second;
				int child = dep.third -1;
	
				//child and parent should not be equivalent
				if(parent == child){
					parent = -1;
				}
				
				if(child < tokens.size()){
					depParents[child] = parent;
					depTypes[child] = type;
				}
				else{
					System.err.println("ERROR BETWEEN DEPENDENCY PARSE AND TOKEN SIZE");
					return new ArrayList<String>();
				}
			}
		}
		else{
			return new ArrayList<String>();
		}
		
		//add 1 to end Pos values
		arg1Pos[1] += 1;
		arg2Pos[1] += 1;
			
		arg1ner = "O";
		arg2ner = "O";
		return DefaultFeatureGenerator.originalMultirFeatures(tokenStrings, posTags, depParents, depTypes, arg1Pos, arg2Pos, arg1ner, arg2ner);

	}
}
