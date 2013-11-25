package cpu;

/**
 * Represents the basic ALU operations for the THUMB mode. 
 * <p>
 * ALL instructions in this group set CPSR condition codes.
 * 
 * @author David Goldman
 * @see <a href="https://ece.uwaterloo.ca/~ece222/ARM/ARM7-TDMI-manual-pt3.pdf">THUMB Specifications</a>
 */
public class THUMBALUOpCode {
	
	/**
	 * AND Rd, Rs (Rd = Rd AND Rs)
	 */
	public static final byte AND = 0;
	
	/**
	 * EOR Rd, Rs (Rd = Rd EOR Rs)
	 */
	public static final byte EOR = 1;
	
	/**
	 * LSL Rd, Rs (Rd = Rd << Rs)
	 */
	public static final byte LSL = 2;
	
	/**
	 * LSR Rd, Rs (Rd = Rd >>> Rs)
	 * LSR = Logical Shift Right
	 */
	public static final byte LSR = 3;
	
	/**
	 * ASR Rd, Rs (Rd = Rd >> Rs)
	 * ASR = Arithmetic (signed) Shift Right
	 */
	public static final byte ASR = 4;
	
	/**
	 * ADC Rd, Rs (Rd = Rd + Rs + C-bit)
	 */
	public static final byte ADC = 5;
	
	/**
	 * SBC Rd, Rs (Rd = Rd - Rs - NOT C-bit)
	 */
	public static final byte SBC = 6;
	
	/**
	 * ROR Rd, Rs (Rd = Rd ROR Rs)
	 */
	public static final byte ROR = 7;
	
	/**
	 * TST Rd, Rs (Set condition codes on Rd AND Rs)
	 */
	public static final byte TST = 8;
	
	/**
	 * NEG Rd, Rs (Rd = -Rs)
	 */
	public static final byte NEG = 9;
	
	/**
	 * CMP Rd, Rs (Set condition codes on Rd - Rs)
	 */
	public static final byte CMP = 10;
	
	/**
	 * CMN Rd, Rs (Set condition codes on Rd + Rs)
	 */
	public static final byte CMN = 11; 
	
	/**
	 * ORR Rd, Rs (Rd = Rd OR Rs)
	 */
	public static final byte ORR = 12;
	
	/**
	 * MUL Rd, Rs (Rd = Rd * Rs)
	 */
	public static final byte MUL = 13;
	
	/**
	 * BIC Rd, Rs (Rd = Rd AND NOT Rs)
	 */
	public static final byte BIC = 14;
	
	/**
	 * MVN Rd, Rs (Rd = NOT Rs)
	 */
	public static final byte MVN = 15;
}
