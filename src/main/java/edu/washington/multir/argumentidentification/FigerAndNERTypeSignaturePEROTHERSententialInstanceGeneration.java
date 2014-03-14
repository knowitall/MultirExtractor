package edu.washington.multir.argumentidentification;

import edu.washington.multir.util.TypeConstraintUtils;

public class FigerAndNERTypeSignaturePEROTHERSententialInstanceGeneration extends
FigerAndNERTypeSignatureSententialInstanceGeneration {
	
	private static FigerAndNERTypeSignaturePEROTHERSententialInstanceGeneration instance = null;
	private FigerAndNERTypeSignaturePEROTHERSententialInstanceGeneration(String arg1Type, String arg2Type){
		super(arg1Type,arg2Type);
	}
	public static FigerAndNERTypeSignaturePEROTHERSententialInstanceGeneration getInstance(){
		if(instance == null) {
			instance = new FigerAndNERTypeSignaturePEROTHERSententialInstanceGeneration(TypeConstraintUtils.GeneralType.PERSON,
					TypeConstraintUtils.GeneralType.OTHER);
		}
		return instance;
		}
}