package cpu;

import static cpu.ARMDataOpCode.ADC;
import static cpu.ARMDataOpCode.ADD;
import static cpu.ARMDataOpCode.AND;
import static cpu.ARMDataOpCode.BIC;
import static cpu.ARMDataOpCode.CMN;
import static cpu.ARMDataOpCode.CMP;
import static cpu.ARMDataOpCode.EOR;
import static cpu.ARMDataOpCode.MOV;
import static cpu.ARMDataOpCode.MVN;
import static cpu.ARMDataOpCode.ORR;
import static cpu.ARMDataOpCode.RSB;
import static cpu.ARMDataOpCode.RSC;
import static cpu.ARMDataOpCode.SBC;
import static cpu.ARMDataOpCode.SUB;
import static cpu.ARMDataOpCode.TEQ;
import static cpu.ARMDataOpCode.TST;

/*
 * TODO: Implement edge case for STM 
 *      -Writeback with Rb included in Rlist: Store OLD base if Rb is FIRST entry in Rlist, otherwise store NEW base
 */
public class ARMProcessor implements CPU.IProcessor {

	private final CPU cpu;

	public ARMProcessor(CPU cpu) {
		this.cpu = cpu;
	}

	private int getRegDelayedPC(int reg) {
		return (reg & 0xF) == 0xF ? cpu.getPC() + 4 : cpu.getReg(reg);
	}

	private int getUserRegDelayedPC(int reg) {
		return (reg & 0xF) == 0xF ? cpu.getPC() + 4 : cpu.getUserReg(reg);
	}

	private void setRegSafe(int reg, int val) {
		if ((reg & 0xF) == 0xF)
			cpu.branch(val & 0xFFFFFFFC);
		else
			cpu.setReg(reg, val);
	}

	private void setUserRegSafe(int reg, int val) {
		if ((reg & 0xF) == 0xF)
			cpu.branch(val & 0xFFFFFFFC);
		else
			cpu.setUserReg(reg, val);
	}

	private void setRegSafeCPSR(int reg, int val) {
		if ((reg & 0xF) == 0xF) {
			cpu.loadCPSR();
			val &= (cpu.cpsr.thumb) ? 0xFFFFFFFE : 0xFFFFFFFC;
			cpu.branch(val);
		}
		else
			cpu.setReg(reg, val);
	}

	/**
	 * Given the pc, accesses the cartridge ROM and retrieves the current operation bytes.
	 * If the evaluated condition is true, an operation will be decoded and executed.
	 * 
	 * @param pc Program counter for this operation
	 */
	@Override
	public void execute(int pc) {
		//TODO Get instruction
		int instr = 0;
		cpu.execute = instr;

		/*Top four bits of top are the condition codes
		  Byte indices start at 0, domain [0, 31]*/
		if (Condition.condition((byte) (instr >>> 28), cpu.cpsr)) {
			byte bit27_to_24 = (byte) ((instr >>> 24) & 0xF);
			byte bit23_to_20 = (byte) ((instr >>> 20) & 0xF);

			switch(bit27_to_24) {
			case 0x0:
				if ((instr & 0x10) == 0 || (instr & 0x80) == 0) //Bit 4 or bit 7 clear
					dataProcPSRReg(instr);
				else if ((instr & 0x60) == 0) { //Bit 6,5 are CLEAR
					if ((bit23_to_20 & 0xC) == 0)
						multiply(instr);
					else if ((bit23_to_20 & 0x8) == 0x8)
						multiplyLong(instr);
					else
						cpu.undefinedInstr("Illegal multiply varation");
				}
				else { //Bit 6,5 are NOT both CLEAR, implies Halfword DT
					if ((bit23_to_20 & 0x4) == 0x4) //Bit 22 is SET
						halfwordDTImmPost(instr);
					else if ((instr & 0xF00) == 0) //Bit 22 is CLEAR AND Bit 11-8 CLEAR
						halfwordDTRegPost(instr);
					else
						cpu.undefinedInstr("Illegal (post) halfword data transfer variation");
				}
				break;
			case 0x1:
				if ((instr & 0xFFFFF0) == 0x2FFF10)  //0x12FFF1, Rn
					branchAndExchange(instr);
				else if ((instr & 0x10) == 0 || (instr & 0x80) == 0) //Bit 4 or bit 7 clear
					dataProcPSRReg(instr); 
				else if ((instr & 0x60) == 0) { //Bit 6,5 are CLEAR
					if ((bit23_to_20 & 0xB) == 0 && (instr & 0xF00) == 0) //Bit 27-25 CLEAR, Bit 24 SET, BIT 23,21,20 CLEAR, Bit 11-8 CLEAR
						singleDataSwap(instr);
					else
						cpu.undefinedInstr("Illegal single data swap variation");
				}
				else { //Bit 6,5 are NOT both CLEAR, implies Halfword DT
					if ((bit23_to_20 & 0x4) == 0x4) //Bit 22 is SET
						halfwordDTImmPre(instr);
					else if ((instr & 0xF00) == 0) //Bit 22 is CLEAR AND Bit 11-8 CLEAR
						halfwordDTRegPre(instr);
					else
						cpu.undefinedInstr("Illegal (pre) halfword data transfer variation");
				}
				break;
			case 0x2: dataProcPSRImm(instr); break;
			case 0x3: dataProcPSRImm(instr); break;
			case 0x4: singleDataTransferImmPost(instr); break;
			case 0x5: singleDataTransferImmPre(instr); break;
			case 0x6: 
				if ((instr & 0x10) == 0) /*Bit 4 CLEAR*/
					singleDataTransferRegPost(instr);
				else
					undefinedTrap();
				break;
			case 0x7:
				if ((instr & 0x10) == 0) /*Bit 4 CLEAR*/
					singleDataTransferRegPre(instr);
				else
					undefinedTrap();
				break;
			case 0x8: blockDataTransferPost(instr); break;
			case 0x9: blockDataTransferPre(instr); break;
			case 0xA: branch(instr); break;
			case 0xB: branchLink(instr); break;
			case 0xC: coprocDataTransferPost(instr); break;
			case 0xD: coprocDataTransferPre(instr); break;
			case 0xE:
				if ((instr & 0x10) == 0) /*Bit 4 CLEAR*/
					coprocDataOperation(instr);
				else
					coprocRegisterTransfer(instr);
				break;
			case 0xF: softwareInterrupt(instr); break;
			}

		}
	}

	private void branchAndExchange(int rn) {
		int address = cpu.getReg(rn);
		if ((address & 0x1) == 0)
			cpu.branch(address & 0xFFFFFFFC); //Word aligned
		else {
			cpu.cpsr.thumb = true;	
			cpu.branch(address & 0xFFFFFFFE); //Halfword aligned
		}
	}

	private void dataProcPSRReg(int instr) {
		byte opcode = (byte) ((instr >>> 21) & 0xF);
		int shift = (instr >>> 4) & 0xFF;
		//rd = (instr >>> 12) & 0xF
		//rn = (instr >>> 16) & 0xF
		//rm = instr & 0xF
		if ((instr & 0x100000) == 0x100000) //Bit 20 SET
			dataProcS(opcode, instr >>> 12, getRegDelayedPC(instr >>> 16), getOp2S(shift, instr));
		else if(opcode >= TST && opcode <= CMN) //PSR Transfer
			psrTransfer(instr);
		else
			dataProc(opcode, instr >>> 12, getRegDelayedPC(instr >>> 16), getOp2(shift, instr));
	}

	private int getOp2(int shift, int rm) {
		cpu.wait.internalCycle(); //Clock internal cycle
		byte type = (byte)((shift & 0x6) >>> 1); //type is bit 6-5
		
		if ((shift & 0x1) == 0) { //shift unsigned integer
			int imm5 = shift >>> 3; //bit 11-7
			switch(type) {
			case 0: return lsli(rm, imm5);
			case 1: return lsri(rm, imm5);
			case 2: return asri(rm, imm5);
			case 3: return rori(rm, imm5);
			}
		}
		else {
			int rs = shift >>> 4; //rs is bit 11-8
			switch(type) {
			case 0: return lslr(rm, rs);
			case 1: return lsrr(rm, rs);
			case 2: return asrr(rm, rs);
			case 3: return rorr(rm, rs);
			}
		}
		//Should never occur
		throw new RuntimeException();
		//return 0;
	}

	private int lsli(int rm, int imm5) {
		return cpu.getReg(rm) << imm5;
	}

	private int lsri(int rm, int imm5) {
		return (imm5 == 0) ? 0 : cpu.getReg(rm) >>> imm5; //LSR 0 is actually LSR #32
	}

