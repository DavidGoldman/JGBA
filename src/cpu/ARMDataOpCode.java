package cpu;

/**
 * The logical operations (AND, EOR, TST, TEQ, ORR, MOV, BIC, MVN) perform the logical action
 * on all corresponding bits of the operand or operands to produce the result. 
 * <p>
 * 	If the S bit is set (and Rd is not 15): <ul>
 *	<li>V flag in the CPSR will be unaffected</li>
 * 	<li>C flag will be set to the carry out (or preserved when the shift operation is LSL #0)</li>
 * 	<li>Z flag will be set if and only if the result is all zeros</li>
 * 	<li>N flag will be set to the logical value of bit 31 of the result</li>
 * </ul>
 * 
 * The arithmetic operations (SUB, RSB, ADD, ADC, SBC, RSC, CMP, CMN) treat each operand as
 * a 32 bit integer (signed/unsigned, doesn't matter).
 * <p>
 * If the S bit is set (and Rd is not 15): <ul>
 * 	<li>V flag in the CPSR will be set if an overflow occurs into bit 31 of the result;
 * 		this may be ignored if the operands were considered unsigned, but warns of a possible
 * 		error if the operands were 2's complement signed. </li>
 * 	<li>C flag will be set to the carry out of bit 31 of the ALU </li>
 * 	<li>Z flag will be set if and only if the result was zero </li>
 *  <li>N flag will be set to the value of bit 31 of the result (indicating a negative result
 *  	if the operands are considered to be 2's complement signed).  </li>
 *  </ul>
 * 
 * @author David Goldman
 * @see <a href="http://bear.ces.cwru.edu/eecs_382/ARM7-TDMI-manual-pt2.pdf">ARM7TDMI manual</a>
 */
public class ARMDataOpCode {
	
	/**
	 * operand1 AND operand2
	 */
	public static final byte AND = 0;
	
	/**
	 * operand1 EOR operand2
	 */
	public static final byte EOR = 1;
	
	/**
	 * operand1 - operand2
	 */
	public static final byte SUB = 2;
	
	/**
	 * operand2 - operand1
	 */
	public static final byte RSB = 3;
	
	/**
	 * operand1 + operand2
	 */
	public static final byte ADD = 4;
	
	/**
	 * operand1 + operand2 + carry
	 */
	public static final byte ADC = 5;
	
	/**
	 * operand1 - operand2 + carry - 1
	 */
	public static final byte SBC = 6;
	
	/**
	 * operand2 - operand1 + carry - 1
	 */
	public static final byte RSC = 7;
	
	/**
	 * operand1 AND operand2; result is NOT written
	 */
	public static final byte TST = 8;
	
	/**
	 * operand1 EOR operand2; result is NOT written
	 */
	public static final byte TEQ = 9;
	
	/**
	 * operand1 - operand2; result is NOT written
	 */
	public static final byte CMP = 10;
	
	/**
	 * operand1 + operand2; result is NOT written
	 */
	public static final byte CMN = 11;
	
	/**
	 * operand1 OR operand2
	 */
	public static final byte ORR = 12;
	
	/**
	 * operand2 (operand1 is ignored)
	 */
	public static final byte MOV = 13;
	
	/**
	 * operand1 AND NOT operand2 (bit clear)
	 */
	public static final byte BIC = 14;
	
	/**
	 * NOT operand2 (operand1 is ignored)
	 */
	public static final byte MVN = 15;
	
	public static String toString(byte op) {
		switch(op) {
		case AND: return "AND";
		case EOR: return "EOR";
		case SUB: return "SUB";
		case RSB: return "RSB";
		case ADD: return "ADD";
		case ADC: return "ADC";
		case SBC: return "SBC";
		case RSC: return "RSC";
		case TST: return "TST";
		case TEQ: return "TEQ";
		case CMP: return "CMP";
		case CMN: return "CMN";
		case ORR: return "ORR";
		case MOV: return "MOV";
		case BIC: return "BIC";
		case MVN: return "MVN";
		default: return "INVALID";
		}
	}
}
