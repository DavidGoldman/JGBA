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

	private int getRegDelayedPC(byte reg) {
		return (reg & 0xF) == 0xF ? cpu.getPC() + 4 : cpu.getReg(reg);
	}

	private int getUserRegDelayedPC(byte reg) {
		return (reg & 0xF) == 0xF ? cpu.getPC() + 4 : cpu.getUserReg(reg);
	}

	private void setRegSafe(byte reg, int val) {
		if ((reg & 0xF) == 0xF)
			cpu.branch(val & 0xFFFFFFFC);
		else
			cpu.setReg(reg, val);
	}

	private void setUserRegSafe(byte reg, int val) {
		if ((reg & 0xF) == 0xF)
			cpu.branch(val & 0xFFFFFFFC);
		else
			cpu.setUserReg(reg, val);
	}

	private void setRegSafeCPSR(byte reg, int val) {
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
		/*4 Bytes stored in Little-Endian format
		  31-24, 23-16 */
		byte top = cpu.accessROM(pc+3), midTop = cpu.accessROM(pc+2);
		/*15-8, 7-0*/
		byte midBot = cpu.accessROM(pc+1), bot = cpu.accessROM(pc);

		/*Top four bits of top are the condition codes
		  Byte indices start at 0, domain [0, 31]*/
		if (Condition.condition((byte) ((top >>> 4) & 0xF), cpu.cpsr)) {
			byte bit27_to_24 = (byte) (top & 0xF);
			byte bit23_to_20 = (byte) ((midTop >>> 4) & 0xF);

			switch(bit27_to_24) {
			case 0x0:
				if ((bot & 0x10) == 0 || (bot & 0x80) == 0) //Bit 4 or bit 7 clear
					dataProcPSRReg(top, midTop, midBot, bot);
				else if ((bot & 0x60) == 0) { //Bit 6,5 are CLEAR
					if ((bit23_to_20 & 0xC) == 0)
						multiply(midTop, midBot, bot);
					else if ((bit23_to_20 & 0x8) == 0x8)
						multiplyLong(midTop, midBot, bot);
					else
						cpu.undefinedInstr("Illegal multiply varation");
				}
				else { //Bit 6,5 are NOT both CLEAR, implies Halfword DT
					if ((bit23_to_20 & 0x4) == 0x4) //Bit 22 is SET
						halfwordDTImmPost(midTop, midBot, bot);
					else if ((midBot & 0xF) == 0) //Bit 22 is CLEAR AND Bit 11-8 CLEAR
						halfwordDTRegPost(midTop, midBot, bot);
					else
						cpu.undefinedInstr("Illegal (post) halfword data transfer variation");
				}
				break;
			case 0x1:
				if (midTop == (byte)0x2F && midBot == (byte)0xFF && (bot & 0xF0) == 0x10) //0x12FFF1, Rn
					branchAndExchange((byte) (bot & 0xF));
				else if ((bot & 0x10) == 0 || (bot & 0x80) == 0) //Bit 4 or bit 7 clear
					dataProcPSRReg(top, midTop, midBot, bot); 
				else if ((bot & 0x60) == 0) { //Bit 6,5 are CLEAR
					if ((bit23_to_20 & 0xB) == 0 && (midBot & 0xF) == 0) //Bit 27-25 CLEAR, Bit 24 SET, BIT 23,21,20 CLEAR, Bit 11-8 CLEAR
						singleDataSwap(midTop, midBot, bot);
					else
						cpu.undefinedInstr("Illegal single data swap variation");
				}
				else { //Bit 6,5 are NOT both CLEAR, implies Halfword DT
					if ((bit23_to_20 & 0x4) == 0x4) //Bit 22 is SET
						halfwordDTImmPre(midTop, midBot, bot);
					else if ((midBot & 0xF) == 0) //Bit 22 is CLEAR AND Bit 11-8 CLEAR
						halfwordDTRegPre(midTop, midBot, bot);
					else
						cpu.undefinedInstr("Illegal (pre) halfword data transfer variation");
				}
				break;
			case 0x2: dataProcPSRImm(top, midTop, midBot, bot); break;
			case 0x3: dataProcPSRImm(top, midTop, midBot, bot); break;
			case 0x4: singleDataTransferImmPost(midTop, midBot, bot); break;
			case 0x5: singleDataTransferImmPre(midTop, midBot, bot); break;
			case 0x6: 
				if ((bot & 0x10) == 0) /*Bit 4 CLEAR*/
					singleDataTransferRegPost(midTop, midBot, bot);
				else
					undefinedTrap();
				break;
			case 0x7:
				if ((bot & 0x10) == 0) /*Bit 4 CLEAR*/
					singleDataTransferRegPre(midTop, midBot, bot);
				else
					undefinedTrap();
				break;
			case 0x8: blockDataTransferPost(midTop, midBot, bot); break;
			case 0x9: blockDataTransferPre(midTop, midBot, bot); break;
			case 0xA: branch(midTop, midBot, bot); break;
			case 0xB: branchLink(midTop, midBot, bot); break;
			case 0xC: coprocDataTransferPost(midTop, midBot, bot); break;
			case 0xD: coprocDataTransferPre(midTop, midBot, bot); break;
			case 0xE:
				if ((bot & 0x10) == 0) /*Bit 4 CLEAR*/
					coprocDataOperation(midTop, midBot, bot);
				else
					coprocRegisterTransfer(midTop, midBot, bot);
				break;
			case 0xF: softwareInterrupt(midTop, midBot, bot); break;
			}

		}
	}

	private void branchAndExchange(byte rn) {
		int address = cpu.getReg(rn);
		if ((address & 0x1) == 0)
			cpu.branch(address & 0xFFFFFFFC); //Word aligned
		else {
			cpu.cpsr.thumb = true;	
			cpu.branch(address & 0xFFFFFFFE); //Halfword aligned
		}
	}

	private void dataProcPSRReg(byte top, byte midTop, byte midBot, byte bot) {
		byte opcode = (byte) (((top & 0x1) << 3) | ((midTop & 0xE0) >>> 5));
		byte rd = (byte) ((midBot & 0xF0) >>> 4);
		byte shift = (byte) (((midBot & 0xF) << 4) | ((bot & 0xF0) >>> 4));
		byte rm = (byte) (bot & 0xF);
		if ((midTop & 0x10) == 0x10) //Bit 20 SET
			dataProcS(opcode, rd, getRegDelayedPC(midTop), getOp2S(shift, rm));
		else if(opcode >= TST && opcode <= CMN) //PSR Transfer
			psrTransfer(midTop, midBot, bot);
		else		
			dataProc(opcode, rd, getRegDelayedPC(midTop), getOp2(shift, rm));
	}

	private int getOp2(byte shift, byte rm) {
		byte type = (byte)((shift & 0x6) >>> 1); //type is bit 6-5
		if ((shift & 0x1) == 0) { //shift unsigned integer
			int imm5 = ((shift & 0xF8) >>> 3); //bit 11-7
			switch(type) {
			case 0: return lsli(rm, imm5);
			case 1: return lsri(rm, imm5);
			case 2: return asri(rm, imm5);
			case 3: return rori(rm, imm5);
			}
		}
		else {
			byte rs = (byte) ((shift & 0xF0) >>> 4); //rs is bit 11-8
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

	private int lsli(byte rm, int imm5) {
		return cpu.getReg(rm) << imm5;
	}

	private int lsri(byte rm, int imm5) {
		return (imm5 == 0) ? 0 : cpu.getReg(rm) >>> imm5; //LSR 0 is actually LSR #32
	}

	private int asri(byte rm, int imm5) {
		if (imm5 == 0) //ASR 0 is actually ASR #32 -> same as ASR #31 value wise
			imm5 = 31; 
		return cpu.getReg(rm) >> imm5;
	}

	private int rori(byte rm, int imm5) {
		int reg = cpu.getReg(rm);
		if (imm5 > 0) //ROR
			return (reg >>> imm5) | (reg << (32 - imm5));
		else //RRX
			return ((cpu.cpsr.carry) ? 0x80000000 : 0) | (reg >>> 1);
	}

	private int lslr(byte rm, byte rs) {
		int reg = getRegDelayedPC(rm);
		int shift = getRegDelayedPC(rs) & 0xFF;
		return (shift < 32) ? reg << shift : 0;
	}

	private int lsrr(byte rm, byte rs) {
		int reg = getRegDelayedPC(rm);
		int shift = getRegDelayedPC(rs) & 0xFF;
		return (shift < 32) ? reg >>> shift : 0;
	}

	private int asrr(byte rm, byte rs) {
		int reg = getRegDelayedPC(rm);
		int shift = getRegDelayedPC(rs) & 0xFF;
		if (shift > 31)
			shift = 31;
		return reg >> shift;
	}

	private int rorr(byte rm, byte rs) {
		int reg = getRegDelayedPC(rm);
		int shift = getRegDelayedPC(rs) & 0x1F;
		return (shift > 0) ? (reg >>> shift) | (reg << (32 - shift)) : reg;
	}

	private int getOp2S(byte shift, byte rm) {
		byte type = (byte)((shift & 0x6) >>> 1); //type is bit 6-5
		if ((shift & 0x1) == 0) { //shift unsigned integer
			int imm5 = ((shift & 0xF8) >>> 3); //bit 11-7
			switch(type) {
			case 0: return lslis(rm, imm5);
			case 1: return lsris(rm, imm5);
			case 2: return asris(rm, imm5);
			case 3: return roris(rm, imm5);
			}
		}
		else {
			byte rs = (byte) ((shift & 0xF0) >>> 4); //rs is bit 11-8
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

	private int lslis(byte rm, int imm5) {
		int reg = cpu.getReg(rm);
		if (imm5 > 0)
			cpu.cpsr.carry = (reg << (imm5-1) < 0);
		return reg << imm5;
	}

	private int lsris(byte rm, int imm5) {
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

	private int asris(byte rm, int imm5) {
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

	private int roris(byte rm, int imm5) {
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

	private int lslrs(byte rm, byte rs) {
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

	private int lsrrs(byte rm, byte rs) {
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

	private int asrrs(byte rm, byte rs) {
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

	private int rorrs(byte rm, byte rs) {
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

	private void dataProcPSRImm(byte top, byte midTop, byte midBot, byte bot) {
		byte opcode = (byte) (((top & 0x1) << 3) | ((midTop & 0xE0) >>> 5));
		byte rd = (byte) ((midBot & 0xF0) >>> 4);
		if ((midTop & 0x10) == 0x10) //Bit 20 SET
			dataProcS(opcode, rd, cpu.getReg(midTop), immOpS(bot & 0xFF, midBot & 0xF));
		else if(opcode >= TST && opcode <= CMN) //PSR Transfer
			psrTransferImm(midTop, midBot, bot);
		else		
			dataProc(opcode, rd, cpu.getReg(midTop), immOp(bot & 0xFF, midBot & 0xF));
	}

	private void psrTransfer(byte midTop, byte midBot, byte bot) {
		boolean spsr = ((midTop & 0x40) == 0x40); //Bit 22
		byte bit21_to_16 = (byte) (midTop & 0x3F);
		if (bit21_to_16 == (byte) 0x0F && (midBot & 0xF) == 0 && bot == 0) //Bit 21-16 is 001111 (0x0F), bit 11-0 is 0
			mrs((byte)((midBot & 0xF0) >>> 4), spsr); //Rd is bit 15-12
		else if (bit21_to_16 == (byte) 0x29 && midBot == (byte) (0xF0) && (bot & 0xF0) == 0) //Bit 21-16 is 101001 (0x29), bit 15-12 is 1, bit 11-4 is 0
			msr(bot, spsr); //Rm is bit 3-0
		else if (bit21_to_16 == (byte) 0x28 && midBot == (byte) (0xF0) && (bot & 0xF0) == 0) //Bit 21-16 is 101000 (0x28), bit 15-12 is 1, bit 11-4 is 0
			msrFLG(cpu.getReg(bot), spsr); //Rm is bit 3-0
		else
			cpu.undefinedInstr("Illegal (reg) psr transfer variation");
	}

	private void mrs(byte reg, boolean spsr) {
		setRegSafe(reg, (spsr) ? cpu.getSPSR() : cpu.cpsr.save());
	}

	private void msr(byte reg, boolean spsr) {
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

	private void psrTransferImm(byte midTop, byte midBot, byte bot) {
		if ((midTop & 0x3F) == 0x28 && (midBot & 0xF0) == 0xF0) //Bit 21-16 is 101000 (0x28), bit 15-12 is 1
			msrFLG(immOp(bot & 0xFF, midBot & 0xF), (midTop & 0x40) == 0x40);
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

	private void dataProcS(byte opcode, byte rd, int op1, int op2) {
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

	private void dataProc(byte opcode, byte rd, int op1, int op2) {
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

	private void and(byte rd, int op1, int op2) {
		setRegSafe(rd, op1 & op2);
	}

	private void ands(byte rd, int op1, int op2) {
		int val = op1 & op2;
		cpu.cpsr.negative = (val < 0);
		cpu.cpsr.zero = (val == 0);
		setRegSafeCPSR(rd, val);
	}

	private void eor(byte rd, int op1, int op2) {
		setRegSafe(rd, op1 ^ op2);
	}

	private void eors(byte rd, int op1, int op2) {
		int val = op1 ^ op2;
		cpu.cpsr.negative = (val < 0);
		cpu.cpsr.zero = (val == 0);
		setRegSafeCPSR(rd, val);
	}

	private void sub(byte rd, int op1, int op2) {
		setRegSafe(rd, op1 - op2);
	}

	private void subs(byte rd, int op1, int op2) {
		setRegSafeCPSR(rd, cpu.cpsr.setSubFlags(op1, op2));
	}

	private void rsb(byte rd, int op1, int op2) {
		setRegSafe(rd, op2 - op1);
	}

	private void rsbs(byte rd, int op1, int op2) {
		setRegSafeCPSR(rd, cpu.cpsr.setSubFlags(op2, op1));
	}

	private void add(byte rd, int op1, int op2) {
		setRegSafe(rd, op1 + op2);
	}

	private void adds(byte rd, int op1, int op2) {
		setRegSafeCPSR(rd, cpu.cpsr.setAddFlags(op1, op2));
	}

	private void adc(byte rd, int op1, int op2) {
		setRegSafe(rd, op1 + op2 + ((cpu.cpsr.carry) ? 1 : 0));
	}

	private void adcs(byte rd, int op1, int op2) {
		setRegSafeCPSR(rd, cpu.cpsr.setAddCarryFlags(op1, op2));
	}

	private void sbc(byte rd, int op1, int op2) {
		setRegSafe(rd, op1 - op2 - ((cpu.cpsr.carry) ? 0 : 1));
	}

	private void sbcs(byte rd, int op1, int op2) {
		setRegSafeCPSR(rd, cpu.cpsr.setSubCarryFlags(op1, op2));
	}

	private void rsc(byte rd, int op1, int op2) {
		setRegSafe(rd, op2 - op1 - ((cpu.cpsr.carry) ? 0 : 1));
	}

	private void rscs(byte rd, int op1, int op2) {
		setRegSafeCPSR(rd, cpu.cpsr.setSubCarryFlags(op2, op1));
	}

	private void tst(byte rd, int op1, int op2) {
		int val = op1 & op2;
		cpu.cpsr.negative = (val < 0);
		cpu.cpsr.zero = (val == 0);
	}

	private void teq(byte rd, int op1, int op2) {
		int val = op1 ^ op2;
		cpu.cpsr.negative = (val < 0);
		cpu.cpsr.zero = (val == 0);
	}

	private void cmp(byte rd, int op1, int op2) {
		cpu.cpsr.setSubFlags(op1, op2);
	}

	private void cmn(byte rd, int op1, int op2) {
		cpu.cpsr.setAddFlags(op1, op2);
	}

	private void orr(byte rd, int op1, int op2) {
		setRegSafe(rd, op1 | op2);
	}

	private void orrs(byte rd, int op1, int op2) {
		int val = op1 | op2;
		cpu.cpsr.negative = (val < 0);
		cpu.cpsr.zero = (val == 0);
		setRegSafeCPSR(rd, val);
	}

	private void mov(byte rd, int op1, int op2) {
		setRegSafe(rd, op2);
	}

	private void movs(byte rd, int op1, int op2) {
		cpu.cpsr.negative = (op2 < 0);
		cpu.cpsr.zero = (op2 == 0);
		setRegSafeCPSR(rd, op2);
	}

	private void bic(byte rd, int op1, int op2) {
		setRegSafe(rd, op1 & ~op2);
	}

	private void bics(byte rd, int op1, int op2) {
		int val = op1 & ~op2;
		cpu.cpsr.negative = (val < 0);
		cpu.cpsr.zero = (val == 0);
		setRegSafeCPSR(rd, val);
	}

	private void mvn(byte rd, int op1, int op2) {
		setRegSafe(rd, ~op2);
	}

	private void mvns(byte rd, int op1, int op2) {
		op2 = ~op2;
		cpu.cpsr.negative = (op2 < 0);
		cpu.cpsr.zero = (op2 == 0);
		setRegSafeCPSR(rd, op2);
	}

	private void multiply(byte midTop, byte midBot, byte bot) {
		byte as = (byte) ((midTop & 0x30) >>> 4); //accumulate, set bits
		switch(as){
		//We don't need to & 0xF -> methods will do it for us
		case 0: mul(midTop, bot, midBot); break;
		case 1: muls(midTop, bot, midBot); break;
		case 2: mla(midTop, bot, midBot, (byte) ((midBot & 0xF0) >>> 4)); break;
		case 3: mlas(midTop, bot, midBot, (byte) ((midBot & 0xF0) >>> 4)); break;
		}
	}

	private void mul(byte rd, byte rm, byte rs) {
		setRegSafe(rd, cpu.getReg(rm) * cpu.getReg(rs));
	}

	private void muls(byte rd, byte rm, byte rs) {
		int val = cpu.getReg(rm) * cpu.getReg(rs);
		cpu.cpsr.carry = false;
		cpu.cpsr.negative = (val < 0);
		cpu.cpsr.zero = (val == 0);
		setRegSafe(rd, val);
	}

	private void mla(byte rd, byte rm, byte rs, byte rn) {
		setRegSafe(rd, cpu.getReg(rm)*cpu.getReg(rs) + cpu.getReg(rn));
	}

	private void mlas(byte rd, byte rm, byte rs, byte rn) {
		int val = cpu.getReg(rm)*cpu.getReg(rs) + cpu.getReg(rn);
		cpu.cpsr.carry = false;
		cpu.cpsr.negative = (val < 0);
		cpu.cpsr.zero = (val == 0);
		setRegSafe(rd, val);
	}

	private void multiplyLong(byte midTop, byte midBot, byte bot) {
		byte sas = (byte) ((midTop & 0x70) >>> 4); //signed, accumulate, set bits
		switch(sas) {
		//We don't need to & 0xF -> methods will do it for us
		case 0: umull(midTop, (byte) ((midBot & 0xF0) >>> 4), bot, midBot); break;
		case 1: umulls(midTop, (byte) ((midBot & 0xF0) >>> 4), bot, midBot); break;
		case 2: umlal(midTop, (byte) ((midBot & 0xF0) >>> 4), bot, midBot); break;
		case 3: umlals(midTop, (byte) ((midBot & 0xF0) >>> 4), bot, midBot); break;
		case 4: smull(midTop, (byte) ((midBot & 0xF0) >>> 4), bot, midBot); break;
		case 5: smulls(midTop, (byte) ((midBot & 0xF0) >>> 4), bot, midBot); break;
		case 6: smlal(midTop, (byte) ((midBot & 0xF0) >>> 4), bot, midBot); break;
		case 7: smlals(midTop, (byte) ((midBot & 0xF0) >>> 4), bot, midBot); break;
		}
	}

	private void umull(byte rdHi, byte rdLo, byte rm, byte rs) {
		long result = (cpu.getReg(rm) & 0xFFFFFFFFL)*(cpu.getReg(rs) & 0xFFFFFFFFL);
		setRegSafe(rdHi, (int) (result >>> 32));
		setRegSafe(rdLo, (int) result);
	}

	private void umulls(byte rdHi, byte rdLo, byte rm, byte rs) {
		long result = (cpu.getReg(rm) & 0xFFFFFFFFL)*(cpu.getReg(rs) & 0xFFFFFFFFL);
		cpu.cpsr.carry = false;
		cpu.cpsr.negative = (result < 0);
		cpu.cpsr.zero = (result == 0);
		setRegSafe(rdHi, (int) (result >>> 32));
		setRegSafe(rdLo, (int) result);
	}

	private void umlal(byte rdHi, byte rdLo, byte rm, byte rs) {
		long result = (cpu.getReg(rm) & 0xFFFFFFFFL)*(cpu.getReg(rs) & 0xFFFFFFFFL) + (((cpu.getReg(rdHi) & 0xFFFFFFFFL) << 32) | (cpu.getReg(rdLo) & 0xFFFFFFFFL));
		setRegSafe(rdHi, (int) (result >>> 32));
		setRegSafe(rdLo, (int) result);
	}

	private void umlals(byte rdHi, byte rdLo, byte rm, byte rs) {
		long result = (cpu.getReg(rm) & 0xFFFFFFFFL)*(cpu.getReg(rs) & 0xFFFFFFFFL) + (((cpu.getReg(rdHi) & 0xFFFFFFFFL) << 32) | (cpu.getReg(rdLo) & 0xFFFFFFFFL));
		cpu.cpsr.carry = false;
		cpu.cpsr.negative = (result < 0);
		cpu.cpsr.zero = (result == 0);
		setRegSafe(rdHi, (int) (result >>> 32));
		setRegSafe(rdLo, (int) result);
	}

	private void smull(byte rdHi, byte rdLo, byte rm, byte rs) {
		long result = ((long) cpu.getReg(rm))*cpu.getReg(rs);
		setRegSafe(rdHi, (int) (result >>> 32));
		setRegSafe(rdLo, (int) result);
	}

	private void smulls(byte rdHi, byte rdLo, byte rm, byte rs) {
		long result = ((long) cpu.getReg(rm))*cpu.getReg(rs);
		cpu.cpsr.carry = false;
		cpu.cpsr.negative = (result < 0);
		cpu.cpsr.zero = (result == 0);
		setRegSafe(rdHi, (int) (result >>> 32));
		setRegSafe(rdLo, (int) result);
	}

	private void smlal(byte rdHi, byte rdLo, byte rm, byte rs) {
		long result = ((long) cpu.getReg(rm))*cpu.getReg(rs) + (((cpu.getReg(rdHi) & 0xFFFFFFFFL) << 32) | (cpu.getReg(rdLo) & 0xFFFFFFFFL));
		setRegSafe(rdHi, (int) (result >>> 32));
		setRegSafe(rdLo, (int) result);
	}

	private void smlals(byte rdHi, byte rdLo, byte rm, byte rs) {
		long result = ((long) cpu.getReg(rm))*cpu.getReg(rs) + (((cpu.getReg(rdHi) & 0xFFFFFFFFL) << 32) | (cpu.getReg(rdLo) & 0xFFFFFFFFL));
		cpu.cpsr.carry = false;
		cpu.cpsr.negative = (result < 0);
		cpu.cpsr.zero = (result == 0);
		setRegSafe(rdHi, (int) (result >>> 32));
		setRegSafe(rdLo, (int) result);
	}

	private void singleDataSwap(byte midTop, byte midBot, byte bot) {
		if ((midTop & 0x40) == 0x40) //Bit 22 SET - byte quantity
			swpb(midTop, (byte) ((midBot & 0xF0) >>> 4), bot);
		else
			swp(midTop, (byte) ((midBot & 0xF0) >>> 4), bot);	
	}

	private void swpb(byte rn, byte rd, byte rs) {
		int address = cpu.getReg(rn);
		int contents = cpu.read8(address);
		cpu.write8(address, cpu.getReg(rs));
		setRegSafe(rd, contents);
	}

	private void swp(byte rn, byte rd, byte rs) {
		int address = cpu.getReg(rn);
		int contents = cpu.read32(address);
		cpu.write32(address, cpu.getReg(rs));
		setRegSafe(rd, contents);
	}

	private void halfwordDTImmPost(byte midTop, byte midBot, byte bot) {
		if ((midTop & 0x20) == 0x20) { //Write back should be 0
			cpu.undefinedInstr("Halfword data transfer POST write back bit must be CLEAR");
			return; 
		}
		//rn = midTop
		byte rd = (byte) ((midBot & 0xF0) >>> 4);
		int imm8 = ((midBot & 0xF) << 4)| (bot & 0xF);

		//Bit 23 is U bit - up or down 
		//Post indexed data transfers always write back the modified base
		//int address = ((midTop & 0x80) == 0x80) ? basePostIncr(midTop, offset) : basePostDecr(midTop, offset);
		byte lsh = (byte) (((midTop & 0x10) >>> 2) | ((bot & 0x60) >>> 5)); // load/store, signed/unsigned, halfword/byte
		switch(lsh) {
		case 0: break; //swp - won't happen
		case 1: strh(rd, ((midTop & 0x80) == 0x80) ? basePostIncr(midTop, imm8) : basePostDecr(midTop, imm8)); break;
		case 2: cpu.undefinedInstr("Halfword data transfer cannot store sign extended byte"); break; //invalid
		case 3: cpu.undefinedInstr("Halfword data transfer cannot store sign extended halfword"); break; //invalid
		case 4: break; //swp - won't happen
		case 5: ldrh(rd, ((midTop & 0x80) == 0x80) ? basePostIncr(midTop, imm8) : basePostDecr(midTop, imm8)); break;
		case 6: ldrsb(rd, ((midTop & 0x80) == 0x80) ? basePostIncr(midTop, imm8) : basePostDecr(midTop, imm8)); break;
		case 7: ldrsh(rd, ((midTop & 0x80) == 0x80) ? basePostIncr(midTop, imm8) : basePostDecr(midTop, imm8)); break;
		}
	}

	private void strh(byte reg, int address) {
		cpu.write16(address, getRegDelayedPC(reg));
	}

	private void ldrh(byte reg, int address) {
		setRegSafe(reg, cpu.read16(address));
	}

	private void ldrsh(byte reg, int address) {
		//Load sign extended half word
		setRegSafe(reg, (cpu.read16(address) << 16) >> 16);
	}

	private void ldrsb(byte reg, int address) {
		//Load sign extended byte
		setRegSafe(reg, (cpu.read8(address) << 24) >> 24);
	}

	private void halfwordDTImmPre(byte midTop, byte midBot, byte bot) {
		//rn = midTop
		byte rd = (byte) ((midBot & 0xF0) >>> 4);
		int imm8 = ((midBot & 0xF) << 4)| (bot & 0xF);

		byte uw = (byte) (((midTop & 0x80) >>> 6) | ((midTop & 0x20) >>> 5));
		byte lsh = (byte) (((midTop & 0x10) >>> 2) | ((bot & 0x60) >>> 5)); // load/store, signed/unsigned, halfword/byte
		switch(lsh) {
		case 0: break; //swp - won't happen
		case 1: strh(rd, getPreAddress(uw, rd, imm8)); break;
		case 2: cpu.undefinedInstr("Halfword data transfer cannot store sign extended byte"); break; //invalid
		case 3: cpu.undefinedInstr("Halfword data transfer cannot store sign extended halfword"); break; //invalid
		case 4: break; //swp - won't happen
		case 5: ldrh(rd, getPreAddress(uw, rd, imm8)); break;
		case 6: ldrsb(rd, getPreAddress(uw, rd, imm8)); break;
		case 7: ldrsh(rd, getPreAddress(uw, rd, imm8)); break;
		}
	}

	private int getPreAddress(byte uw, byte reg, int offset) {
		//uw - up/down, write back/don't
		switch(uw) {
		case 0: return baseDecr(reg, offset);
		case 1: return basePreDecr(reg, offset);
		case 2: return baseIncr(reg, offset);
		case 3: return basePreIncr(reg, offset);
		}
		//Should never occur
		throw new RuntimeException();
		//return 0;
	}

	private void halfwordDTRegPost(byte midTop, byte midBot, byte bot) {
		if ((midTop & 0x20) == 0x20) { //Write back should be 0
			cpu.undefinedInstr("Halfword data transfer POST write back bit must be CLEAR");
			return; 
		}
		//rn = midTop
		byte rd = (byte) ((midBot & 0xF0) >>> 4);
		int offset = cpu.getReg(bot);

		//Bit 23 is U bit - up or down 
		//Post indexed data transfers always write back the modified base
		//int address = ((midTop & 0x80) == 0x80) ? basePostIncr(midTop, offset) : basePostDecr(midTop, offset);
		byte lsh = (byte) (((midTop & 0x10) >>> 2) | ((bot & 0x60) >>> 5)); // load/store, signed/unsigned, halfword/byte
		switch(lsh) {
		case 0: break; //swp - won't happen
		case 1: strh(rd, ((midTop & 0x80) == 0x80) ? basePostIncr(midTop, offset) : basePostDecr(midTop, offset)); break;
		case 2: cpu.undefinedInstr("Halfword data transfer cannot store sign extended byte"); break; //invalid
		case 3: cpu.undefinedInstr("Halfword data transfer cannot store sign extended halfword"); break; //invalid
		case 4: break; //swp - won't happen
		case 5: ldrh(rd, ((midTop & 0x80) == 0x80) ? basePostIncr(midTop, offset) : basePostDecr(midTop, offset)); break;
		case 6: ldrsb(rd, ((midTop & 0x80) == 0x80) ? basePostIncr(midTop, offset) : basePostDecr(midTop, offset)); break;
		case 7: ldrsh(rd, ((midTop & 0x80) == 0x80) ? basePostIncr(midTop, offset) : basePostDecr(midTop, offset)); break;
		}
	}

	private void halfwordDTRegPre(byte midTop, byte midBot, byte bot) {
		//rn = midTop
		byte rd = (byte) ((midBot & 0xF0) >>> 4);
		int offset = cpu.getReg(bot);

		byte uw = (byte) (((midTop & 0x80) >>> 6) | ((midTop & 0x20) >>> 5));
		byte lsh = (byte) (((midTop & 0x10) >>> 2) | ((bot & 0x60) >>> 5)); // load/store, signed/unsigned, halfword/byte
		switch(lsh) {
		case 0: break; //swp - won't happen
		case 1: strh(rd, getPreAddress(uw, rd, offset)); break;
		case 2: cpu.undefinedInstr("Halfword data transfer cannot store sign extended byte"); break; //invalid
		case 3: cpu.undefinedInstr("Halfword data transfer cannot store sign extended halfword"); break; //invalid
		case 4: break; //swp - won't happen
		case 5: ldrh(rd, getPreAddress(uw, rd, offset)); break;
		case 6: ldrsb(rd, getPreAddress(uw, rd, offset)); break;
		case 7: ldrsh(rd, getPreAddress(uw, rd, offset)); break;
		}
	}

	private void singleDataTransferImmPre(byte midTop, byte midBot, byte bot) {
		byte ubwl = (byte) ((midTop & 0xF0) >>> 4); // up/down, byte/word, write back/don't, load/store bits
		//rn = midTop
		byte rd = (byte) ((midBot & 0xF0) >>> 4);
		int imm12 = ((midBot & 0xF) << 8) | (bot & 0xFF);
		//8 - Up (else down)
		//4 - Byte (else word)
		//2 - Write back (else don't)
		//1 - Load (else store)
		switch(ubwl) { 
		case 0x0: str(rd, baseDecr(midTop, imm12)); break; //Group 1 - word decr
		case 0x1: ldr(rd, baseDecr(midTop, imm12)); break;
		case 0x2: str(rd, basePreDecr(midTop, imm12)); break;
		case 0x3: ldr(rd, basePreDecr(midTop, imm12)); break;
		case 0x4: strb(rd, baseDecr(midTop, imm12)); break; //Group 2 - byte decr
		case 0x5: ldrb(rd, baseDecr(midTop, imm12)); break;
		case 0x6: strb(rd, basePreDecr(midTop, imm12)); break;
		case 0x7: ldrb(rd, basePreDecr(midTop, imm12)); break;
		case 0x8: str(rd, baseIncr(midTop, imm12)); break; //Group 3 - word incr
		case 0x9: ldr(rd, baseIncr(midTop, imm12)); break;
		case 0xA: str(rd, basePreIncr(midTop, imm12)); break;
		case 0xB: ldr(rd, basePreIncr(midTop, imm12)); break;
		case 0xC: strb(rd, baseIncr(midTop, imm12)); break; //Group 4 - byte incr
		case 0xD: ldrb(rd, baseIncr(midTop, imm12)); break;
		case 0xE: strb(rd, basePreIncr(midTop, imm12)); break;
		case 0xF: ldrb(rd, basePreIncr(midTop, imm12)); break;
		}
	}

	private int baseIncr(byte base, int offset) {
		return cpu.getReg(base) + offset;
	}

	private int baseDecr(byte base, int offset) {
		return cpu.getReg(base) - offset;
	}

	private int basePreIncr(byte base, int offset) {
		int val = cpu.getReg(base) + offset;
		setRegSafe(base, val);
		return val;
	}

	private int basePreDecr(byte base, int offset) {
		int val = cpu.getReg(base) - offset;
		setRegSafe(base, val);
		return val;
	}

	private void str(byte reg, int address) {
		cpu.write32(address, getRegDelayedPC(reg));
	}

	private void ldr(byte reg, int address) {
		setRegSafe(reg, cpu.read32(address));
	}

	private void strb(byte reg, int address) {
		cpu.write8(address, getRegDelayedPC(reg));
	}

	private void ldrb(byte reg, int address) {
		setRegSafe(reg, cpu.read8(address));
	}

	private void singleDataTransferImmPost(byte midTop, byte midBot, byte bot) {
		byte ubtl = (byte) ((midTop & 0xF0) >>> 4); // up/down, byte/word, force non-privileged/don't, load/store bits
		//rn = midTop
		byte rd = (byte) ((midBot & 0xF0) >>> 4);
		int imm12 = ((midBot & 0xF) << 8) | (bot & 0xFF);
		//8 - Up (else down)
		//4 - Byte (else word)
		//2 - Force user mode (else don't)
		//1 - Load (else store)
		switch(ubtl) { 
		case 0x0: str(rd, basePostDecr(midTop, imm12)); break; //Group 1 - word decr
		case 0x1: ldr(rd, basePostDecr(midTop, imm12)); break;
		case 0x2: str(rd, basePostDecrUser(midTop, imm12)); break;
		case 0x3: ldr(rd, basePostDecrUser(midTop, imm12)); break;
		case 0x4: strb(rd, basePostDecr(midTop, imm12)); break; //Group 2 - byte decr
		case 0x5: ldrb(rd, basePostDecr(midTop, imm12)); break;
		case 0x6: strb(rd, basePostDecrUser(midTop, imm12)); break;
		case 0x7: ldrb(rd, basePostDecrUser(midTop, imm12)); break;
		case 0x8: str(rd, basePostIncr(midTop, imm12)); break; //Group 3 - word incr
		case 0x9: ldr(rd, basePostIncr(midTop, imm12)); break;
		case 0xA: str(rd, basePostIncrUser(midTop, imm12)); break;
		case 0xB: ldr(rd, basePostIncrUser(midTop, imm12)); break;
		case 0xC: strb(rd, basePostIncr(midTop, imm12)); break; //Group 4 - byte incr
		case 0xD: ldrb(rd, basePostIncr(midTop, imm12)); break;
		case 0xE: strb(rd, basePostIncrUser(midTop, imm12)); break;
		case 0xF: ldrb(rd, basePostIncrUser(midTop, imm12)); break;
		}
	}

	private int basePostDecr(byte base, int offset) {
		int val = cpu.getReg(base);
		setRegSafe(base, val - offset);
		return val;
	}

	private int basePostDecrUser(byte base, int offset) {
		int val = cpu.getUserReg(base);
		setUserRegSafe(base, val - offset);
		return val;
	}

	private int basePostIncr(byte base, int offset) {
		int val = cpu.getReg(base);
		setRegSafe(base, val + offset);
		return val;
	}

	private int basePostIncrUser(byte base, int offset) {
		int val = cpu.getUserReg(base);
		setUserRegSafe(base, val + offset);
		return val;
	}

	private void singleDataTransferRegPre(byte midTop, byte midBot, byte bot) {
		byte ubwl = (byte) ((midTop & 0xF0) >>> 4); // up/down, byte/word, write back/don't, load/store bits
		//rn = midTop
		byte rd = (byte) ((midBot & 0xF0) >>> 4);
		byte shift = (byte) (((midBot & 0xF) << 4) | ((bot & 0xF0) >>> 4));
		byte rm = (byte) (bot & 0xF);
		int offset = getOp2DT(shift, rm);
		//8 - Up (else down)
		//4 - Byte (else word)
		//2 - Write back (else don't)
		//1 - Load (else store)
		switch(ubwl) { 
		case 0x0: str(rd, baseDecr(midTop, offset)); break; //Group 1 - word decr
		case 0x1: ldr(rd, baseDecr(midTop, offset)); break;
		case 0x2: str(rd, basePreDecr(midTop, offset)); break;
		case 0x3: ldr(rd, basePreDecr(midTop, offset)); break;
		case 0x4: strb(rd, baseDecr(midTop, offset)); break; //Group 2 - byte decr
		case 0x5: ldrb(rd, baseDecr(midTop, offset)); break;
		case 0x6: strb(rd, basePreDecr(midTop, offset)); break;
		case 0x7: ldrb(rd, basePreDecr(midTop, offset)); break;
		case 0x8: str(rd, baseIncr(midTop, offset)); break; //Group 3 - word incr
		case 0x9: ldr(rd, baseIncr(midTop, offset)); break;
		case 0xA: str(rd, basePreIncr(midTop, offset)); break;
		case 0xB: ldr(rd, basePreIncr(midTop, offset)); break;
		case 0xC: strb(rd, baseIncr(midTop, offset)); break; //Group 4 - byte incr
		case 0xD: ldrb(rd, baseIncr(midTop, offset)); break;
		case 0xE: strb(rd, basePreIncr(midTop, offset)); break;
		case 0xF: ldrb(rd, basePreIncr(midTop, offset)); break;
		}
	}

	private int getOp2DT(byte shift, byte rm) {
		byte type = (byte)((shift & 0x6) >>> 1); //type is bit 6-5
		if ((shift & 0x1) == 0) { //shift unsigned integer
			int imm5 = ((shift & 0xF8) >>> 3); //bit 11-7
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

	private void singleDataTransferRegPost(byte midTop, byte midBot, byte bot) {
		byte ubtl = (byte) ((midTop & 0xF0) >>> 4); // up/down, byte/word, force non-privileged/don't, load/store bits
		//rn = midTop
		byte rd = (byte) ((midBot & 0xF0) >>> 4);
		byte shift = (byte) (((midBot & 0xF) << 4) | ((bot & 0xF0) >>> 4));
		byte rm = (byte) (bot & 0xF);
		int offset = getOp2DT(shift, rm);
		//8 - Up (else down)
		//4 - Byte (else word)
		//2 - Force user mode (else don't)
		//1 - Load (else store)
		switch(ubtl) { 
		case 0x0: str(rd, basePostDecr(midTop, offset)); break; //Group 1 - word decr
		case 0x1: ldr(rd, basePostDecr(midTop, offset)); break;
		case 0x2: str(rd, basePostDecrUser(midTop, offset)); break;
		case 0x3: ldr(rd, basePostDecrUser(midTop, offset)); break;
		case 0x4: strb(rd, basePostDecr(midTop, offset)); break; //Group 2 - byte decr
		case 0x5: ldrb(rd, basePostDecr(midTop, offset)); break;
		case 0x6: strb(rd, basePostDecrUser(midTop, offset)); break;
		case 0x7: ldrb(rd, basePostDecrUser(midTop, offset)); break;
		case 0x8: str(rd, basePostIncr(midTop, offset)); break; //Group 3 - word incr
		case 0x9: ldr(rd, basePostIncr(midTop, offset)); break;
		case 0xA: str(rd, basePostIncrUser(midTop, offset)); break;
		case 0xB: ldr(rd, basePostIncrUser(midTop, offset)); break;
		case 0xC: strb(rd, basePostIncr(midTop, offset)); break; //Group 4 - byte incr
		case 0xD: ldrb(rd, basePostIncr(midTop, offset)); break;
		case 0xE: strb(rd, basePostIncrUser(midTop, offset)); break;
		case 0xF: ldrb(rd, basePostIncrUser(midTop, offset)); break;
		}
	}

	private void undefinedTrap() {
		cpu.undefinedTrap();
	}

	private void blockDataTransferPre(byte midTop, byte midBot, byte bot) {
		//rn = midTop
		int list = ((midBot & 0xFF) << 8) | (bot & 0xFF);
		byte uswl = (byte) ((midTop & 0xF0) >>> 4); // up/down, loadPSR/force User mode/DON'T, write back base/don't, load/store 
		switch(uswl) { 
		case 0x0: stmdb(midTop, list); break; //PRE DECR
		case 0x1: ldmdb(midTop, list); break;
		case 0x2: stmdbw(midTop, list); break; //write back
		case 0x3: ldmdbw(midTop, list); break; //write back
		case 0x4: stmdbs(midTop, list); break; //user mode
		case 0x5: ldmdbs(midTop, list); break; //user mode/mode change
		case 0x6: stmdbws(midTop, list); break; //write back and user mode, TODO Verify this is legal
		case 0x7: ldmdbws(midTop, list); break; //write back and user mode, TODO Verify this is legal
		case 0x8: stmib(midTop, list); break; //PRE INCR
		case 0x9: ldmib(midTop, list); break;
		case 0xA: stmibw(midTop, list); break; //write back
		case 0xB: ldmibw(midTop, list); break; //write back
		case 0xC: stmibs(midTop, list); break; //user mode
		case 0xD: ldmibs(midTop, list); break; //user mode/mode change
		case 0xE: stmibws(midTop, list); break; //write back and user mode, TODO Verify this is legal
		case 0xF: ldmibws(midTop, list); break; //write back and user mode, TODO Verify this is legal
		}
	}

	//PRE DECR
	private void stmdb(byte base, int list) {
		int address = cpu.getReg(base);
		for (byte reg = 15; reg >= 0; --reg) { 
			if ((list & (1 << reg)) != 0)	{
				address -= 4;
				cpu.write32(address, getRegDelayedPC(reg));
			}
		}
	}

	//PRE DECR - write back
	private void stmdbw(byte base, int list) {
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
	private void stmdbs(byte base, int list) {
		int address = cpu.getReg(base);
		for (byte reg = 15; reg >= 0; --reg) { 
			if ((list & (1 << reg)) != 0)	{
				address -= 4;
				cpu.write32(address, getUserRegDelayedPC(reg));
			}
		}
	}

	//PRE DECR - write back and user mode
	private void stmdbws(byte base, int list) {
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
	private void ldmdb(byte base, int list) {
		int address = cpu.getReg(base);
		for (byte reg = 15; reg >= 0; --reg) { 
			if ((list & (1 << reg)) != 0)	{
				address -= 4;
				setRegSafe(reg, cpu.read32(address));
			}
		}
	}

	//PRE DECR - write back
	private void ldmdbw(byte base, int list) {
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
	private void ldmdbs(byte base, int list) {
		int address = cpu.getReg(base);
		if ((list & 0x8000) == 0x8000) { //R15 is in list - special mode change
			address -= 4;
			setRegSafeCPSR((byte)15, cpu.read32(address));
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
	private void ldmdbws(byte base, int list) {
		int address = cpu.getReg(base);
		if ((list & 0x8000) == 0x8000) { //R15 is in list - special mode change
			address -= 4;
			setRegSafeCPSR((byte)15, cpu.read32(address));
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
	private void stmib(byte base, int list) {
		int address = cpu.getReg(base);
		for (byte reg = 0; reg <= 15; ++reg) {
			if ((list & (1 << reg)) != 0)	{
				address += 4;
				cpu.write32(address, getRegDelayedPC(reg));
			}
		}
	}

	//PRE INCR - write back
	private void stmibw(byte base, int list) {
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
	private void stmibs(byte base, int list) {
		int address = cpu.getReg(base);
		for (byte reg = 0; reg <= 15; ++reg) {
			if ((list & (1 << reg)) != 0)	{
				address += 4;
				cpu.write32(address, getUserRegDelayedPC(reg));
			}
		}
	}

	//PRE INCR - write back and user mode
	private void stmibws(byte base, int list) {
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
	private void ldmib(byte base, int list) {
		int address = cpu.getReg(base);
		for (byte reg = 0; reg <= 15; ++reg) {
			if ((list & (1 << reg)) != 0)	{
				address += 4;
				setRegSafe(reg, cpu.read32(address));
			}
		}
	}

	//PRE INCR - write back
	private void ldmibw(byte base, int list) {
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
	private void ldmibs(byte base, int list) {
		int address = cpu.getReg(base);
		if ((list & 0x8000) == 0x8000) { //R15 is in list - special mode change
			for (byte reg = 0; reg <= 14; ++reg) { 
				if ((list & (1 << reg)) != 0)	{
					address += 4;
					setRegSafe(reg, cpu.read32(address));
				}
			}
			address += 4;
			setRegSafeCPSR((byte)15, cpu.read32(address));
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
	private void ldmibws(byte base, int list) {
		int address = cpu.getReg(base);
		if ((list & 0x8000) == 0x8000) { //R15 is in list - special mode change
			for (byte reg = 0; reg <= 14; ++reg) { 
				if ((list & (1 << reg)) != 0)	{
					address += 4;
					setRegSafe(reg, cpu.read32(address));
				}
			}
			address += 4;
			setRegSafeCPSR((byte)15, cpu.read32(address));
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

	private void blockDataTransferPost(byte midTop, byte midBot, byte bot) {
		//rn = midTop
		int list = ((midBot & 0xFF) << 8) | (bot & 0xFF);
		byte uswl = (byte) ((midTop & 0xF0) >>> 4); // up/down, loadPSR/force User mode/DON'T, write back base/don't, load/store 
		switch(uswl) {
		case 0x0: stmda(midTop, list); break; //POST DECR
		case 0x1: ldmda(midTop, list); break;
		case 0x2: stmdaw(midTop, list); break; //write back
		case 0x3: ldmdaw(midTop, list); break; //write back
		case 0x4: stmdas(midTop, list); break; //user mode
		case 0x5: ldmdas(midTop, list); break; //user mode/mode change
		case 0x6: stmdaws(midTop, list); break; //write back and user mode, TODO Verify this is legal
		case 0x7: ldmdaws(midTop, list); break; //write back and user mode, TODO Verify this is legal
		case 0x8: stmia(midTop, list); break; //POST INCR
		case 0x9: ldmia(midTop, list); break;
		case 0xA: stmiaw(midTop, list); break; //write back
		case 0xB: ldmiaw(midTop, list); break; //write back
		case 0xC: stmias(midTop, list); break; //user mode
		case 0xD: ldmias(midTop, list); break; //user mode/mode change
		case 0xE: stmiaws(midTop, list); break; //write back and user mode, TODO Verify this is legal
		case 0xF: ldmiaws(midTop, list); break; //write back and user mode, TODO Verify this is legal
		}
	}

	//POST DECR
	private void stmda(byte base, int list) {
		int address = cpu.getReg(base);
		for (byte reg = 15; reg >= 0; --reg) { 
			if ((list & (1 << reg)) != 0)	{
				cpu.write32(address, getRegDelayedPC(reg));
				address -= 4;
			}
		}
	}

	//POST DECR - write back
	private void stmdaw(byte base, int list) {
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
	private void stmdas(byte base, int list) {
		int address = cpu.getReg(base);
		for (byte reg = 15; reg >= 0; --reg) { 
			if ((list & (1 << reg)) != 0)	{
				cpu.write32(address, getUserRegDelayedPC(reg));
				address -= 4;
			}
		}
	}

	//POST DECR - write back and user mode
	private void stmdaws(byte base, int list) {
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
	private void ldmda(byte base, int list) {
		int address = cpu.getReg(base);
		for (byte reg = 15; reg >= 0; --reg) { 
			if ((list & (1 << reg)) != 0)	{
				setRegSafe(reg, cpu.read32(address));
				address -= 4;
			}
		}
	}

	//POST DECR - write back
	private void ldmdaw(byte base, int list) {
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
	private void ldmdas(byte base, int list) {
		int address = cpu.getReg(base);
		if ((list & 0x8000) == 0x8000) { //R15 is in list - special mode change
			setRegSafeCPSR((byte)15, cpu.read32(address));
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
	private void ldmdaws(byte base, int list) {
		int address = cpu.getReg(base);
		if ((list & 0x8000) == 0x8000) { //R15 is in list - special mode change
			setRegSafeCPSR((byte)15, cpu.read32(address));
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
	private void stmia(byte base, int list) {
		int address = cpu.getReg(base);
		for (byte reg = 0; reg <= 15; ++reg) {
			if ((list & (1 << reg)) != 0)	{
				cpu.write32(address, getRegDelayedPC(reg));
				address += 4;
			}
		}
	}

	//POST INCR - write back
	private void stmiaw(byte base, int list) {
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
	private void stmias(byte base, int list) {
		int address = cpu.getReg(base);
		for (byte reg = 0; reg <= 15; ++reg) {
			if ((list & (1 << reg)) != 0)	{
				cpu.write32(address, getUserRegDelayedPC(reg));
				address += 4;
			}
		}
	}

	//POST INCR - write back and user mode
	private void stmiaws(byte base, int list) {
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
	private void ldmia(byte base, int list) {
		int address = cpu.getReg(base);
		for (byte reg = 0; reg <= 15; ++reg) {
			if ((list & (1 << reg)) != 0)	{
				setRegSafe(reg, cpu.read32(address));
				address += 4;
			}
		}
	}

	//POST INCR - write back
	private void ldmiaw(byte base, int list) {
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
	private void ldmias(byte base, int list) {
		int address = cpu.getReg(base);
		if ((list & 0x8000) == 0x8000) { //R15 is in list - special mode change
			for (byte reg = 0; reg <= 14; ++reg) { 
				if ((list & (1 << reg)) != 0)	{
					setRegSafe(reg, cpu.read32(address));
					address += 4;
				}
			}
			setRegSafeCPSR((byte)15, cpu.read32(address));
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
	private void ldmiaws(byte base, int list) {
		int address = cpu.getReg(base);
		if ((list & 0x8000) == 0x8000) { //R15 is in list - special mode change
			for (byte reg = 0; reg <= 14; ++reg) { 
				if ((list & (1 << reg)) != 0)	{
					setRegSafe(reg, cpu.read32(address));
					address += 4;
				}
			}
			setRegSafeCPSR((byte)15, cpu.read32(address));
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

	private void branchLink(byte midTop, byte midBot, byte bot) {
		cpu.setLR(cpu.getPC() - 4);
		//Sign extended offset
		int offset = ((((midTop & 0xFF) << 16) | ((midBot & 0xFF) << 8) | (bot & 0xFF)) << 8) >> 6;
		cpu.branch(cpu.getPC() + offset);
	}

	private void branch(byte midTop, byte midBot, byte bot) {
		//Sign extended offset
		int offset = ((((midTop & 0xFF) << 16) | ((midBot & 0xFF) << 8) | (bot & 0xFF)) << 8) >> 6;
		cpu.branch(cpu.getPC() + offset);
	}

	private void coprocDataTransferPre(byte midTop, byte midBot, byte bot) {
		cpu.undefinedInstr("Coprocessor data transfer (pre) is not available");
	}

	private void coprocDataTransferPost(byte midTop, byte midBot, byte bot) {
		cpu.undefinedInstr("Coprocessor data transfer (post) is not available");
	}

	private void coprocDataOperation(byte midTop, byte midBot, byte bot) {
		cpu.undefinedInstr("Coprocessor data operation is not available");
	}

	private void coprocRegisterTransfer(byte midTop, byte midBot, byte bot) {
		cpu.undefinedInstr("Coprocessor register transfer is not available");
	}

	private void softwareInterrupt(byte midTop, byte midBot, byte bot) {
		cpu.softwareInterrupt(midTop, midBot, bot);
	}

}