	private int asri(int rm, int imm5) {
		if (imm5 == 0) //ASR 0 is actually ASR #32 -> same as ASR #31 value wise
			imm5 = 31; 
		return cpu.getReg(rm) >> imm5;
	}

	private int rori(int rm, int imm5) {
		int reg = cpu.getReg(rm);
		if (imm5 > 0) //ROR
			return (reg >>> imm5) | (reg << (32 - imm5));
		else //RRX
			return ((cpu.cpsr.carry) ? 0x80000000 : 0) | (reg >>> 1);
	}

	private int lslr(int rm, int rs) {
		int reg = getRegDelayedPC(rm);
		int shift = getRegDelayedPC(rs) & 0xFF;
		return (shift < 32) ? reg << shift : 0;
	}

	private int lsrr(int rm, int rs) {
		int reg = getRegDelayedPC(rm);
		int shift = getRegDelayedPC(rs) & 0xFF;
		return (shift < 32) ? reg >>> shift : 0;
	}

	private int asrr(int rm, int rs) {
		int reg = getRegDelayedPC(rm);
		int shift = getRegDelayedPC(rs) & 0xFF;
		if (shift > 31)
			shift = 31;
		return reg >> shift;
	}

	private int rorr(int rm, int rs) {
		int reg = getRegDelayedPC(rm);
		int shift = getRegDelayedPC(rs) & 0x1F;
		return (shift > 0) ? (reg >>> shift) | (reg << (32 - shift)) : reg;
	}

	private int getOp2S(int shift, int rm) {
		cpu.wait.internalCycle(); //Clock internal cycle
		byte type = (byte)((shift & 0x6) >>> 1); //type is bit 6-5
		
		if ((shift & 0x1) == 0) { //shift unsigned integer
			int imm5 = shift >>> 3;
			switch(type) {
			case 0: return lslis(rm, imm5);
			case 1: return lsris(rm, imm5);
			case 2: return asris(rm, imm5);
			case 3: return roris(rm, imm5);
			}
		}
		else {
			int rs = shift >>> 4; //rs is bit 11-8
			switch(type) {
			case 0: return lslrs(rm, rs);
			case 1: return lsrrs(rm, rs);
			case 2: return asrrs(rm, rs);
			case 3: return rorrs(rm, rs);
			}
		}
		//Should never occur
		throw new RuntimeException();
		//return 0;
	}

	private int lslis(int rm, int imm5) {
		int reg = cpu.getReg(rm);
		if (imm5 > 0)
			cpu.cpsr.carry = (reg << (imm5-1) < 0);
		return reg << imm5;
	}

	private int lsris(int rm, int imm5) {
		int reg = cpu.getReg(rm);
		if (imm5 > 0) {
			cpu.cpsr.carry = (((reg >>> (imm5 - 1)) & 0x1) == 0x1);
			return reg >>> imm5;
		}
		else { //LSR 0 is actually LSR #32
			cpu.cpsr.carry = (reg < 0);
			return 0;
		}
	}

	private int asris(int rm, int imm5) {
		int reg = cpu.getReg(rm);
		if (imm5 > 0) {
			cpu.cpsr.carry = (((reg >>> (imm5 - 1)) & 0x1) == 0x1);
			return reg >> imm5;
		}
		else { //ASR 0 is actually ASR #32
			cpu.cpsr.carry = (reg < 0);
			return reg >> 31;
		}
	}

	private int roris(int rm, int imm5) {
		int reg = cpu.getReg(rm);
		if (imm5 > 0) { //ROR
			cpu.cpsr.carry = (((reg >>> (imm5 - 1)) & 0x1) == 0x1);
			return (reg >>> imm5) | (reg << (32-imm5));
		}
		else { //RRX
			boolean carry = cpu.cpsr.carry;
			//Carry is 0 bit
			cpu.cpsr.carry = ((reg & 0x1) == 0x1);
			return ((carry) ? 0x80000000 : 0) | (reg >>> 1);
		}
	}

	private int lslrs(int rm, int rs) {
		int reg = getRegDelayedPC(rm);
		int shift = getRegDelayedPC(rs) & 0xFF;
		if (shift > 0) {
			if (shift < 32) { //Shifts <32 are fine, carry is the last bit shifted out
				cpu.cpsr.carry = (reg << (shift-1) < 0);
				return reg << shift;
			}
			else if (shift == 32) { //We do this manually b/c in Java, shifts are % #bits, carry is the 0 bit
				cpu.cpsr.carry = ((reg & 0x1) == 0x1); 
				return 0;
			}
			else { //Shift >32, 0's!
				cpu.cpsr.carry = false;
				return 0;
			}
		}
		return reg; //0 shift just returns reg
	}

	private int lsrrs(int rm, int rs) {
		int reg = getRegDelayedPC(rm);
		int shift = getRegDelayedPC(rs) & 0xFF;
		if (shift > 0) {
			if (shift < 32) { //Shifts <32 are fine, carry is the last bit shifted out
				cpu.cpsr.carry = (((reg >>> (shift - 1)) & 0x1) == 0x1);
				return reg >>> shift;
			}
			else if (shift == 32) { //We do this manually b/c in Java, shifts are % #bits, carry is sign bit
				cpu.cpsr.carry = (reg < 0); 
				return 0;
			}
			else { //Shift >32, 0's!
				cpu.cpsr.carry = false;
				return 0;
			}
		}
		return reg; //0 shift just returns reg
	}

	private int asrrs(int rm, int rs) {
		int reg = getRegDelayedPC(rm);
		int shift = getRegDelayedPC(rs) & 0xFF;
		if (shift > 0) {
			if (shift < 32) { //Shifts <32 are fine, carry is the last bit shifted out
				cpu.cpsr.carry = (((reg >> (shift - 1)) & 0x1) == 0x1);
				return reg >> shift;
			}
			else { //Shift >=32, carry is equal to the sign bit, value becomes either all 1's or 0's
				cpu.cpsr.carry = (reg < 0);
				return reg >> 31;
			}
		}
		return reg; //0 shift just returns reg
	}

	private int rorrs(int rm, int rs) {
		int reg = getRegDelayedPC(rm);
		int rotate = getRegDelayedPC(rs) & 0xFF;
		if (rotate > 0) {
			rotate = rotate & 0x1F; //If rotate >32, we subtract 32 until in range [0-31] -> same as & 0x1F (31)
			if (rotate > 0) { //Carry is the last bit rotated out
				cpu.cpsr.carry = (((reg >>> (rotate - 1)) & 0x1) == 0x1);
				//Val is the remaining bits from the shift and the removed bits shifted to the left
				return (reg >>> rotate) | (reg << (32-rotate));
			}
			else //ROR 32, carry equal to sign bit
				cpu.cpsr.carry = (reg < 0);
		}
		return reg; //0 shift just returns reg
	}

	private void dataProcPSRImm(int instr) {
		byte opcode = (byte) ((instr >>> 21) & 0xF);
		//rd = (instr >>> 12) & 0xF
		//rn = (instr >>> 16) & 0xF
		//rm = instr & 0xF
		//rotate = (instr >>> 8) & 0xF;
		if ((instr & 0x100000) == 0x100000) //Bit 20 SET
			dataProcS(opcode, instr >>> 12, cpu.getReg(instr >>> 16), immOpS(instr & 0xFF, (instr >>> 8) & 0xF));
		else if(opcode >= TST && opcode <= CMN) //PSR Transfer
			psrTransferImm(instr);
		else		
			dataProc(opcode, instr >>> 12, cpu.getReg(instr >>> 16), immOp(instr & 0xFF, (instr >>> 8) & 0xF));
	}

	private void psrTransfer(int instr) {
		boolean spsr = ((instr & 0x400000) == 0x400000); //Bit 22
		if ((instr & 0x3F0FFF) == 0x0F0000) //Bit 21-16 is 001111 (0x0F), bit 11-0 is 0
			mrs(instr >>> 12, spsr); //Rd is bit 15-12
		else if ((instr & 0x3FFFF0) == 0x29F000) //Bit 21-16 is 101001 (0x29), bit 15-12 is 1, bit 11-4 is 0
			msr(instr, spsr); //Rm is bit 3-0
		else if ((instr & 0x3FFFF0) == 0x28F000) //Bit 21-16 is 101000 (0x28), bit 15-12 is 1, bit 11-4 is 0
			msrFLG(cpu.getReg(instr), spsr); //Rm is bit 3-0
		else
			cpu.undefinedInstr("Illegal (reg) psr transfer variation");
	}

	private void mrs(int reg, boolean spsr) {
		setRegSafe(reg, (spsr) ? cpu.getSPSR() : cpu.cpsr.save());
	}

