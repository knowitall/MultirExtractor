package edu.washington.multir.argumentidentification;

import java.util.List;

import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;
import edu.washington.multir.data.Argument;

public interface SententialInstanceGeneration {
	
    public List<Pair<Argument,Argument>> generateSententialInstances(List<Argument> arguments, CoreMap sentence);
}
