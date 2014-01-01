package cpu;

import utils.ByteUtils;

public class CPSR {

	/*
	 * The mode bit combinations - define the processor's operating mode.
	 * 
	 * Not all combinations of the mode define a valid processor mode.
	 */
	public static final byte USER = 0x10; 
	public static final byte FIQ = 0x11;
	public static final byte IRQ = 0x12;
	public static final byte SUPERVISOR = 0x13;
	public static final byte ABORT = 0x17;
	public static final byte UNDEFINED = 0x1B;
	public static final byte SYSTEM = 0x1F;

	public static String modeToString(byte mode) {
		switch(mode) {
		case USER: return "USER";
		case FIQ: return "FIQ";
		case IRQ: return "IRQ";
		case SUPERVISOR: return "SUPERVISOR";
		case ABORT: return "ABORT";
		case UNDEFINED: return "UNDEFINED";
		case SYSTEM: return "SYSTEM";
		default: return "INVALID";
		}
	}
	
	public static String toString(int spsr) {
		String s = ByteUtils.hexi(spsr) + ' ';
		if ((spsr & 0x80000000) == 0x80000000)
			s += 'N';
		if ((spsr & 0x40000000) == 0x40000000)
			s += 'Z';
		if ((spsr & 0x20000000) == 0x20000000)
			s += 'C';
		if ((spsr & 0x10000000) == 0x10000000)
			s += 'V';
		s += ' ';
		
		if ((spsr & 0x80) == 0x80)
			s += 'I';
		if ((spsr & 0x40) == 0x40)
			s += 'F';
		if ((spsr & 0x20) == 0x20)
			s += 'T';
		s += ' ';
		s += modeToString((byte) (spsr & 0x1F));
		return s;
	}

	/**
	 * Map from (mode & 0xF) to its index for the register banks of r13 and r14.
	 * Therefore, <ul>
	 * <li>USER->0</li>
	 * <li>FIQ->1</li>
	 * <li>IRQ->2</li>
	 * <li>SUPERVISOR->3</li>
	 * <li>ABORT->4</li>
	 * <li>UNDEFINED->5</li>
	 * <li>SYSTEM->0</li>
	 * <li>???->0</li>
	 * </ul>
	 */
	private static final byte[] R13_R14_MAP = { 0, 1, 2, 3, 0, 0, 0, 4, 0, 0, 0, 5, 0, 0, 0, 0 };

	/** 
	 * Map from (mode & 0xF) to its index for the register banks of r8-r12.
	 *  Therefore, <ul>
	 * <li>USER->0</li>
	 * <li>FIQ->1</li>
	 * <li>???->0</li>
	 * </ul>
	 */
	private static final byte[] R8_TO_R12_MAP = { 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };

	/**
	 * Map from (mode & 0xF) to 0 (for the PC).
	 */
	private static final byte[] ZERO_MAP = new byte[16]; 

	/**
	 * Banked register map.
	 */
	private static final byte[][] HIGH_REG_MAP = {
		R8_TO_R12_MAP, R8_TO_R12_MAP, R8_TO_R12_MAP, R8_TO_R12_MAP, R8_TO_R12_MAP, R13_R14_MAP, R13_R14_MAP, ZERO_MAP
	};

	protected boolean negative; //Negative/Less Than - Bit 31
	protected boolean zero; //Zero - Bit 30
	protected boolean carry; //Carry/Borrow/Extend - Bit 29
	protected boolean overflow; //Overflow - Bit 28

	//Bit 27-8 are RESERVED

	protected boolean irqDisable; //IRQ Interrupt Disable - Bit 7
	protected boolean fiqDisable; //FIQ Interrupt Disable - Bit 6
	protected boolean thumb; //THUMB State (if false, ARM state) - Bit 5

	protected byte mode; //5 mode bits - Bit 4-0

	public CPSR() {
		//TODO Initialize this correctly
		mode = SUPERVISOR;
	}

	public int mapHighRegister(int reg) {
		return HIGH_REG_MAP[reg & 0x7][mode & 0xF];
	}

	public int mapSPSRRegister() {
		return R13_R14_MAP[mode & 0xF] - 1;
	}
	
	 public void load(int cpsr) {
         negative = (cpsr & 0x80000000) == 0x80000000;
         zero = (cpsr & 0x40000000) == 0x40000000;
         carry = (cpsr & 0x20000000) == 0x20000000;
         overflow = (cpsr & 0x10000000) == 0x10000000;
         
         irqDisable = (cpsr & 0x80) == 0x80;
         fiqDisable = (cpsr & 0x40) == 0x40;
         thumb = (cpsr & 0x20) == 0x20;
         mode = (byte) (cpsr & 0x1F);
 }

