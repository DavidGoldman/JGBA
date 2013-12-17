package utils;

import cpu.Condition;

public class ByteUtils {
	
	public static String hex(byte b) {
		return String.format("%02X", b & 0xFF);
	}
	
	public static String hex(int i) {
		return String.format("%08X", i);
	}
	
	public static char ascii(byte b) {
		return (char) (b & 0xff);
	}
	
	public static String string(byte[] b, int min, int max) {
		StringBuilder sb = new StringBuilder();
		for(int i = min; i <= max; ++i)
			if (b[i] != 0)
				sb.append(ByteUtils.ascii(b[i]));
		return sb.toString();
	}
	
	public static String opString(byte most, byte b, byte c, byte least) {
		return Condition.toString((byte)((most >>> 4) & 0xF)) + " " + 
			hex((byte) (most & 0xf)) + " " + hex(b) + " " + hex(c) + " " + hex(least);
	}
	
}
