package edu.washington.multir.argumentidentification;

import edu.washington.multir.util.TypeConstraintUtils;

/**
 * Populates <code>FigerAndNERTypeSignatureSententialInsantceGeneration</code> instance
 * with types of <code>GeneralType.ORGANIZATION</code> and <code>GeneralType.PERSON</code>
 * @author jgilme1
 *
 */
public class FigerAndNERTypeSignatureORGPERSententialInstanceGeneration extends
FigerAndNERTypeSignatureSententialInstanceGeneration {
	
	private static FigerAndNERTypeSignatureORGPERSententialInstanceGeneration instance = null;
	private FigerAndNERTypeSignatureORGPERSententialInstanceGeneration(String arg1Type, String arg2Type){
		super(arg1Type,arg2Type);
	}
	public static FigerAndNERTypeSignatureORGPERSententialInstanceGeneration getInstance(){
		if(instance == null) {
			instance = new FigerAndNERTypeSignatureORGPERSententialInstanceGeneration(TypeConstraintUtils.GeneralType.ORGANIZATION,
					TypeConstraintUtils.GeneralType.PERSON);
		}
		return instance;
		}
	
}
