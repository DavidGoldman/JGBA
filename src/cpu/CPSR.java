package cpu;

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

	public int mapHighRegister(byte reg) {
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
}