	private void msr(int reg, boolean spsr) {
		if (spsr)
			cpu.setSPSR(cpu.getReg(reg));
		else
			cpu.cpsr.loadRestricted(cpu.getReg(reg));
	}

	private void msrFLG(int val, boolean spsr) {
		if (spsr)
			cpu.modifySPSR(val);
		else
			cpu.cpsr.loadFlagBits(val);
	}

	private void psrTransferImm(int instr) {
		if ((instr & 0x3FF000) == 0x28F000) //Bit 21-16 is 101000 (0x28), bit 15-12 is 1
			msrFLG(immOp(instr & 0xFF, (instr >>> 8) & 0xF), (instr & 0x400000) == 0x400000);
		else
			cpu.undefinedInstr("Illegal (imm) psr transfer variation");
	}

	private int immOp(int val, int rotate) {
		rotate = rotate * 2; //ROR by twice the value passed in 
		if (rotate > 0) 
			val = (val >>> rotate) | (val << (32-rotate));
		return val;
	}

	private int immOpS(int val, int rotate) {
		rotate = rotate * 2; //ROR by twice the value passed in 
		if (rotate > 0) {
			cpu.cpsr.carry = (((val >>> (rotate - 1)) & 0x1) == 0x1);
			val = (val >>> rotate) | (val << (32-rotate));
		}
		return val;
	}

	private void dataProcS(byte opcode, int rd, int op1, int op2) {
		switch(opcode) {
		case AND: ands(rd, op1, op2); break;
		case EOR: eors(rd, op1, op2); break;
		case SUB: subs(rd, op1, op2); break;
		case RSB: rsbs(rd, op1, op2); break;
		case ADD: adds(rd, op1, op2); break;
		case ADC: adcs(rd, op1, op2); break;
		case SBC: sbcs(rd, op1, op2); break;
		case RSC: rscs(rd, op1, op2); break;
		case TST: tst(rd, op1, op2); break;
		case TEQ: teq(rd, op1, op2); break;
		case CMP: cmp(rd, op1, op2); break;
		case CMN: cmn(rd, op1, op2); break;
		case ORR: orrs(rd, op1, op2); break;
		case MOV: movs(rd, op1, op2); break;
		case BIC: bics(rd, op1, op2); break;
		case MVN: mvns(rd, op1, op2); break;
		}
	}

	private void dataProc(byte opcode, int rd, int op1, int op2) {
		switch(opcode) {
		case AND: and(rd, op1, op2); break;
		case EOR: eor(rd, op1, op2); break;
		case SUB: sub(rd, op1, op2); break;
		case RSB: rsb(rd, op1, op2); break;
		case ADD: add(rd, op1, op2); break;
		case ADC: adc(rd, op1, op2); break;
		case SBC: sbc(rd, op1, op2); break;
		case RSC: rsc(rd, op1, op2); break;
		case TST: break; //Special cases
		case TEQ: break; //Handled by PSR transfers
		case CMP: break; 
		case CMN: break;
		case ORR: orr(rd, op1, op2); break;
		case MOV: mov(rd, op1, op2); break;
		case BIC: bic(rd, op1, op2); break;
		case MVN: mvn(rd, op1, op2); break;
		}
	}

	private void and(int rd, int op1, int op2) {
		setRegSafe(rd, op1 & op2);
	}

	private void ands(int rd, int op1, int op2) {
		int val = op1 & op2;
		cpu.cpsr.negative = (val < 0);
		cpu.cpsr.zero = (val == 0);
		setRegSafeCPSR(rd, val);
	}

	private void eor(int rd, int op1, int op2) {
		setRegSafe(rd, op1 ^ op2);
	}

	private void eors(int rd, int op1, int op2) {
		int val = op1 ^ op2;
		cpu.cpsr.negative = (val < 0);
		cpu.cpsr.zero = (val == 0);
		setRegSafeCPSR(rd, val);
	}

	private void sub(int rd, int op1, int op2) {
		setRegSafe(rd, op1 - op2);
	}

	private void subs(int rd, int op1, int op2) {
		setRegSafeCPSR(rd, cpu.cpsr.setSubFlags(op1, op2));
	}

	private void rsb(int rd, int op1, int op2) {
		setRegSafe(rd, op2 - op1);
	}

	private void rsbs(int rd, int op1, int op2) {
		setRegSafeCPSR(rd, cpu.cpsr.setSubFlags(op2, op1));
	}

	private void add(int rd, int op1, int op2) {
		setRegSafe(rd, op1 + op2);
	}

	private void adds(int rd, int op1, int op2) {
		setRegSafeCPSR(rd, cpu.cpsr.setAddFlags(op1, op2));
	}

	private void adc(int rd, int op1, int op2) {
		setRegSafe(rd, op1 + op2 + ((cpu.cpsr.carry) ? 1 : 0));
	}

	private void adcs(int rd, int op1, int op2) {
		setRegSafeCPSR(rd, cpu.cpsr.setAddCarryFlags(op1, op2));
	}

	private void sbc(int rd, int op1, int op2) {
		setRegSafe(rd, op1 - op2 - ((cpu.cpsr.carry) ? 0 : 1));
	}

	private void sbcs(int rd, int op1, int op2) {
		setRegSafeCPSR(rd, cpu.cpsr.setSubCarryFlags(op1, op2));
	}

	private void rsc(int rd, int op1, int op2) {
		setRegSafe(rd, op2 - op1 - ((cpu.cpsr.carry) ? 0 : 1));
	}

	private void rscs(int rd, int op1, int op2) {
		setRegSafeCPSR(rd, cpu.cpsr.setSubCarryFlags(op2, op1));
	}

	private void tst(int rd, int op1, int op2) {
		int val = op1 & op2;
		cpu.cpsr.negative = (val < 0);
		cpu.cpsr.zero = (val == 0);
	}

	private void teq(int rd, int op1, int op2) {
		int val = op1 ^ op2;
		cpu.cpsr.negative = (val < 0);
		cpu.cpsr.zero = (val == 0);
	}

	private void cmp(int rd, int op1, int op2) {
		cpu.cpsr.setSubFlags(op1, op2);
	}

	private void cmn(int rd, int op1, int op2) {
		cpu.cpsr.setAddFlags(op1, op2);
	}

	private void orr(int rd, int op1, int op2) {
		setRegSafe(rd, op1 | op2);
	}

	private void orrs(int rd, int op1, int op2) {
		int val = op1 | op2;
		cpu.cpsr.negative = (val < 0);
		cpu.cpsr.zero = (val == 0);
		setRegSafeCPSR(rd, val);
	}

	private void mov(int rd, int op1, int op2) {
		setRegSafe(rd, op2);
	}

	private void movs(int rd, int op1, int op2) {
		cpu.cpsr.negative = (op2 < 0);
		cpu.cpsr.zero = (op2 == 0);
		setRegSafeCPSR(rd, op2);
	}

	private void bic(int rd, int op1, int op2) {
		setRegSafe(rd, op1 & ~op2);
	}

	private void bics(int rd, int op1, int op2) {
		int val = op1 & ~op2;
		cpu.cpsr.negative = (val < 0);
		cpu.cpsr.zero = (val == 0);
		setRegSafeCPSR(rd, val);
	}

	private void mvn(int rd, int op1, int op2) {
		setRegSafe(rd, ~op2);
	}

	private void mvns(int rd, int op1, int op2) {
		op2 = ~op2;
		cpu.cpsr.negative = (op2 < 0);
		cpu.cpsr.zero = (op2 == 0);
		setRegSafeCPSR(rd, op2);
	}

	private void multiply(int instr) {
		byte as = (byte) ((instr >>> 20) & 0x3); //accumulate, set bits
		//rd = (instr >>> 16) & 0xF
		//rm = instr & 0xF
		//rs = (instr >>> 8) & 0xF
		//rn = (instr >>> 12) & 0xF
		switch(as){
		case 0: mul(instr >>> 16, instr, instr >>> 8); break;
		case 1: muls(instr >>> 16, instr, instr >>> 8); break;
		case 2: mla(instr >>> 16, instr, instr >>> 8, instr >>> 12); break;
		case 3: mlas(instr >>> 16, instr, instr >>> 8, instr >>> 12); break;
		}
	}

	private void mul(int rd, int rm, int rs) {
		setRegSafe(rd, cpu.getReg(rm) * cpu.getReg(rs));
	}

	private void muls(int rd, int rm, int rs) {
		int val = cpu.getReg(rm) * cpu.getReg(rs);
		cpu.cpsr.carry = false;
		cpu.cpsr.negative = (val < 0);
		cpu.cpsr.zero = (val == 0);
		setRegSafe(rd, val);
	}

