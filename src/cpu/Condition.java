package cpu;

/**
 * This class evaluates a condition given the value of the CPSR (status register).
 * There are 15 different conditions (0-14), the last condition, represent by 0xF (15)
 * is reserved according to the ARM7TDMI specifications and should not be used. 
 * 
 * @author David Goldman
 * @see <a href="http://bear.ces.cwru.edu/eecs_382/ARM7-TDMI-manual-pt2.pdf">ARM7TDMI manual</a>
 */
public class Condition {
	/**
	 * Equal - Z set
	 */
	public static final byte EQ = 0;
	/**
	 * Not equal - Z clear
	 */
	public static final byte NE = 1;
	
	/**
	 * Unsigned higher or same - C set
	 */
	public static final byte CS = 2;
	/**
	 * Unsigned lower - C clear
	 */
	public static final byte CC = 3;
	
	/**
	 * Negative - N set
	 */
	public static final byte MI = 4;
	/**
	 * Positive or zero - N clear
	 */
	public static final byte PL = 5;
	
	/**
	 * Overflow - V set
	 */
	public static final byte VS = 6;
	/**
	 * No overflow - V clear
	 */
	public static final byte VC = 7;
	
	/**
	 * Unsigned higher - C set AND Z clear
	 */
	public static final byte HI = 8;
	/**
	 * Unsigned lower or same - C clear OR Z set
	 */
	public static final byte LS = 9;
	
	/**
	 * Greater or equal - N equals V
	 */
	public static final byte GE = 10;
	/**
	 * Less than - N not equal to V
	 */
	public static final byte LT = 11;
	
	/**
	 * Greater than - Z clear AND N equals V
	 */
	public static final byte GT = 12;
	/**
	 * Less than or equal - Z set OR N not equal to V
	 */
	public static final byte LE = 13;
	
	/**
	 * Always
	 */
	public static final byte AL = 14;
	
	/**
	 * Evaluates the condition given the cpsr, which contains the condition bits. 
	 * 
	 * @param cond Condition byte (0 - 14)
	 * @param cpsr Status Register
	 * Bit 31    Bit 30    Bit 29    Bit 28  ...		Bit 0
	 *    N        Z         C          V
	 * @return Whether the given condition is true
	 * @throws IllegalArgumentException When cond is not valid
	 */
	public static boolean condition(byte cond, int cpsr) throws IllegalArgumentException {
		switch(cond) {
		case EQ: return (cpsr & 0x40000000) == 0x40000000; //Z set
		case NE: return (cpsr & 0x40000000) == 0; //Z clear
		
		case CS: return (cpsr & 0x20000000) == 0x20000000; //C set
		case CC: return (cpsr & 0x20000000) == 0; //C clear
		
		case MI: return (cpsr & 0x80000000) == 0x80000000; //N set
		case PL: return (cpsr & 0x80000000) == 0; //N clear
		
		case VS: return (cpsr & 0x10000000) == 0x10000000; //V set
		case VC: return (cpsr & 0x10000000) == 0; //V clear
		
		case HI: return (cpsr & 0x20000000) == 0x20000000 && (cpsr & 0x40000000) == 0; //C set AND Z clear
		case LS: return (cpsr & 0x20000000) == 0 || (cpsr & 0x40000000) == 0x40000000; //C clear OR Z set
		
		case GE: return (cpsr & 0x80000000) >> 3 == (cpsr & 0x10000000); //N equals V
		case LT: return (cpsr & 0x80000000) >> 3 != (cpsr & 0x10000000); //N not equal to V
		
		case GT: return (cpsr & 0x40000000) == 0 && (cpsr & 0x80000000) >> 3 == (cpsr & 0x10000000); //Z clear AND (N equals V)
		case LE: return (cpsr & 0x40000000) == 0x40000000 || (cpsr & 0x80000000) >> 3 != (cpsr & 0x10000000); //Z set OR (Not equal to V)
		
		case AL: return true;
		default:
			return false;
			//throw new IllegalArgumentException("Invalid condition " + cond);
		}
	}
	
	public static String toString(byte cond) {
		switch(cond) {
		case EQ: return "EQ";
		case NE: return "NE";
		case CS: return "CS";
		case CC: return "CC";
		case MI: return "MI";
		case PL: return "PL";
		case VS: return "VS";
		case VC: return "VC";
		case HI: return "HI";
		case LS: return "LS";
		case GE: return "GE";
		case LT: return "LT";
		case GT: return "GT";
		case LE: return "LE";
		case AL: return "AL";
		default: return "UNDEFINED";
		}
	}
}
