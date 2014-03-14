package edu.washington.multir.argumentidentification;

import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Interval;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.Triple;
import edu.washington.multir.corpus.DefaultCorpusInformationSpecification.TokenOffsetInformation.SentenceRelativeCharacterOffsetBeginAnnotation;
import edu.washington.multir.corpus.DefaultCorpusInformationSpecification.TokenOffsetInformation.SentenceRelativeCharacterOffsetEndAnnotation;
import edu.washington.multir.corpus.DefaultCorpusInformationSpecificationWithNEL.SentNamedEntityLinkingInformation.NamedEntityLinkingAnnotation;
import edu.washington.multir.data.Argument;
import edu.washington.multir.data.KBArgument;

/**
 * NELArgumentIdentification returns every token span with
 * a link as an argument and all NER-recognized arguments as well.
 * @author jgilme1
 *
 */
public class NELNERConstrainedArgumentIdentification implements ArgumentIdentification{

	
	private static NELNERConstrainedArgumentIdentification instance = null;
	private static final double CONFIDENCE_THRESHOLD = 0.5;
	private static final boolean removeRedundantArguments = true;
	
	private NELNERConstrainedArgumentIdentification(){}
	public static NELNERConstrainedArgumentIdentification getInstance(){
		if(instance == null) instance = new NELNERConstrainedArgumentIdentification();
		return instance;
		}
	
	@Override
	public List<Argument> identifyArguments(Annotation d, CoreMap s) {
		//first grab all the NER arguments and store are nil links
		List<Argument> arguments = new ArrayList<>();
		List<KBArgument> nelArguments = new ArrayList<>();
		List<Argument> nerArguments = NERArgumentIdentification.getInstance().identifyArguments(d, s);
		List<CoreLabel> tokens = s.get(CoreAnnotations.TokensAnnotation.class);

		//then grab all the NERL arguments
		List<Triple<Pair<Integer,Integer>,String,Float>> nelAnnotation = s.get(NamedEntityLinkingAnnotation.class);
		if(nelAnnotation != null){
			for(Triple<Pair<Integer,Integer>,String,Float> trip : nelAnnotation){
				String id = trip.second;
				Float conf = trip.third;
				//if token span has a link create a new argument
				if(!id.equals("null")){
					if(conf > CONFIDENCE_THRESHOLD){
						//get character offsets
						Integer startTokenOffset = trip.first.first;
						Integer endTokenOffset = trip.first.second;
						if(startTokenOffset >= 0 && startTokenOffset < tokens.size() && endTokenOffset >= 0 && endTokenOffset < tokens.size()){
							Integer startCharacterOffset = tokens.get(startTokenOffset).get(SentenceRelativeCharacterOffsetBeginAnnotation.class);
							Integer endCharacterOffset = tokens.get(endTokenOffset-1).get(SentenceRelativeCharacterOffsetEndAnnotation.class);
								
							//only consider link if matches a NER type
							boolean isNer =false;
							for(Argument nerArgument: nerArguments){
								if(nerArgument.getStartOffset()==(startCharacterOffset) && nerArgument.getEndOffset()==(endCharacterOffset)){
									isNer=true;
								}
							}
							if(isNer){
								//get argument string
								String sentText = s.get(CoreAnnotations.TextAnnotation.class);
								if(sentText != null && startCharacterOffset !=null && endCharacterOffset!=null){
									String argumentString = sentText.substring(startCharacterOffset, endCharacterOffset);
									
									//add argument to list
									KBArgument nelArgument = new KBArgument(new Argument(argumentString,startCharacterOffset,endCharacterOffset),id);
									nelArguments.add(nelArgument);
								}
							}
						}
					}
				}
			}
		}
		if(removeRedundantArguments){
			arguments.addAll(removeRedundantArguments(nelArguments));
		}
		else{
		 arguments.addAll(nelArguments);
		}
		
		return arguments;
	}
	private List<Argument> removeRedundantArguments(List<KBArgument> nelArguments) {
		List<Argument> nonRedundantArguments = new ArrayList<>();
		for(int i =0; i < nelArguments.size(); i++){
			boolean redundant = false;
			KBArgument a = nelArguments.get(i);
			for(int j = 0; j < nelArguments.size(); j++){
				if(j != i){
					KBArgument b = nelArguments.get(j);
					if(b.getKbId().equals(a.getKbId())){
						if(argumentContains(b,a)){
							redundant = true;
						}
					}
				}
			}
			if(!redundant){
				nonRedundantArguments.add(a);
			}
		}
		return nonRedundantArguments;
	}
	
	/**
	 * Returns true if b contains a, false otherwise
	 * @param b
	 * @param a
	 * @return
	 */
	private boolean argumentContains(KBArgument b, KBArgument a) {
		int bStart = b.getStartOffset();
		int bEnd = b.getEndOffset();
		int aStart = a.getStartOffset();
		int aEnd = a.getEndOffset();
		
		if ((aStart > bStart) && (aEnd <= bEnd)){
			return true;
		}
		
		if((aEnd < bEnd) && (aStart >= bStart)){
			return true;
		}
		return false;
	}
}