	private void mla(int rd, int rm, int rs, int rn) {
		setRegSafe(rd, cpu.getReg(rm)*cpu.getReg(rs) + cpu.getReg(rn));
	}

	private void mlas(int rd, int rm, int rs, int rn) {
		int val = cpu.getReg(rm)*cpu.getReg(rs) + cpu.getReg(rn);
		cpu.cpsr.carry = false;
		cpu.cpsr.negative = (val < 0);
		cpu.cpsr.zero = (val == 0);
		setRegSafe(rd, val);
	}

	private void multiplyLong(int instr) {
		byte sas = (byte) ((instr >>> 20) & 0x7); //signed, accumulate, set bits
		//rdHi = (instr >>> 16) & 0xF
		//rdLo = (instr >>> 12) & 0xF
		//rm = instr & 0xF
		//rs = (instr >>> 8) & 0xF
		switch(sas) {
		case 0: umull(instr >>> 16, instr >>> 12, instr, instr >>> 8); break;
		case 1: umulls(instr >>> 16, instr >>> 12, instr, instr >>> 8); break;
		case 2: umlal(instr >>> 16, instr >>> 12, instr, instr >>> 8); break;
		case 3: umlals(instr >>> 16, instr >>> 12, instr, instr >>> 8); break;
		case 4: smull(instr >>> 16, instr >>> 12, instr, instr >>> 8); break;
		case 5: smulls(instr >>> 16, instr >>> 12, instr, instr >>> 8); break;
		case 6: smlal(instr >>> 16, instr >>> 12, instr, instr >>> 8); break;
		case 7: smlals(instr >>> 16, instr >>> 12, instr, instr >>> 8); break;
		}
	}

	private void umull(int rdHi, int rdLo, int rm, int rs) {
		long result = (cpu.getReg(rm) & 0xFFFFFFFFL)*(cpu.getReg(rs) & 0xFFFFFFFFL);
		setRegSafe(rdHi, (int) (result >>> 32));
		setRegSafe(rdLo, (int) result);
	}

	private void umulls(int rdHi, int rdLo, int rm, int rs) {
		long result = (cpu.getReg(rm) & 0xFFFFFFFFL)*(cpu.getReg(rs) & 0xFFFFFFFFL);
		cpu.cpsr.carry = false;
		cpu.cpsr.negative = (result < 0);
		cpu.cpsr.zero = (result == 0);
		setRegSafe(rdHi, (int) (result >>> 32));
		setRegSafe(rdLo, (int) result);
	}

	private void umlal(int rdHi, int rdLo, int rm, int rs) {
		long result = (cpu.getReg(rm) & 0xFFFFFFFFL)*(cpu.getReg(rs) & 0xFFFFFFFFL) + (((cpu.getReg(rdHi) & 0xFFFFFFFFL) << 32) | (cpu.getReg(rdLo) & 0xFFFFFFFFL));
		setRegSafe(rdHi, (int) (result >>> 32));
		setRegSafe(rdLo, (int) result);
	}

	private void umlals(int rdHi, int rdLo, int rm, int rs) {
		long result = (cpu.getReg(rm) & 0xFFFFFFFFL)*(cpu.getReg(rs) & 0xFFFFFFFFL) + (((cpu.getReg(rdHi) & 0xFFFFFFFFL) << 32) | (cpu.getReg(rdLo) & 0xFFFFFFFFL));
		cpu.cpsr.carry = false;
		cpu.cpsr.negative = (result < 0);
		cpu.cpsr.zero = (result == 0);
		setRegSafe(rdHi, (int) (result >>> 32));
		setRegSafe(rdLo, (int) result);
	}

	private void smull(int rdHi, int rdLo, int rm, int rs) {
		long result = ((long) cpu.getReg(rm))*cpu.getReg(rs);
		setRegSafe(rdHi, (int) (result >>> 32));
		setRegSafe(rdLo, (int) result);
	}

	private void smulls(int rdHi, int rdLo, int rm, int rs) {
		long result = ((long) cpu.getReg(rm))*cpu.getReg(rs);
		cpu.cpsr.carry = false;
		cpu.cpsr.negative = (result < 0);
		cpu.cpsr.zero = (result == 0);
		setRegSafe(rdHi, (int) (result >>> 32));
		setRegSafe(rdLo, (int) result);
	}

	private void smlal(int rdHi, int rdLo, int rm, int rs) {
		long result = ((long) cpu.getReg(rm))*cpu.getReg(rs) + (((cpu.getReg(rdHi) & 0xFFFFFFFFL) << 32) | (cpu.getReg(rdLo) & 0xFFFFFFFFL));
		setRegSafe(rdHi, (int) (result >>> 32));
		setRegSafe(rdLo, (int) result);
	}

	private void smlals(int rdHi, int rdLo, int rm, int rs) {
		long result = ((long) cpu.getReg(rm))*cpu.getReg(rs) + (((cpu.getReg(rdHi) & 0xFFFFFFFFL) << 32) | (cpu.getReg(rdLo) & 0xFFFFFFFFL));
		cpu.cpsr.carry = false;
		cpu.cpsr.negative = (result < 0);
		cpu.cpsr.zero = (result == 0);
		setRegSafe(rdHi, (int) (result >>> 32));
		setRegSafe(rdLo, (int) result);
	}

	private void singleDataSwap(int instr) {
		//rn = (instr >>> 16) & 0xF
		//rd = (instr >>> 12) & 0xF
		//rs = instr & 0xF
		if ((instr & 0x400000) == 0x400000) //Bit 22 SET - byte quantity
			swpb(instr >>> 16, instr >>> 12, instr);
		else
			swp(instr >>> 16, instr >>> 12, instr);	
	}

	private void swpb(int rn, int rd, int rs) {
		int address = cpu.getReg(rn);
		int contents = cpu.read8(address);
		cpu.wait.internalCycle(); //Clock internal cycle
		cpu.write8(address, cpu.getReg(rs));
		setRegSafe(rd, contents);
	}

	private void swp(int rn, int rd, int rs) {
		int address = cpu.getReg(rn);
		int contents = cpu.read32(address);
		cpu.wait.internalCycle(); //Clock internal cycle
		cpu.write32(address, cpu.getReg(rs));
		setRegSafe(rd, contents);
	}

	private void halfwordDTImmPost(int instr) {
		if ((instr & 0x200000) == 0x200000) { //Write back should be 0
			cpu.undefinedInstr("Halfword data transfer POST write back bit must be CLEAR");
			return; 
		}
		//rn = (instr >>> 16) & 0xF
		//rd = (instr >>> 12) & 0xF
		int imm8 = ((instr & 0xF00) >>> 4) | (instr & 0xF);

		//Bit 23 is U bit - up or down 
		boolean incr = (instr & 0x800000) == 0x800000;
		//Post indexed data transfers always write back the modified base
		byte lsh = (byte) (((instr & 0x100000) >>> 18) | ((instr & 0x60) >>> 5)); // load/store, signed/unsigned, halfword/byte
		switch(lsh) {
		case 0: break; //swp - won't happen
		case 1: strh(instr >>> 12, (incr) ? basePostIncr(instr >>> 16, imm8) : basePostDecr(instr >>> 16, imm8)); break;
		case 2: cpu.undefinedInstr("Halfword data transfer cannot store sign extended byte"); break; //invalid
		case 3: cpu.undefinedInstr("Halfword data transfer cannot store sign extended halfword"); break; //invalid
		case 4: break; //swp - won't happen
		case 5: ldrh(instr >>> 12, (incr) ? basePostIncr(instr >>> 16, imm8) : basePostDecr(instr >>> 16, imm8)); break;
		case 6: ldrsb(instr >>> 12, (incr) ? basePostIncr(instr >>> 16, imm8) : basePostDecr(instr >>> 16, imm8)); break;
		case 7: ldrsh(instr >>> 12, (incr) ? basePostIncr(instr >>> 16, imm8) : basePostDecr(instr >>> 16, imm8)); break;
		}
	}

	private void strh(int reg, int address) {
		cpu.write16(address, getRegDelayedPC(reg));
	}

	private void ldrh(int reg, int address) {
		setRegSafe(reg, cpu.read16(address));
	}

	private void ldrsh(int reg, int address) {
		//Load sign extended half word
		setRegSafe(reg, (cpu.read16(address) << 16) >> 16);
	}

	private void ldrsb(int reg, int address) {
		//Load sign extended byte
		setRegSafe(reg, (cpu.read8(address) << 24) >> 24);
	}

