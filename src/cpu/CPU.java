package cpu;

import utils.ByteUtils;
import cores.Waitstate;

public class CPU {

	public static interface IProcessor {
		/**
		 * Execute an operation.
		 * @param pc Program counter for this operation
		 */
		public void execute(int pc);
	}

	/**
	 * Register 0-7 are "low" registers.
	 * Registers 8-15 are "high" registers.
	 * Register 13 is the stack pointer.
	 * Register 14 is the link registers.
	 * Register 15 is the program counter.
	 * 
	 * Byte - 8 bit data
	 * Halfword - 16 bit data
	 * Word - 32 bit data
	 */
	private final int[][] regs =  {
			{ 0 }, //r0
			{ 0 }, //r1
			{ 0 }, //r2
			{ 0 }, //r3
			{ 0 }, //r4
			{ 0 }, //r5
			{ 0 }, //r6
			{ 0 }, //r7
			{ 0, 0 }, //r8, r8_fiq
			{ 0, 0 }, //r9, r9_fiq
			{ 0, 0 }, //r10, r10_fiq
			{ 0, 0 }, //r11, r11_fiq
			{ 0, 0 }, //r12, r12_fiq
			{ 0, 0, 0, 0, 0, 0 }, //r13 (SP) - STACK PNTR, r13_fiq, r13_irq, r13_svc, r13_abt, r13_und
			{ 0, 0, 0, 0, 0, 0 }, //r14 (LR) -   LINK REG, r14_fiq, r14_irq, r14_svc, r14_abt, r14_und
			{ 0 } //r15 (PC) - PROGRAM COUNTER
	};

	private final int[] spsr = { 0, 0, 0, 0, 0 }; // SPSR (Saved Program Status Register - PRIVELEGED ONLY): SPSR_fiq, SPSR_irq, SPSR_svc, SPSR_abt, SPSR_und

	private final ARMProcessor arm;
	private final THUMBProcessor thumb;
	protected final CPSR cpsr; //CPSR (CONDITION CODE FLAGS AND CURRENT MODE BITS)
	protected final Waitstate wait;
	
	/**
	 * The actual PC.
	 */
	private int pc;
	
	/**
	 * The instruction (ARM or THUMB) that is/was executing. 
	 */
	protected int execute;

	public CPU() {
		arm = new ARMProcessor(this);
		thumb = new THUMBProcessor(this);
		cpsr = new CPSR();
		wait = new Waitstate();
	}

	/**
	 * Load CPSR from current SPSR register. Should not be called from USER mode.
	 */
	protected void loadCPSR() {
		int index = cpsr.mapSPSRRegister();
		if (index != -1) 
			cpsr.load(spsr[index]);
	}

	/**
	 * Get the current SPSR register. If called from USER mode, returns the CPSR.
	 */
	protected int getSPSR() {
		int index = cpsr.mapSPSRRegister();
		return (index != -1) ? spsr[index] : cpsr.save();
	}

	/**
	 * Sets the current SPSR register. Should not be called from USER mode.
	 */
	protected void setSPSR(int val) {
		int index = cpsr.mapSPSRRegister();
		if (index != -1) 
			spsr[index] = val;
	}

	/**
	 * Modify the flag bits of the current SPSR register. Should not be called from USER mode.
	 */
	protected void modifySPSR(int val) {
		int index = cpsr.mapSPSRRegister();
		if (index != -1) //Force the old flag bits to 0, OR that with new flag bits
			spsr[index] = (spsr[index] & 0x0FFFFFFF) | (val & 0xF0000000);
	}

	/**
	 * Low registers cannot be banked, so no mode checking is done.
	 * 
	 * @param reg Register to access
	 * @return The value in (reg & 0x7)
	 */
	protected int getLowReg(int reg) {
		return regs[reg & 0x7][0];
	}

	protected void setLowReg(int reg, int value) {
		regs[reg & 0x7][0] = value;
	}

	/**
	 * Read from a high register, mode checking done because of banking.
	 * 
	 * @param reg Register MINUS 8, thus to access register 8, pass in 0
	 * @return The value in ((reg  & 0x7) + 8) (bank)
	 */
	protected int getHighReg(int reg) {
		return regs[(reg & 0x7) + 0x8][cpsr.mapHighRegister(reg)];
	}

