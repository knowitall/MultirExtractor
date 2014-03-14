package edu.washington.multir.data;

public class KBArgument extends Argument {
	private String kbid;
	
	public String getKbId(){return kbid;}
	
	public KBArgument(Argument arg, String kbid){
		super(arg);
		this.kbid = kbid;
	}
}