	private void halfwordDTImmPre(int instr) {
		//rn = (instr >>> 16) & 0xF
		//rd = (instr >>> 12) & 0xF
		int imm8 = ((instr & 0xF00) >>> 4) | (instr & 0xF);

		byte uw = (byte) (((instr >>> 22) & 0x2) | ((instr >>> 21) & 0x1));
		byte lsh = (byte) (((instr & 0x100000) >>> 18) | ((instr & 0x60) >>> 5)); // load/store, signed/unsigned, halfword/byte
		switch(lsh) {
		case 0: break; //swp - won't happen
		case 1: strh(instr >>> 12, getPreAddress(uw, instr >>> 16, imm8)); break;
		case 2: cpu.undefinedInstr("Halfword data transfer cannot store sign extended byte"); break; //invalid
		case 3: cpu.undefinedInstr("Halfword data transfer cannot store sign extended halfword"); break; //invalid
		case 4: break; //swp - won't happen
		case 5: ldrh(instr >>> 12, getPreAddress(uw, instr >>> 16, imm8)); break;
		case 6: ldrsb(instr >>> 12, getPreAddress(uw, instr >>> 16, imm8)); break;
		case 7: ldrsh(instr >>> 12, getPreAddress(uw, instr >>> 16, imm8)); break;
		}
	}

	private int getPreAddress(byte uw, int rn, int offset) {
		//uw - up/down, write back/don't
		switch(uw) {
		case 0: return baseDecr(rn, offset);
		case 1: return basePreDecr(rn, offset);
		case 2: return baseIncr(rn, offset);
		case 3: return basePreIncr(rn, offset);
		}
		//Should never occur
		throw new RuntimeException();
		//return 0;
	}

	private void halfwordDTRegPost(int instr) {
		if ((instr & 0x200000) == 0x200000) { //Write back should be 0
			cpu.undefinedInstr("Halfword data transfer POST write back bit must be CLEAR");
			return; 
		}
		//rn = (instr >>> 16) & 0xF
		//rd = (instr >>> 12) & 0xF
		int offset = cpu.getReg(instr);

		//Bit 23 is U bit - up or down 
		boolean incr = (instr & 0x800000) == 0x800000;
		//Post indexed data transfers always write back the modified base
		byte lsh = (byte) (((instr & 0x100000) >>> 18) | ((instr & 0x60) >>> 5)); // load/store, signed/unsigned, halfword/byte
		switch(lsh) {
		case 0: break; //swp - won't happen
		case 1: strh(instr >>> 12, (incr) ? basePostIncr(instr >>> 16, offset) : basePostDecr(instr >>> 16, offset)); break;
		case 2: cpu.undefinedInstr("Halfword data transfer cannot store sign extended byte"); break; //invalid
		case 3: cpu.undefinedInstr("Halfword data transfer cannot store sign extended halfword"); break; //invalid
		case 4: break; //swp - won't happen
		case 5: ldrh(instr >>> 12, (incr) ? basePostIncr(instr >>> 16, offset) : basePostDecr(instr >>> 16, offset)); break;
		case 6: ldrsb(instr >>> 12, (incr) ? basePostIncr(instr >>> 16, offset) : basePostDecr(instr >>> 16, offset)); break;
		case 7: ldrsh(instr >>> 12, (incr) ? basePostIncr(instr >>> 16, offset) : basePostDecr(instr >>> 16, offset)); break;
		}
	}

	private void halfwordDTRegPre(int instr) {
		//rn = (instr >>> 16) & 0xF
		//rd = (instr >>> 12) & 0xF
		int offset = cpu.getReg(instr);

		byte uw = (byte) (((instr >>> 22) & 0x2) | ((instr >>> 21) & 0x1));
		byte lsh = (byte) (((instr & 0x100000) >>> 18) | ((instr & 0x60) >>> 5)); // load/store, signed/unsigned, halfword/byte
		switch(lsh) {
		case 0: break; //swp - won't happen
		case 1: strh(instr >>> 12, getPreAddress(uw, instr >>> 16, offset)); break;
		case 2: cpu.undefinedInstr("Halfword data transfer cannot store sign extended byte"); break; //invalid
		case 3: cpu.undefinedInstr("Halfword data transfer cannot store sign extended halfword"); break; //invalid
		case 4: break; //swp - won't happen
		case 5: ldrh(instr >>> 12, getPreAddress(uw, instr >>> 16, offset)); break;
		case 6: ldrsb(instr >>> 12, getPreAddress(uw, instr >>> 16, offset)); break;
		case 7: ldrsh(instr >>> 12, getPreAddress(uw, instr >>> 16, offset)); break;
		}
	}

	private void singleDataTransferImmPre(int instr) {
		byte ubwl = (byte) ((instr >>> 20) & 0xF); // up/down, byte/word, write back/don't, load/store bits
		//rn = (instr >>> 16) & 0xF
		//rd = (instr >>> 12) & 0xF
		int imm12 = instr & 0xFFF;

		//8 - Up (else down)
		//4 - Byte (else word)
		//2 - Write back (else don't)
		//1 - Load (else store)
		switch(ubwl) { 
		case 0x0: str(instr >>> 12, baseDecr(instr >>> 16, imm12)); break; //Group 1 - word decr
		case 0x1: ldr(instr >>> 12, baseDecr(instr >>> 16, imm12)); break;
		case 0x2: str(instr >>> 12, basePreDecr(instr >>> 16, imm12)); break;
		case 0x3: ldr(instr >>> 12, basePreDecr(instr >>> 16, imm12)); break;
		case 0x4: strb(instr >>> 12, baseDecr(instr >>> 16, imm12)); break; //Group 2 - byte decr
		case 0x5: ldrb(instr >>> 12, baseDecr(instr >>> 16, imm12)); break;
		case 0x6: strb(instr >>> 12, basePreDecr(instr >>> 16, imm12)); break;
		case 0x7: ldrb(instr >>> 12, basePreDecr(instr >>> 16, imm12)); break;
		case 0x8: str(instr >>> 12, baseIncr(instr >>> 16, imm12)); break; //Group 3 - word incr
		case 0x9: ldr(instr >>> 12, baseIncr(instr >>> 16, imm12)); break;
		case 0xA: str(instr >>> 12, basePreIncr(instr >>> 16, imm12)); break;
		case 0xB: ldr(instr >>> 12, basePreIncr(instr >>> 16, imm12)); break;
		case 0xC: strb(instr >>> 12, baseIncr(instr >>> 16, imm12)); break; //Group 4 - byte incr
		case 0xD: ldrb(instr >>> 12, baseIncr(instr >>> 16, imm12)); break;
		case 0xE: strb(instr >>> 12, basePreIncr(instr >>> 16, imm12)); break;
		case 0xF: ldrb(instr >>> 12, basePreIncr(instr >>> 16, imm12)); break;
		}
	}

	private int baseIncr(int base, int offset) {
		return cpu.getReg(base) + offset;
	}

	private int baseDecr(int base, int offset) {
		return cpu.getReg(base) - offset;
	}

	private int basePreIncr(int base, int offset) {
		int val = cpu.getReg(base) + offset;
		setRegSafe(base, val);
		return val;
	}

	private int basePreDecr(int base, int offset) {
		int val = cpu.getReg(base) - offset;
		setRegSafe(base, val);
		return val;
	}

	private void str(int reg, int address) {
		cpu.write32(address, getRegDelayedPC(reg));
	}

	private void ldr(int reg, int address) {
		setRegSafe(reg, cpu.read32(address));
	}

	private void strb(int reg, int address) {
		cpu.write8(address, getRegDelayedPC(reg));
	}

	private void ldrb(int reg, int address) {
		setRegSafe(reg, cpu.read8(address));
	}

