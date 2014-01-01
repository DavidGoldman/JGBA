package utils;

public class ByteUtils {
	
	public static String hexs(int _short) {
		return String.format("%04X", _short & 0xFFFF);
	}
	
	public static String hexi(int i) {
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
	
}
