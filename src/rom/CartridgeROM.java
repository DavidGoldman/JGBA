package rom;

import static utils.ByteUtils.string;
import static utils.LoadException.assertion;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import utils.LoadException;

/**
 * This class represents a game/ROM cartridge. Normally this will be read from
 * a file (see {@link #parse(File)}). This class verifies various checksums/assertions
 * according to the specifications found at GBATEK.
 * 
 * @author David Goldman
 * @see <a href="http://nocash.emubase.de/gbatek.htm">GBATEK</a>
 */
public class CartridgeROM {

	public static final int HEADER_LENGTH = 0xBF; //Header is 191 bytes long
	public static final int MAX_LENGTH = 0x2000000; //Max file size is 32 MB

	private static final byte[] NINTENDO =   { 
		  36,   -1,   -82,    81,   105,  -102,   -94,   33,    61,  -124,  -126,   10,  -124, 
		 -28,    9,   -83,    17,    36,  -117,  -104,  -64,  -127,   127,    33,  -93,    82, 
		 -66,   25,  -109,     9,   -50,    32,    16,   70,    74,    74,    -8,   39,    49, 
		 -20,   88,   -57,   -24,    51,  -126,   -29,  -50,   -65,  -123,   -12,  -33,  -108, 
		 -50,   75,     9,   -63,  -108,    86,  -118,  -64,    19,   114,   -89,   -4,   -97, 
		-124,   77,   115,   -93,   -54,  -102,    97,   88,  -105,   -93,    39,   -4,     3, 
		-104,  118,    35,    29,   -57,    97,     3,    4,   -82,    86,   -65,   56,  -124, 
		   0,   64,   -89,    14,    -3,    -1,    82,   -2,     3,   111,  -107,   48,   -15, 
		-105,   -5,   -64,  -123,    96,   -42,  -128,   37,   -87,    99,   -66,    3,     1, 
		  78,   56,   -30,    -7,   -94,    52,    -1,  -69,    62,     3,    68,  120,     0, 
		-112,  -53,  -120,    17,    58,  -108,   101,  -64,   124,    99,  -121,  -16,    60, 
		 -81,  -42,    37,   -28,  -117,    56,    10,  -84,   114,    33,   -44,   -8,     7 
	}; 
	
	public static CartridgeROM parse(File file) throws LoadException {
		assertion(file.exists(), "File does not exist!");
		assertion(file.canRead(), "Unable to read file!");
		assertion(file.length() > HEADER_LENGTH, "File is too small!");
		assertion(file.length() <= MAX_LENGTH, "File is too big!");

		try(FileInputStream stream = (new FileInputStream(file))) {
			byte[] rom = new byte[(int) file.length()];
			stream.read(rom);
			return new CartridgeROM(rom);
		} catch (IOException e) {
			throw new LoadException(e.getClass().getSimpleName() + ": " + e.getMessage());
		}
	}

	public final byte[] rom;
	public final String title, code, dev;
	public final boolean debug;
	public final byte hardware, type, ver, checksum;

	public CartridgeROM(byte[] rom) throws LoadException {
		this.rom = rom;
		this.title = string(rom, 0xA0, 0xab);
		this.code = string(rom, 0xAC, 0xAF);
		this.dev = string(rom, 0xB0, 0xB1);
		this.hardware = rom[0xB3];
		this.type = rom[0xB4];
		this.ver = rom[0xBC];
		this.checksum = rom[0xBD];
		this.debug = rom[0x9C] == (byte)0xA3; /*(rom[0x9C] & 2) == 2 && (rom[0x9C] & 128) == 128*/

		assertion(nintendo(), "Nintendo Logo Invalid!");
		assertion(checksum(), "Checksum failed!");
	}
	
	public void printInfo() {
		System.out.println("TITLE: " + title + "\nGAMECODE: " + code + "\nDEV: " + dev);
		System.out.println("HARDWARE: " + hardware + "\nTYPE: " + type + "\nVER: " + ver);
		System.out.println("CHECKSUM: " + checksum + "\nDEBUG: " + debug);
	}
	
	private boolean nintendo() {
		boolean valid = true;
		for (int i = 0x4; i <= 0x9B; ++i)
			valid &= rom[i] == NINTENDO[i-4];
		valid &= (rom[0x9C] & 0b01111101) == 0x21; //Debugging Enabled (see above)
		valid &= rom[0x9D] == NINTENDO[0x99];
		valid &= (rom[0x9E] & 0b11111100) == 0xF8; //Cartridge Key (TODO)
		valid &= rom[0x9F] == NINTENDO[0x9B];
		return valid;
	}
	
	private boolean checksum() {
		byte sum = 0;
		for (int i = 0xA0; i <= 0xBC; ++i)
			sum -= rom[i];
		sum = (byte) ((sum - 0x19) & 0xff);
		return sum == checksum;
	}
}