	private void singleDataTransferImmPost(int instr) {
		byte ubtl = (byte) ((instr >>> 20) & 0xF); // up/down, byte/word, force non-privileged/don't, load/store bits
		//rn = (instr >>> 16) & 0xF
		//rd = (instr >>> 12) & 0xF
		int imm12 = instr & 0xFFF;

		//8 - Up (else down)
		//4 - Byte (else word)
		//2 - Force user mode (else don't)
		//1 - Load (else store)
		switch(ubtl) { 
		case 0x0: str(instr >>> 12, basePostDecr(instr >>> 16, imm12)); break; //Group 1 - word decr
		case 0x1: ldr(instr >>> 12, basePostDecr(instr >>> 16, imm12)); break;
		case 0x2: str(instr >>> 12, basePostDecrUser(instr >>> 16, imm12)); break;
		case 0x3: ldr(instr >>> 12, basePostDecrUser(instr >>> 16, imm12)); break;
		case 0x4: strb(instr >>> 12, basePostDecr(instr >>> 16, imm12)); break; //Group 2 - byte decr
		case 0x5: ldrb(instr >>> 12, basePostDecr(instr >>> 16, imm12)); break;
		case 0x6: strb(instr >>> 12, basePostDecrUser(instr >>> 16, imm12)); break;
		case 0x7: ldrb(instr >>> 12, basePostDecrUser(instr >>> 16, imm12)); break;
		case 0x8: str(instr >>> 12, basePostIncr(instr >>> 16, imm12)); break; //Group 3 - word incr
		case 0x9: ldr(instr >>> 12, basePostIncr(instr >>> 16, imm12)); break;
		case 0xA: str(instr >>> 12, basePostIncrUser(instr >>> 16, imm12)); break;
		case 0xB: ldr(instr >>> 12, basePostIncrUser(instr >>> 16, imm12)); break;
		case 0xC: strb(instr >>> 12, basePostIncr(instr >>> 16, imm12)); break; //Group 4 - byte incr
		case 0xD: ldrb(instr >>> 12, basePostIncr(instr >>> 16, imm12)); break;
		case 0xE: strb(instr >>> 12, basePostIncrUser(instr >>> 16, imm12)); break;
		case 0xF: ldrb(instr >>> 12, basePostIncrUser(instr >>> 16, imm12)); break;
		}
	}

	private int basePostDecr(int base, int offset) {
		int val = cpu.getReg(base);
		setRegSafe(base, val - offset);
		return val;
	}

	private int basePostDecrUser(int base, int offset) {
		int val = cpu.getUserReg(base);
		setUserRegSafe(base, val - offset);
		return val;
	}

	private int basePostIncr(int base, int offset) {
		int val = cpu.getReg(base);
		setRegSafe(base, val + offset);
		return val;
	}

	private int basePostIncrUser(int base, int offset) {
		int val = cpu.getUserReg(base);
		setUserRegSafe(base, val + offset);
		return val;
	}

	private void singleDataTransferRegPre(int instr) {
		byte ubwl = (byte) ((instr >>> 20) & 0xF); // up/down, byte/word, write back/don't, load/store bits
		//rn = (instr >>> 16) & 0xF
		//rd = (instr >>> 12) & 0xF
		//shift = (instr >>> 4) & 0xFF
		//rm = instr & 0xF

		int offset = getOp2DT((instr >>> 4) & 0xFF, instr);
		//8 - Up (else down)
		//4 - Byte (else word)
		//2 - Write back (else don't)
		//1 - Load (else store)
		switch(ubwl) { 
		case 0x0: str(instr >>> 12, baseDecr(instr >>> 16, offset)); break; //Group 1 - word decr
		case 0x1: ldr(instr >>> 12, baseDecr(instr >>> 16, offset)); break;
		case 0x2: str(instr >>> 12, basePreDecr(instr >>> 16, offset)); break;
		case 0x3: ldr(instr >>> 12, basePreDecr(instr >>> 16, offset)); break;
		case 0x4: strb(instr >>> 12, baseDecr(instr >>> 16, offset)); break; //Group 2 - byte decr
		case 0x5: ldrb(instr >>> 12, baseDecr(instr >>> 16, offset)); break;
		case 0x6: strb(instr >>> 12, basePreDecr(instr >>> 16, offset)); break;
		case 0x7: ldrb(instr >>> 12, basePreDecr(instr >>> 16, offset)); break;
		case 0x8: str(instr >>> 12, baseIncr(instr >>> 16, offset)); break; //Group 3 - word incr
		case 0x9: ldr(instr >>> 12, baseIncr(instr >>> 16, offset)); break;
		case 0xA: str(instr >>> 12, basePreIncr(instr >>> 16, offset)); break;
		case 0xB: ldr(instr >>> 12, basePreIncr(instr >>> 16, offset)); break;
		case 0xC: strb(instr >>> 12, baseIncr(instr >>> 16, offset)); break; //Group 4 - byte incr
		case 0xD: ldrb(instr >>> 12, baseIncr(instr >>> 16, offset)); break;
		case 0xE: strb(instr >>> 12, basePreIncr(instr >>> 16, offset)); break;
		case 0xF: ldrb(instr >>> 12, basePreIncr(instr >>> 16, offset)); break;
		}
	}

	private int getOp2DT(int shift, int rm) {
		byte type = (byte)((shift & 0x6) >>> 1); //type is bit 6-5
		if ((shift & 0x1) == 0) { //shift unsigned integer
			int imm5 = shift >>> 3; //bit 11-7
			switch(type) {
			case 0: return lsli(rm, imm5);
			case 1: return lsri(rm, imm5);
			case 2: return asri(rm, imm5);
			case 3: return rori(rm, imm5);
			}
		}
		else //Shift register not available in this instruction class, TODO handle this properly
			return cpu.getReg(rm);
		//Should never occur
		throw new RuntimeException();
		//return 0;
	}

	private void singleDataTransferRegPost(int instr) {
		byte ubtl = (byte) ((instr >>> 20) & 0xF); // up/down, byte/word, force non-privileged/don't, load/store bits
		//rn = (instr >>> 16) & 0xF
		//rd = (instr >>> 12) & 0xF
		//shift = (instr >>> 4) & 0xFF
		//rm = instr & 0xF

		int offset = getOp2DT((instr >>> 4) & 0xFF, instr);
		//8 - Up (else down)
		//4 - Byte (else word)
		//2 - Force user mode (else don't)
		//1 - Load (else store)
		switch(ubtl) { 
		case 0x0: str(instr >>> 12, basePostDecr(instr >>> 16, offset)); break; //Group 1 - word decr
		case 0x1: ldr(instr >>> 12, basePostDecr(instr >>> 16, offset)); break;
		case 0x2: str(instr >>> 12, basePostDecrUser(instr >>> 16, offset)); break;
		case 0x3: ldr(instr >>> 12, basePostDecrUser(instr >>> 16, offset)); break;
		case 0x4: strb(instr >>> 12, basePostDecr(instr >>> 16, offset)); break; //Group 2 - byte decr
		case 0x5: ldrb(instr >>> 12, basePostDecr(instr >>> 16, offset)); break;
		case 0x6: strb(instr >>> 12, basePostDecrUser(instr >>> 16, offset)); break;
		case 0x7: ldrb(instr >>> 12, basePostDecrUser(instr >>> 16, offset)); break;
		case 0x8: str(instr >>> 12, basePostIncr(instr >>> 16, offset)); break; //Group 3 - word incr
		case 0x9: ldr(instr >>> 12, basePostIncr(instr >>> 16, offset)); break;
		case 0xA: str(instr >>> 12, basePostIncrUser(instr >>> 16, offset)); break;
		case 0xB: ldr(instr >>> 12, basePostIncrUser(instr >>> 16, offset)); break;
		case 0xC: strb(instr >>> 12, basePostIncr(instr >>> 16, offset)); break; //Group 4 - byte incr
		case 0xD: ldrb(instr >>> 12, basePostIncr(instr >>> 16, offset)); break;
		case 0xE: strb(instr >>> 12, basePostIncrUser(instr >>> 16, offset)); break;
		case 0xF: ldrb(instr >>> 12, basePostIncrUser(instr >>> 16, offset)); break;
		}
	}

	private void undefinedTrap() {
		cpu.undefinedTrap();
	}

	private void blockDataTransferPre(int instr) {
		byte uswl = (byte) ((instr >>> 20) & 0xF); // up/down, loadPSR/force User mode/DON'T, write back base/don't, load/store 
		//rn = (instr >>> 16) & 0xF
		int list = instr & 0xFFFF;

		switch(uswl) { 
		case 0x0: stmdb(instr >>> 16, list); break; //PRE DECR
		case 0x1: ldmdb(instr >>> 16, list); break;
		case 0x2: stmdbw(instr >>> 16, list); break; //write back
		case 0x3: ldmdbw(instr >>> 16, list); break; //write back
		case 0x4: stmdbs(instr >>> 16, list); break; //user mode
		case 0x5: ldmdbs(instr >>> 16, list); break; //user mode/mode change
		case 0x6: stmdbws(instr >>> 16, list); break; //write back and user mode, TODO Verify this is legal
		case 0x7: ldmdbws(instr >>> 16, list); break; //write back and user mode, TODO Verify this is legal
		case 0x8: stmib(instr >>> 16, list); break; //PRE INCR
		case 0x9: ldmib(instr >>> 16, list); break;
		case 0xA: stmibw(instr >>> 16, list); break; //write back
		case 0xB: ldmibw(instr >>> 16, list); break; //write back
		case 0xC: stmibs(instr >>> 16, list); break; //user mode
		case 0xD: ldmibs(instr >>> 16, list); break; //user mode/mode change
		case 0xE: stmibws(instr >>> 16, list); break; //write back and user mode, TODO Verify this is legal
		case 0xF: ldmibws(instr >>> 16, list); break; //write back and user mode, TODO Verify this is legal
		}
	}

