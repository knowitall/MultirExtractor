package edu.washington.multir.util;

public class GuidMidConversion {

	public static void main(String[] args) {
		System.out.println("9202a8c04000641f800000000029c277="+
                        GuidMidConversion.convertBackward("/m/02mjmr"));
	}

	private static String changeBaseAlphabet(String mid) {
		for (int i = 10; i < map2.length(); i++) {
			mid = mid.replace(map.charAt(i), map2.charAt(i));
		}
		return mid;
	}

	public static String convertBackward(String mid) {
		StringBuilder guid = new StringBuilder("9202a8c04000641f8");
		String tmp = Long.toHexString(Long.parseLong(
				changeBaseAlphabet(mid.substring(4)), 32));
		for (int i = 0; i < (32 - 17 - tmp.length()); i++) {
			guid.append("0");
		}
		guid.append(tmp);
		return guid.toString();
	}

	private static String map = "0123456789bcdfghjklmnpqrstvwxyz_";
	private static String map2 = "0123456789abcdefghijklmnopqrstuv";


}
