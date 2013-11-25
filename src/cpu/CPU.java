package cpu;

public class CPU {
	
	public static interface IProcessor {
		/**
		 * Execute an operation.
		 * @param pc Program counter for this operation
		 */
		public void execute(int pc);
	}

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
			{ 0, 0, 0, 0, 0, 0 }, //r13 (SP - STACK PNTR, r13_fiq, r13_svc, r13_abt, r13_irq, r13_und
			{ 0, 0, 0, 0, 0, 0 }, //r14 (LR) - LINK REG, r14_fiq, r14_svc, r14_abt, r14_irq, r14_und
			{ 0 } //r15 (PC) - PROGRAM COUNTER
	};
	
	private final int[] spsr = { 0, 0, 0, 0, 0 }; // SPSR (Saved Program Status Register - PRIVELEGED ONLY): SPSR_fiq, SPSR_svc, SPSR_abt, SPSR_irq, SPSR_und

	private final ARMProcessor arm;
	private final THUMBProcessor thumb;
	protected int cpsr; //CPSR (CONDITION CODE FLAGS AND CURRENT MODE BITS)
	
	public CPU() {
		arm = new ARMProcessor(this);
		thumb = new THUMBProcessor(this);
	}

	protected int getReg(int num) {
		return 0;
	}

	protected int getStatusReg(int num) {
		return 0;
	}

	protected byte accessROM(int pc) {
		return 0;
	}

}