	//PRE DECR
	private void stmdb(int base, int list) {
		int address = cpu.getReg(base);
		for (byte reg = 15; reg >= 0; --reg) { 
			if ((list & (1 << reg)) != 0)	{
				address -= 4;
				cpu.write32(address, getRegDelayedPC(reg));
			}
		}
	}

	//PRE DECR - write back
	private void stmdbw(int base, int list) {
		int address = cpu.getReg(base);
		for (byte reg = 15; reg >= 0; --reg) { 
			if ((list & (1 << reg)) != 0)	{
				address -= 4;
				cpu.write32(address, getRegDelayedPC(reg));
			}
		}
		setRegSafe(base, address);
	}

	//PRE DECR - user mode
	private void stmdbs(int base, int list) {
		int address = cpu.getReg(base);
		for (byte reg = 15; reg >= 0; --reg) { 
			if ((list & (1 << reg)) != 0)	{
				address -= 4;
				cpu.write32(address, getUserRegDelayedPC(reg));
			}
		}
	}

	//PRE DECR - write back and user mode
	private void stmdbws(int base, int list) {
		int address = cpu.getReg(base);
		for (byte reg = 15; reg >= 0; --reg) { 
			if ((list & (1 << reg)) != 0)	{
				address -= 4;
				cpu.write32(address, getUserRegDelayedPC(reg));
			}
		}
		setRegSafe(base, address);
	}

	//PRE DECR
	private void ldmdb(int base, int list) {
		int address = cpu.getReg(base);
		for (byte reg = 15; reg >= 0; --reg) { 
			if ((list & (1 << reg)) != 0)	{
				address -= 4;
				setRegSafe(reg, cpu.read32(address));
			}
		}
	}

	//PRE DECR - write back
	private void ldmdbw(int base, int list) {
		int address = cpu.getReg(base);
		for (byte reg = 15; reg >= 0; --reg) { 
			if ((list & (1 << reg)) != 0)	{
				address -= 4;
				setRegSafe(reg, cpu.read32(address));
			}
		}
		setRegSafe(base, address);
	}

	//PRE DECR - user mode/SPSR transfer
	private void ldmdbs(int base, int list) {
		int address = cpu.getReg(base);
		if ((list & 0x8000) == 0x8000) { //R15 is in list - special mode change
			address -= 4;
			setRegSafeCPSR(15, cpu.read32(address));
			for (byte reg = 14; reg >= 0; --reg) { 
				if ((list & (1 << reg)) != 0)	{
					address -= 4;
					setRegSafe(reg, cpu.read32(address));
				}
			}
		}
		else {
			for (byte reg = 14; reg >= 0; --reg) { 
				if ((list & (1 << reg)) != 0)	{
					address -= 4;
					setUserRegSafe(reg, cpu.read32(address));
				}
			}
		}
	}

	//PRE DECR - write back and user mode/SPSR transfer
	private void ldmdbws(int base, int list) {
		int address = cpu.getReg(base);
		if ((list & 0x8000) == 0x8000) { //R15 is in list - special mode change
			address -= 4;
			setRegSafeCPSR(15, cpu.read32(address));
			for (byte reg = 14; reg >= 0; --reg) { 
				if ((list & (1 << reg)) != 0)	{
					address -= 4;
					setRegSafe(reg, cpu.read32(address));
				}
			}
		}
		else {
			for (byte reg = 14; reg >= 0; --reg) { 
				if ((list & (1 << reg)) != 0)	{
					address -= 4;
					setUserRegSafe(reg, cpu.read32(address));
				}
			}
		}
		setRegSafe(base, address);
	}

	//PRE INCR
	private void stmib(int base, int list) {
		int address = cpu.getReg(base);
		for (byte reg = 0; reg <= 15; ++reg) {
			if ((list & (1 << reg)) != 0)	{
				address += 4;
				cpu.write32(address, getRegDelayedPC(reg));
			}
		}
	}

	//PRE INCR - write back
	private void stmibw(int base, int list) {
		int address = cpu.getReg(base);
		for (byte reg = 0; reg <= 15; ++reg) {
			if ((list & (1 << reg)) != 0)	{
				address += 4;
				cpu.write32(address, getRegDelayedPC(reg));
			}
		}
		setRegSafe(base, address);
	}

	//PRE INCR - user mode
	private void stmibs(int base, int list) {
		int address = cpu.getReg(base);
		for (byte reg = 0; reg <= 15; ++reg) {
			if ((list & (1 << reg)) != 0)	{
				address += 4;
				cpu.write32(address, getUserRegDelayedPC(reg));
			}
		}
	}

	//PRE INCR - write back and user mode
	private void stmibws(int base, int list) {
		int address = cpu.getReg(base);
		for (byte reg = 0; reg <= 15; ++reg) {
			if ((list & (1 << reg)) != 0)	{
				address += 4;
				cpu.write32(address, getUserRegDelayedPC(reg));
			}
		}
		setRegSafe(base, address);
	}

	//PRE INCR
	private void ldmib(int base, int list) {
		int address = cpu.getReg(base);
		for (byte reg = 0; reg <= 15; ++reg) {
			if ((list & (1 << reg)) != 0)	{
				address += 4;
				setRegSafe(reg, cpu.read32(address));
			}
		}
	}

	//PRE INCR - write back
	private void ldmibw(int base, int list) {
		int address = cpu.getReg(base);
		for (byte reg = 0; reg <= 15; ++reg) {
			if ((list & (1 << reg)) != 0)	{
				address += 4;
				setRegSafe(reg, cpu.read32(address));
			}
		}
		setRegSafe(base, address);
	}

	//PRE INCR - user mode/SPSR transfer
	private void ldmibs(int base, int list) {
		int address = cpu.getReg(base);
		if ((list & 0x8000) == 0x8000) { //R15 is in list - special mode change
			for (byte reg = 0; reg <= 14; ++reg) { 
				if ((list & (1 << reg)) != 0)	{
					address += 4;
					setRegSafe(reg, cpu.read32(address));
				}
			}
			address += 4;
			setRegSafeCPSR(15, cpu.read32(address));
		}
		else {
			for (byte reg = 0; reg <= 14; ++reg) { 
				if ((list & (1 << reg)) != 0)	{
					address += 4;
					setUserRegSafe(reg, cpu.read32(address));
				}
			}
		}
	}

	//PRE INCR - write back and user mode/SPSR transfer
	private void ldmibws(int base, int list) {
		int address = cpu.getReg(base);
		if ((list & 0x8000) == 0x8000) { //R15 is in list - special mode change
			for (byte reg = 0; reg <= 14; ++reg) { 
				if ((list & (1 << reg)) != 0)	{
					address += 4;
					setRegSafe(reg, cpu.read32(address));
				}
			}
			address += 4;
			setRegSafeCPSR(15, cpu.read32(address));
		}
		else {
			for (byte reg = 0; reg <= 14; ++reg) { 
				if ((list & (1 << reg)) != 0)	{
					address += 4;
					setUserRegSafe(reg, cpu.read32(address));
				}
			}
		}
		setRegSafe(base, address);
	}

	private void blockDataTransferPost(int instr) {
		byte uswl = (byte) ((instr >>> 20) & 0xF); // up/down, loadPSR/force User mode/DON'T, write back base/don't, load/store 
		//rn = (instr >>> 16) & 0xF
		int list = instr & 0xFFFF; 
		
		switch(uswl) {
		case 0x0: stmda(instr >>> 16, list); break; //POST DECR
		case 0x1: ldmda(instr >>> 16, list); break;
		case 0x2: stmdaw(instr >>> 16, list); break; //write back
		case 0x3: ldmdaw(instr >>> 16, list); break; //write back
		case 0x4: stmdas(instr >>> 16, list); break; //user mode
		case 0x5: ldmdas(instr >>> 16, list); break; //user mode/mode change
		case 0x6: stmdaws(instr >>> 16, list); break; //write back and user mode, TODO Verify this is legal
		case 0x7: ldmdaws(instr >>> 16, list); break; //write back and user mode, TODO Verify this is legal
		case 0x8: stmia(instr >>> 16, list); break; //POST INCR
		case 0x9: ldmia(instr >>> 16, list); break;
		case 0xA: stmiaw(instr >>> 16, list); break; //write back
		case 0xB: ldmiaw(instr >>> 16, list); break; //write back
		case 0xC: stmias(instr >>> 16, list); break; //user mode
		case 0xD: ldmias(instr >>> 16, list); break; //user mode/mode change
		case 0xE: stmiaws(instr >>> 16, list); break; //write back and user mode, TODO Verify this is legal
		case 0xF: ldmiaws(instr >>> 16, list); break; //write back and user mode, TODO Verify this is legal
		}
	}

