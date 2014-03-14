package edu.washington.multir.argumentidentification;

import java.util.List;

import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.Triple;
import edu.washington.multir.data.Argument;
import edu.washington.multir.data.KBArgument;
import edu.washington.multir.knowledgebase.KnowledgeBase;

/**
 * RelationMatching interface returns the triples
 * of two arguments and their shared relation.
 * @author jgilme1
 *
 */
public interface RelationMatching {
	public List<Triple<KBArgument,KBArgument,String>> matchRelations(
			List<Pair<Argument,Argument>> sententialInstances, 
			KnowledgeBase KB, CoreMap sentence, Annotation doc);
}
