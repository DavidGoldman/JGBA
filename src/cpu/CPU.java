package cpu;

public class CPU {
	
	public static interface IProcessor {
		/**
		 * Execute an operation.
		 * @param pc Program counter for this operation
		 */
		public void execute(int pc);
	}

	/**
	 * Register 0-7 are "low" registers and are available in THUMB and ARM mode.
	 * Registers 8-15 are "high" registers and are available ONLY in ARM mode.
	 * Register 13 is the stack pointer.
	 * Register 14 is the link registers.
	 * Register 15 is the program counter.
	 */
	protected final int[][] regs =  {
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
			{ 0, 0, 0, 0, 0, 0 }, //r13 (SP - STACK PNTR, r13_fiq, r13_svc, r13_abt, r13_irq, r13_und
			{ 0, 0, 0, 0, 0, 0 }, //r14 (LR) - LINK REG, r14_fiq, r14_svc, r14_abt, r14_irq, r14_und
			{ 0 } //r15 (PC) - PROGRAM COUNTER
	};
	
	protected final int[] spsr = { 0, 0, 0, 0, 0 }; // SPSR (Saved Program Status Register - PRIVELEGED ONLY): SPSR_fiq, SPSR_svc, SPSR_abt, SPSR_irq, SPSR_und

	private final ARMProcessor arm;
	private final THUMBProcessor thumb;
	
	protected final CPSR cpsr; //CPSR (CONDITION CODE FLAGS AND CURRENT MODE BITS)
	
	public CPU() {
		arm = new ARMProcessor(this);
		thumb = new THUMBProcessor(this);
		cpsr = new CPSR();
	}

	/**
	 * Low registers cannot be banked, so no mode checking is done.
	 * 
	 * @param reg Register to access, ANDED with 0x7
	 * @return The value in (reg & 0x7)
	 */
	protected int getLowReg(byte reg) {
		return regs[reg & 0x7][0];
	}
	
	protected void setLowReg(byte reg, int value) {
		regs[reg & 0x7][0] = value;
	}

	protected int getStatusReg(int num) {
		return 0;
	}

	protected byte accessROM(int pc) {
		return 0;
	}

}