	//POST DECR
	private void stmda(int base, int list) {
		int address = cpu.getReg(base);
		for (byte reg = 15; reg >= 0; --reg) { 
			if ((list & (1 << reg)) != 0)	{
				cpu.write32(address, getRegDelayedPC(reg));
				address -= 4;
			}
		}
	}

	//POST DECR - write back
	private void stmdaw(int base, int list) {
		int address = cpu.getReg(base);
		for (byte reg = 15; reg >= 0; --reg) { 
			if ((list & (1 << reg)) != 0)	{
				cpu.write32(address, getRegDelayedPC(reg));
				address -= 4;
			}
		}
		setRegSafe(base, address);
	}

	//POST DECR - user mode
	private void stmdas(int base, int list) {
		int address = cpu.getReg(base);
		for (byte reg = 15; reg >= 0; --reg) { 
			if ((list & (1 << reg)) != 0)	{
				cpu.write32(address, getUserRegDelayedPC(reg));
				address -= 4;
			}
		}
	}

	//POST DECR - write back and user mode
	private void stmdaws(int base, int list) {
		int address = cpu.getReg(base);
		for (byte reg = 15; reg >= 0; --reg) { 
			if ((list & (1 << reg)) != 0)	{
				cpu.write32(address, getUserRegDelayedPC(reg));
				address -= 4;
			}
		}
		setRegSafe(base, address);
	}

	//POST DECR
	private void ldmda(int base, int list) {
		int address = cpu.getReg(base);
		for (byte reg = 15; reg >= 0; --reg) { 
			if ((list & (1 << reg)) != 0)	{
				setRegSafe(reg, cpu.read32(address));
				address -= 4;
			}
		}
	}

	//POST DECR - write back
	private void ldmdaw(int base, int list) {
		int address = cpu.getReg(base);
		for (byte reg = 15; reg >= 0; --reg) { 
			if ((list & (1 << reg)) != 0)	{
				setRegSafe(reg, cpu.read32(address));
				address -= 4;
			}
		}
		setRegSafe(base, address);
	}

	//POST DECR - user mode/SPSR transfer
	private void ldmdas(int base, int list) {
		int address = cpu.getReg(base);
		if ((list & 0x8000) == 0x8000) { //R15 is in list - special mode change
			setRegSafeCPSR(15, cpu.read32(address));
			address -= 4;
			for (byte reg = 14; reg >= 0; --reg) { 
				if ((list & (1 << reg)) != 0)	{
					setRegSafe(reg, cpu.read32(address));
					address -= 4;
				}
			}
		}
		else {
			for (byte reg = 14; reg >= 0; --reg) { 
				if ((list & (1 << reg)) != 0)	{
					setUserRegSafe(reg, cpu.read32(address));
					address -= 4;
				}
			}
		}
	}

	//POST DECR - write back and user mode/SPSR transfer
	private void ldmdaws(int base, int list) {
		int address = cpu.getReg(base);
		if ((list & 0x8000) == 0x8000) { //R15 is in list - special mode change
			setRegSafeCPSR(15, cpu.read32(address));
			address -= 4;
			for (byte reg = 14; reg >= 0; --reg) { 
				if ((list & (1 << reg)) != 0)	{
					setRegSafe(reg, cpu.read32(address));
					address -= 4;
				}
			}
		}
		else {
			for (byte reg = 14; reg >= 0; --reg) { 
				if ((list & (1 << reg)) != 0)	{
					setUserRegSafe(reg, cpu.read32(address));
					address -= 4;
				}
			}
		}
		setRegSafe(base, address);
	}

	//POST INCR
	private void stmia(int base, int list) {
		int address = cpu.getReg(base);
		for (byte reg = 0; reg <= 15; ++reg) {
			if ((list & (1 << reg)) != 0)	{
				cpu.write32(address, getRegDelayedPC(reg));
				address += 4;
			}
		}
	}

	//POST INCR - write back
	private void stmiaw(int base, int list) {
		int address = cpu.getReg(base);
		for (byte reg = 0; reg <= 15; ++reg) {
			if ((list & (1 << reg)) != 0)	{
				cpu.write32(address, getRegDelayedPC(reg));
				address += 4;
			}
		}
		setRegSafe(base, address);
	}

	//POST INCR - user mode
	private void stmias(int base, int list) {
		int address = cpu.getReg(base);
		for (byte reg = 0; reg <= 15; ++reg) {
			if ((list & (1 << reg)) != 0)	{
				cpu.write32(address, getUserRegDelayedPC(reg));
				address += 4;
			}
		}
	}

	//POST INCR - write back and user mode
	private void stmiaws(int base, int list) {
		int address = cpu.getReg(base);
		for (byte reg = 0; reg <= 15; ++reg) {
			if ((list & (1 << reg)) != 0)	{
				cpu.write32(address, getUserRegDelayedPC(reg));
				address += 4;
			}
		}
		setRegSafe(base, address);
	}

	//POST INCR
	private void ldmia(int base, int list) {
		int address = cpu.getReg(base);
		for (byte reg = 0; reg <= 15; ++reg) {
			if ((list & (1 << reg)) != 0)	{
				setRegSafe(reg, cpu.read32(address));
				address += 4;
			}
		}
	}

	//POST INCR - write back
	private void ldmiaw(int base, int list) {
		int address = cpu.getReg(base);
		for (byte reg = 0; reg <= 15; ++reg) {
			if ((list & (1 << reg)) != 0)	{
				setRegSafe(reg, cpu.read32(address));
				address += 4;
			}
		}
		setRegSafe(base, address);
	}

	//POST INCR - user mode/SPSR transfer
	private void ldmias(int base, int list) {
		int address = cpu.getReg(base);
		if ((list & 0x8000) == 0x8000) { //R15 is in list - special mode change
			for (byte reg = 0; reg <= 14; ++reg) { 
				if ((list & (1 << reg)) != 0)	{
					setRegSafe(reg, cpu.read32(address));
					address += 4;
				}
			}
			setRegSafeCPSR(15, cpu.read32(address));
			address += 4;
		}
		else {
			for (byte reg = 0; reg <= 14; ++reg) { 
				if ((list & (1 << reg)) != 0)	{
					setUserRegSafe(reg, cpu.read32(address));
					address += 4;
				}
			}
		}
	}

	//POST INCR - write back and user mode/SPSR transfer
	private void ldmiaws(int base, int list) {
		int address = cpu.getReg(base);
		if ((list & 0x8000) == 0x8000) { //R15 is in list - special mode change
			for (byte reg = 0; reg <= 14; ++reg) { 
				if ((list & (1 << reg)) != 0)	{
					setRegSafe(reg, cpu.read32(address));
					address += 4;
				}
			}
			setRegSafeCPSR(15, cpu.read32(address));
			address += 4;
		}
		else {
			for (byte reg = 0; reg <= 14; ++reg) { 
				if ((list & (1 << reg)) != 0)	{
					setUserRegSafe(reg, cpu.read32(address));
					address += 4;
				}
			}
		}
		setRegSafe(base, address);
	}

	private void branchLink(int instr) {
		cpu.setLR(cpu.getPC() - 4);
		//Sign extended offset
		int offset = (instr << 8) >> 6;
		cpu.branch(cpu.getPC() + offset);
	}

	private void branch(int instr) {
		//Sign extended offset
		int offset = (instr << 8) >> 6;
		cpu.branch(cpu.getPC() + offset);
	}

	private void coprocDataTransferPre(int instr) {
		cpu.undefinedInstr("Coprocessor data transfer (pre) is not available");
	}

	private void coprocDataTransferPost(int instr) {
		cpu.undefinedInstr("Coprocessor data transfer (post) is not available");
	}

	private void coprocDataOperation(int instr) {
		cpu.undefinedInstr("Coprocessor data operation is not available");
	}

	private void coprocRegisterTransfer(int instr) {
		cpu.undefinedInstr("Coprocessor register transfer is not available");
	}

	private void softwareInterrupt(int instr) {
		cpu.softwareInterrupt(instr & 0xFFFFFF);
	}

}