	protected void setHighReg(int reg, int value) {
		regs[(reg & 0x7) + 0x8][cpsr.mapHighRegister(reg)] = value;
	}

	/**
	 * Read from a register.
	 * 
	 * @param reg Register to access (0-15)
	 * @return The value in (reg & 0xF) (bank)
	 */
	protected int getReg(int reg) {
		reg = reg & 0xF;
		return (reg <= 0x7) ? getLowReg(reg) : getHighReg(reg - 0x8);
	}

	protected void setReg(int reg, int value) {
		reg = reg & 0xF;
		if (reg <= 0x7)
			setLowReg(reg, value);
		else
			setHighReg(reg - 0x8, value);
	}

	protected int getUserReg(int reg) {
		return regs[reg & 0xF][0];
	}

	protected void setUserReg(int reg, int value) {
		regs[reg & 0xF][0] = value;
	}

	protected int getPC() {
		return regs[15][0];
	}

	/**
	 * @return The link register for the current mode.
	 */
	protected int getLR() {
		return regs[14][cpsr.mapHighRegister(6)];
	}

	protected void setLR(int val) {
		regs[14][cpsr.mapHighRegister(6)] = val;
	}

	/**
	 * @return The stack pointer for the current mode.
	 */
	protected int getSP() {
		return regs[13][cpsr.mapHighRegister(5)];
	}

	protected void setSP(int val) {
		regs[13][cpsr.mapHighRegister(5)] = val;
	}

	protected void branch(int address) {
		//TODO Update the PC
	}

	protected int read32(int address) {
		//TODO
		return 0;
	}

	protected void write32(int address, int val) {
		//TODO
	}

	protected int read16(int address) {
		//TODO 
		return 0;
	}

	protected void write16(int address, int val) {
		//TODO
	}

	protected int read8(int address) {
		//TODO
		return 0;
	}

	protected void write8(int address, int val) {
		//TODO
	}

	protected void softwareInterrupt(byte arg) {

	}

	protected void softwareInterrupt(int arg) {

	}

	protected void undefinedTrap() {

	}

	protected void undefinedInstr(String info) {
		if (cpsr.thumb) 
			System.err.println("WARNING: Undefined THUMB instruction " + ByteUtils.hex((short)execute) + " @[" + ByteUtils.hex(pc) + "]: " + info);
		else
			System.err.println("WARNING: Undefined ARM instruction " + ByteUtils.hex(execute) + " @[" + ByteUtils.hex(pc) + "]: " + info);
	}

	public void regDump() {
		System.out.println("---------------------------------------------REG DUMP---------------------------------------------");
		System.out.println("Actual PC: " + ByteUtils.hex(pc));
		System.out.println("PC: " + dr(15,0) + "\tLR: " + dr(14,0) + "\tSP: " + dr(13,0));
		System.out.println("CPSR: " +  cpsr.toString());
		System.out.println("SPSR_fiq: " + CPSR.toString(spsr[0]));
		System.out.println("SPSR_irq: " + CPSR.toString(spsr[1]));
		System.out.println("SPSR_svc: " + CPSR.toString(spsr[2]));
		System.out.println("SPSR_abt: " + CPSR.toString(spsr[3]));
		System.out.println("SPSR_und: " + CPSR.toString(spsr[4]));
		for (int i = 0; i <= 7; ++i)
			System.out.println("r" + i + ": " + dr(i,0));
		for (int i = 8; i <= 12; ++i)
			System.out.println("r" + i + ": " + dr(i,0) + "\tr" + i + "_fiq: " + dr(i,1));
		for (int i = 13; i <= 14; ++i)
			System.out.println("r" + i + "_fiq: " + dr(i,1) + "   r" + i + "_irq: " + dr(i,2) + "   r" + i + "_svc: " + dr(i,3) + "   r" + i + "_abt: " + dr(i,4) + "   r" + i + "_und: " + dr(i,5));
		System.out.println("---------------------------------------------END DUMP---------------------------------------------");
	}

	//Dump reg (hex value)
	private String dr(int r, int i) {
		return ByteUtils.hex(regs[r][i]);
	}
}