	public void loadRestricted(int cpsr) {
		negative = (cpsr & 0x80000000) == 0x80000000;
		zero = (cpsr & 0x40000000) == 0x40000000;
		carry = (cpsr & 0x20000000) == 0x20000000;
		overflow = (cpsr & 0x10000000) == 0x10000000;

		if (mode != USER) {
			irqDisable = (cpsr & 0x80) == 0x80;
			fiqDisable = (cpsr & 0x40) == 0x40;
			//THUMB bit is only set from Branch X
			mode = (byte) (cpsr & 0x1F);
		}
	}
	
	public void loadFlagBits(int cpsr) {
		negative = (cpsr & 0x80000000) == 0x80000000;
		zero = (cpsr & 0x40000000) == 0x40000000;
		carry = (cpsr & 0x20000000) == 0x20000000;
		overflow = (cpsr & 0x10000000) == 0x10000000;
	}

	public int save() {
		int result = 0;
		if (negative)
			result |= 0x80000000;
		if (zero)
			result |= 0x40000000;
		if (carry)
			result |= 0x20000000;
		if (overflow)
			result |= 0x10000000;

		if (irqDisable)
			result |= 0x80;
		if (fiqDisable)
			result |= 0x40;
		if (thumb)
			result |= 0x20;
		result |= (mode & 0x1F);
		return result;
	}
	
	public String toString() {
		String s = ByteUtils.hexi(save()) + ' ';
		if (negative)
			s += 'N';
		if (zero)
			s += 'Z';
		if (carry)
			s += 'C';
		if (overflow)
			s += 'V';
		s += ' ';
		
		if (irqDisable)
			s += 'I';
		if (fiqDisable)
			s += 'F';
		if (thumb)
			s += 'T';
		s += ' ';
		s += modeToString(mode);
		return s;
	}
	
	protected int setAddFlags(int op1, int op2) {
		int result = op1 + op2;
		//Carry if unsigned value has Bit 32 SET
		carry = ((op1 & 0xffffffffL) + (op2 & 0xffffffffL) > 0xffffffffL);
		//Overflow if two positives result in a negative or two negatives result in a positive
		overflow = (op1 >= 0 && op2 >= 0 && result < 0) || (op1 < 0 && op2 < 0 && result >= 0);
		negative = (result < 0);
		zero = (result == 0);
		return result;
	}

	protected int setSubFlags(int op1, int op2) {
		int result = op1 - op2;
		//Odd, but must be true because a CMP calls this and CS (Carry SET) is unsigned higher or same o.0
		carry = ((op1 & 0xffffffffL) >= (op2 & 0xffffffffL));
		//Overflow if two positives result in a negative or two negatives result in a positive
		overflow = (op1 >= 0 && op2 <= 0 && result < 0) || (op1 < 0 && op2 > 0 && result >= 0);
		negative = (result < 0);
		zero = (result == 0);
		return result;
	}

	protected int setAddCarryFlags(int op1, int op2) {
		int result = op1 + op2 + ((carry) ? 1 : 0);
		//Carry if unsigned value has Bit 32 SET
		carry = ((op1 & 0xffffffffL) + (op2 & 0xffffffffL) + ((carry) ? 1 : 0) > 0xffffffffL);
		//Overflow if two positives result in a negative or two negatives result in a positive
		overflow = (op1 >= 0 && op2 >= 0 && result < 0) || (op1 < 0 && op2 < 0 && result >= 0);
		negative = (result < 0);
		zero = (result == 0);
		return result;
	}

	protected int setSubCarryFlags(int op1, int op2) {
		//SBC Rd, Rs (Rd = Rd - Rs - NOT C-bit)
		int result = op1 - op2 - ((carry) ? 0 : 1);
		//Unsigned higher or same including carry
		carry = ((op1 & 0xffffffffL) - (op2 & 0xffffffffL) - ((carry) ? 0 : 1) >= 0);
		//Overflow if two positives result in a negative or two negatives result in a positive
		overflow = (op1 >= 0 && op2 <= 0 && result < 0) || (op1 < 0 && op2 > 0 && result >= 0);
		negative = (result < 0);
		zero = (result == 0);
		return result;
	}
}
