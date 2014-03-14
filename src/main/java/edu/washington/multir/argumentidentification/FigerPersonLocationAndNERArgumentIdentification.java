package edu.washington.multir.argumentidentification;

import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.util.CoreMap;
import edu.washington.multir.corpus.DefaultCorpusInformationSpecification.TokenOffsetInformation.SentenceRelativeCharacterOffsetEndAnnotation;
import edu.washington.multir.data.Argument;

public class FigerPersonLocationAndNERArgumentIdentification implements
		ArgumentIdentification {

	
	private static FigerPersonLocationAndNERArgumentIdentification instance = null;
	
	private FigerPersonLocationAndNERArgumentIdentification(){}
	public static FigerPersonLocationAndNERArgumentIdentification getInstance(){
		if(instance == null) instance = new FigerPersonLocationAndNERArgumentIdentification();
		return instance;
		}
	
	@Override
	public List<Argument> identifyArguments(Annotation d, CoreMap s) {
		List<Argument> figerArguments = FigerPersonLocationArgumentIdentification.getInstance().identifyArguments(d, s);
		List<Argument> nerArguments = NERArgumentIdentification.getInstance().identifyArguments(d,s);
		List<Argument> args = new ArrayList<Argument>();
		List<CoreLabel> tokens = s.get(CoreAnnotations.TokensAnnotation.class);
		args.addAll(figerArguments);
		for(Argument nerArg : nerArguments){
			if(!nerArg.intersectsWithList(figerArguments)){
				String ner = getNERString(nerArg,tokens);
				//System.out.println(ner);
				if(ner.equals("PERSON") || ner.equals("LOCATION")){
					args.add(nerArg);
				}
			}
		}
		
		
		
		return args;
	}
	
	
	public String getNERString(Argument arg, List<CoreLabel> tokens){
		
		for(CoreLabel tok : tokens){
			
			Integer endOffset = tok.get(SentenceRelativeCharacterOffsetEndAnnotation.class);
			if(endOffset.equals(arg.getEndOffset())){
				return tok.get(CoreAnnotations.NamedEntityTagAnnotation.class);
			}
			
		}
		return null;
		
	}

}
