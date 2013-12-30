package cpu;

import static cpu.THUMBALUOpCode.ADC;
import static cpu.THUMBALUOpCode.AND;
import static cpu.THUMBALUOpCode.ASR;
import static cpu.THUMBALUOpCode.BIC;
import static cpu.THUMBALUOpCode.CMN;
import static cpu.THUMBALUOpCode.CMP;
import static cpu.THUMBALUOpCode.EOR;
import static cpu.THUMBALUOpCode.LSL;
import static cpu.THUMBALUOpCode.LSR;
import static cpu.THUMBALUOpCode.MUL;
import static cpu.THUMBALUOpCode.MVN;
import static cpu.THUMBALUOpCode.NEG;
import static cpu.THUMBALUOpCode.ORR;
import static cpu.THUMBALUOpCode.ROR;
import static cpu.THUMBALUOpCode.SBC;
import static cpu.THUMBALUOpCode.TST;

/*
 * TODO: Implement edge case for STM 
 *      -Writeback with Rb included in Rlist: Store OLD base if Rb is FIRST entry in Rlist, otherwise store NEW base
 */
public class THUMBProcessor implements CPU.IProcessor {

	private final CPU cpu;

	public THUMBProcessor(CPU cpu) {
		this.cpu = cpu;
	}

	protected void setHighRegSafe(int reg, int val) {
		if ((reg & 0x7) == 0x7)
			cpu.branch(val & 0xFFFFFFFE);
		else
			cpu.setHighReg(reg, val);
	}

	@Override
	public void execute(int pc) {
		//TODO Get instruction
		int instr = 0; //Actually a short (only use lower 16 bits)

		byte bit15_to_11 = (byte)(instr >>> 11);

		//From 0x0 to 0x1F (0-31)
		switch(bit15_to_11) {
		case 0x0: lslImm(instr); break; 
		case 0x1: lsrImm(instr); break;
		case 0x2: asrImm(instr); break;
		case 0x3: /*Add or Sub*/
			if ((instr & 0x200) == 0) /*Bit 9 CLEAR*/
				if ((instr & 0x400) == 0) /*Bit 10 CLEAR*/
					addReg(instr);
				else
					addImm3(instr);
			else
				if ((instr & 0x400) == 0) /*Bit 10 CLEAR*/
					subReg(instr);
				else
					subImm3(instr);
			break;
		case 0x4: movImm8(instr); break;
		case 0x5: cmpImm8(instr); break;
		case 0x6: addImm8(instr); break;
		case 0x7: subImm8(instr); break;
		case 0x8:
			if ((instr & 0x400) == 0) /*Bit 10 CLEAR*/
				aluOp(instr); 
			else
				hiRegOpsBranchX(instr);
			break;
		case 0x9: pcRelativeLoad(instr); break;
		case 0xA: /*Bit 11 CLEAR*/
			if ((instr & 0x200) == 0)/*Bit 9 CLEAR, Store register offset*/
				if ((instr & 0x400) == 0) /*Bit 10 CLEAR - B*/
					str(instr);
				else
					strb(instr);
			else
				if ((instr & 0x400) == 0) /*Bit 10 CLEAR - S*/
					strh(instr);
				else
					ldsb(instr);
			break;
		case 0xB: /*Bit 11 SET*/
			if ((instr & 0x200) == 0)/*Bit 9 CLEAR, Load register offset*/
				if ((instr & 0x400) == 0) /*Bit 10 CLEAR*/
					ldr(instr);
				else 
					ldrb(instr);
			else
				if ((instr & 0x400) == 0) /*Bit 10 CLEAR - S*/
					ldrh(instr);
				else
					ldsh(instr);
			break;
		case 0xC: strImm(instr); break;
		case 0xD: ldrImm(instr); break;
		case 0xE: strbImm(instr); break;
		case 0xF: ldrbImm(instr); break;
		case 0x10: strhImm(instr); break;
		case 0x11: ldrhImm(instr); break;
		case 0x12: spRelativeStore(instr); break;
		case 0x13: spRelativeLoad(instr); break;
		case 0x14: addPC(instr); break;
		case 0x15: addSP(instr); break;
		case 0x16:
			if ((instr & 0x700) == 0) /*Bit 10-8 CLEAR*/
				addOffsetToSP(instr); 
			else if ((instr & 0x600) == 0x400) /*Bit 10 SET, Bit 9 CLEAR*/
				pushRegisters(instr); 
			else
				cpu.undefinedInstr("Illegal variation of offset stack pointer/push register");
			break;
		case 0x17:
			if ((instr & 0x600) == 0x400) /*Bit 10 SET, Bit 9 CLEAR*/
				popRegisters(instr); 
			else 
				cpu.undefinedInstr("Illegal variation of pop registers");
			break;
		case 0x18: storeMult(instr); break;
		case 0x19: loadMult(instr); break;
		case 0x1A: conditionalBranch(instr); break;
		case 0x1B:
			if ((instr & 0xF00) == 0xF00)
				softwareInterrupt(instr);
			else
				conditionalBranch(instr);
			break;
		case 0x1C: unconditionalBranch(instr); break;
		case 0x1D: cpu.undefinedInstr("THUMB 0x1D... is undefined"); break; 
		case 0x1E: longBranch(instr); break;
		case 0x1F: branchWithLink(instr); break;
		}
	}

	private void lslImm(int instr) {
		int offset5 = (instr >>> 6) & 0x1F; //Bit 10-6
		int val = cpu.getLowReg(instr >>> 3);
		if (offset5 != 0) { //Carry not affected by 0
			//Carry set by the last bit shifted out (=sign of the value shifted one less)
			cpu.cpsr.carry = ((val << (offset5-1)) < 0);
			val <<= offset5;
		}
		cpu.cpsr.negative = (val < 0);
		cpu.cpsr.zero = (val == 0);
		cpu.setLowReg(instr, val); //The method will & 0x7 for us
	}

	private void lsrImm(int instr){
		int offset5 = (instr >>> 6) & 0x1F; //Bit 10-6
		int val = cpu.getLowReg(instr >>> 3);
		if (offset5 != 0) {
			//Carry set by the last bit shifted out (= 0 bit of the value shifted one less)
			cpu.cpsr.carry = (((val >>> (offset5 - 1)) & 0x1) == 0x1);
			val >>>= offset5;
		}
		else {
			//This is actually LSR #32 (page 13 of ARM pdf), thus carry = sign bit, value becomes 0
			cpu.cpsr.carry = (val < 0);
			val = 0;
		}
		cpu.cpsr.negative = (val < 0);
		cpu.cpsr.zero = (val == 0);
		cpu.setLowReg(instr, val); //The method will & 0x7 for us
	}

	private void asrImm(int instr) {
		int offset5 = (instr >>> 6) & 0x1F; //Bit 10-6
		int val = cpu.getLowReg(instr >>> 3);
		if (offset5 != 0) {
			//Carry set by the last bit shifted out (= 0 bit of the value shifted one less)
			cpu.cpsr.carry = (((val >> (offset5 - 1)) & 0x1) == 0x1);
			val >>= offset5;
		}
		else {
			//This is actually ASR #32 (page 13 of ARM pdf), thus carry = sign bit, value becomes either all 0's or all 1's
			cpu.cpsr.carry = (val < 0);
			val >>= 31;
		}
		cpu.cpsr.negative = (val < 0);
		cpu.cpsr.zero = (val == 0);
		cpu.setLowReg(instr, val); //The method will & 0x7 for us
	}

	private void addReg(int instr) {
		//The methods will & 0x7 for us
		int arg = cpu.getLowReg(instr >>> 6);
		int source = cpu.getLowReg(instr >>> 3);
		cpu.setLowReg(instr, cpu.cpsr.setAddFlags(source, arg));
	}

	private void addImm3(int instr) {
		int arg = (instr >>> 6) & 0x7;
		//The methods will & 0x7 for us
		int source = cpu.getLowReg(instr >>> 3);
		cpu.setLowReg(instr, cpu.cpsr.setAddFlags(source, arg));
	}

	private void subReg(int instr) {
		//The methods will & 0x7 for us
		int arg = cpu.getLowReg(instr >>> 6);
		int source = cpu.getLowReg(instr >>> 3);
		cpu.setLowReg(instr, cpu.cpsr.setSubFlags(source, arg));
	}

	private void subImm3(int instr) {
		int arg = (instr >>> 6) & 0x7;
		//The methods will & 0x7 for us
		int source = cpu.getLowReg(instr >>> 3);
		cpu.setLowReg(instr, cpu.cpsr.setSubFlags(source, arg));
	}

	private void movImm8(int instr) {
		int val = instr & 0xFF;
		cpu.cpsr.negative = false;
		cpu.cpsr.zero = (val == 0);
		//The method will & 0x7 for us
		cpu.setLowReg(instr >>> 8, val);
	}

	private void cmpImm8(int instr) {
		//The method will & 0x7 for us
		cpu.cpsr.setSubFlags(cpu.getLowReg(instr >>> 8), instr & 0xFF);
	}

	private void addImm8(int instr) {
		//The methods will & 0x7 for us
		int val = cpu.getLowReg(instr >>> 8);
		cpu.setLowReg(instr >>> 8, cpu.cpsr.setAddFlags(val, instr & 0xFF));
	}

	private void subImm8(int instr) {
		//The methods will & 0x7 for us
		int val = cpu.getLowReg(instr >>> 8);
		cpu.setLowReg(instr >>> 8, cpu.cpsr.setSubFlags(val, instr & 0xFF));
	}

	private void aluOp(int instr) {
		byte op = (byte) ((instr >>> 6) & 0xF);
		//rs = instr >>> 3, rd = instr (& 0x7)
		switch(op) {
		case AND: and(instr, instr >>> 3); break;
		case EOR: eor(instr, instr >>> 3); break;
		case LSL: lsl(instr, instr >>> 3); break;
		case LSR: lsr(instr, instr >>> 3); break;
		case ASR: asr(instr, instr >>> 3); break;
		case ADC: adc(instr, instr >>> 3); break;
		case SBC: sbc(instr, instr >>> 3); break;
		case ROR: ror(instr, instr >>> 3); break;
		case TST: tst(instr, instr >>> 3); break;
		case NEG: neg(instr, instr >>> 3); break;
		case CMP: cmp(instr, instr >>> 3); break;
		case CMN: cmn(instr, instr >>> 3); break;
		case ORR: orr(instr, instr >>> 3); break;
		case MUL: mul(instr, instr >>> 3); break;
		case BIC: bic(instr, instr >>> 3); break;
		case MVN: mvn(instr, instr >>> 3); break;
		}
	}

	/**
	 * AND Rd, Rs (Rd = Rd & Rs)
	 */
	private void and(int rd, int rs) {
		int val = cpu.getLowReg(rd) & cpu.getLowReg(rs);
		cpu.cpsr.negative = (val < 0);
		cpu.cpsr.zero = (val == 0);
		cpu.setLowReg(rd, val);
	}

	/**
	 * EOR Rd, Rs (Rd = Rd ^ Rs)
	 */
	private void eor(int rd, int rs) {
		int val = cpu.getLowReg(rd) ^ cpu.getLowReg(rs);
		cpu.cpsr.negative = (val < 0);
		cpu.cpsr.zero = (val == 0);
		cpu.setLowReg(rd, val);
	}

	/**
	 * LSL Rd, Rs (Rd = Rd << Rs)
	 */
	private void lsl(int rd, int rs) {
		int val = cpu.getLowReg(rd);
		int shift = cpu.getLowReg(rs) & 0xFF; //Only the least significant byte is used to determine the shift
		if (shift > 0) { //Carry not affected by 0 shift
			if (shift < 32) { //Shifts <32 are fine, carry is the last bit shifted out
				cpu.cpsr.carry = (val << (shift-1) < 0);
				val <<= shift;
			}
			else if (shift == 32) { //We do this manually b/c in Java, shifts are % #bits, carry is the 0 bit
				cpu.cpsr.carry = ((val & 0x1) == 0x1); 
				val = 0;
			}
			else { //Shift >32, 0's!
				cpu.cpsr.carry = false;
				val = 0;
			}
		}
		cpu.cpsr.negative = (val < 0);
		cpu.cpsr.zero = (val == 0);
		cpu.setLowReg(rd, val);
	}

	/**
	 * LSR Rd, Rs (Rd = Rd >>> Rs)
	 * LSR = Logical Shift Right
	 */
	private void lsr(int rd, int rs) {
		int val = cpu.getLowReg(rd);
		int shift = cpu.getLowReg(rs) & 0xFF; //Only the least significant byte is used to determine the shift
		if (shift > 0) {
			if (shift < 32) { //Shifts <32 are fine, carry is the last bit shifted out
				cpu.cpsr.carry = (((val >>> (shift - 1)) & 0x1) == 0x1);
				val >>>= shift;
			}
			else if (shift == 32) { //We do this manually b/c in Java, shifts are % #bits, carry is sign bit
				cpu.cpsr.carry = (val < 0); 
				val = 0;
			}
			else { //Shift >32, 0's!
				cpu.cpsr.carry = false;
				val = 0;
			}
		}
		cpu.cpsr.negative = (val < 0);
		cpu.cpsr.zero = (val == 0);
		cpu.setLowReg(rd, val);
	}

	/**
	 * ASR Rd, Rs (Rd = Rd >> Rs)
	 * ASR = Arithmetic (signed) Shift Right
	 */
	private void asr(int rd, int rs) {
		int val = cpu.getLowReg(rd);
		int shift = cpu.getLowReg(rs) & 0xFF; //Only the least significant byte is used to determine the shift
		if (shift > 0) {
			if (shift < 32) { //Shifts <32 are fine, carry is the last bit shifted out
				cpu.cpsr.carry = (((val >> (shift - 1)) & 0x1) == 0x1);
				val >>= shift;
			}
			else { //Shift >=32, carry is equal to the sign bit, value becomes either all 1's or 0's
				cpu.cpsr.carry = (val < 0);
				val >>= 31;
			}
		}
		cpu.cpsr.negative = (val < 0);
		cpu.cpsr.zero = (val == 0);
		cpu.setLowReg(rd, val);
	}

	/**
	 * ADC Rd, Rs (Rd = Rd + Rs + C-bit)
	 */
	private void adc(int rd, int rs) {
		cpu.setLowReg(rd, cpu.cpsr.setAddCarryFlags(cpu.getLowReg(rd), cpu.getLowReg(rs)));
	}

	/**
	 * SBC Rd, Rs (Rd = Rd - Rs - NOT C-bit)
	 */
	private void sbc(int rd, int rs) {
		cpu.setLowReg(rd, cpu.cpsr.setSubCarryFlags(cpu.getLowReg(rd), cpu.getLowReg(rs)));
	}

	/**
	 * ROR Rd, Rs (Rd = Rd ROR Rs)
	 * ROR = ROtate Right
	 */
	private void ror(int rd, int rs) {
		int val = cpu.getLowReg(rd);
		int rotate = cpu.getLowReg(rs) & 0xFF; //Only the least significant byte is used to determine the rotate
		if (rotate > 0) {
			rotate = rotate & 0x1F; //If rotate >32, we subtract 32 until in range [0-31] -> same as & 0x1F (31)
			if (rotate > 0) { //Carry is the last bit rotated out
				cpu.cpsr.carry = (((val >>> (rotate - 1)) & 0x1) == 0x1);
				//Val is the remaining bits from the shift and the removed bits shifted to the left
				val = (val >>> rotate) | (val << (32-rotate));
			}
			else //ROR 32, carry equal to sign bit
				cpu.cpsr.carry = (val < 0);
		}
		cpu.cpsr.negative = (val < 0);
		cpu.cpsr.zero = (val == 0);
		cpu.setLowReg(rd, val);
	}

	/**
	 * TST Rd, Rs (Set condition codes on Rd & Rs)
	 */
	private void tst(int rd, int rs) {
		int val = cpu.getLowReg(rd) & cpu.getLowReg(rs);
		cpu.cpsr.negative = (val < 0);
		cpu.cpsr.zero = (val == 0);
	}

	/**
	 * NEG Rd, Rs (Rd = -Rs)
	 */
	private void neg(int rd, int rs) {
		int val = cpu.getLowReg(rs);
		//Because of the two's complement system, 0 will overflow back to 0
		//and Integer.MIN_VALUE will still be Integer.MIN_VALUE, which is marked as an overflow condition.
		//Therefore, this is identical to cpu.cpsr.overflow = (val == -val);
		cpu.cpsr.overflow = ((val ^ -val) == 0);
		val = -val;
		cpu.cpsr.negative = (val < 0);
		cpu.cpsr.zero = (val == 0);
		cpu.setLowReg(rd, val);
	}

	/**
	 * CMP Rd, Rs (Set condition codes on Rd - Rs)
	 */
	private void cmp(int rd, int rs) {
		cpu.cpsr.setSubFlags(cpu.getLowReg(rd), cpu.getLowReg(rs));
	}

	/**
	 * CMN Rd, Rs (Set condition codes on Rd + Rs)
	 */
	private void cmn(int rd, int rs) {
		cpu.cpsr.setAddFlags(cpu.getLowReg(rd), cpu.getLowReg(rs));
	}

	/**
	 * ORR Rd, Rs (Rd = Rd | Rs)
	 */
	private void orr(int rd, int rs) {
		int val = cpu.getLowReg(rd) | cpu.getLowReg(rs);
		cpu.cpsr.negative = (val < 0);
		cpu.cpsr.zero = (val == 0);
		cpu.setLowReg(rd, val);
	}

	/**
	 * MUL Rd, Rs (Rd = Rd * Rs)
	 */
	private void mul(int rd, int rs) {
		int val = cpu.getLowReg(rd) * cpu.getLowReg(rs);
		cpu.cpsr.carry = false;
		cpu.cpsr.negative = (val < 0);
		cpu.cpsr.zero = (val == 0);
		cpu.setLowReg(rd, val);
	}

	/**
	 * BIC Rd, Rs (Rd = Rd AND NOT Rs)
	 */
	private void bic(int rd, int rs) {
		int val = cpu.getLowReg(rd) & ~cpu.getLowReg(rs);
		cpu.cpsr.negative = (val < 0);
		cpu.cpsr.zero = (val == 0);
		cpu.setLowReg(rd, val);
	}

	/**
	 * MVN Rd, Rs (Rd = NOT Rs)
	 */
	private void mvn(int rd, int rs) {
		int val = ~cpu.getLowReg(rs);
		cpu.cpsr.negative = (val < 0);
		cpu.cpsr.zero = (val == 0);
		cpu.setLowReg(rd, val);
	}

	private void hiRegOpsBranchX(int instr) {
		byte op = (byte) ((instr >>> 8) & 0x3); //Bit 9,8
		//rs = instr >>> 3, rd = instr (& 0x7)
		boolean high1 = (instr & 0x80) == 0x80; //Bit 7
		boolean high2 = (instr & 0x40) == 0x40; //Bit 6
		switch(op) { 
		case 0x0:
			if (high1 && high2)
				addHH(instr, instr >>> 3);
			else if (high1)
				addHL(instr, instr >>> 3);
			else if (high2)
				addLH(instr, instr >>> 3);
			else
				cpu.undefinedInstr("Add low-low is undefined");
			break;
		case 0x1:
			if (high1 && high2)
				cmpHH(instr, instr >>> 3);
			else if (high1)
				cmpHL(instr, instr >>> 3);
			else if (high2)
				cmpLH(instr, instr >>> 3);
			else
				cpu.undefinedInstr("Cmp low-low is undefined");
			break;
		case 0x2:
			if (high1 && high2)
				movHH(instr, instr >>> 3);
			else if (high1)
				movHL(instr, instr >>> 3);
			else if (high2)
				movLH(instr, instr >>> 3);
			else
				cpu.undefinedInstr("Mov low-low is undefined");
			break;
		case 0x3:
			if (high1)
				cpu.undefinedInstr("BranchX high-low, high-high is undefined");
			else if (high2)
				branchXLow(instr >>> 3);
			else
				branchXHigh(instr >>> 3);
			break;
		}
	}

	private void addHH(int hd, int hs) {
		setHighRegSafe(hd, cpu.cpsr.setAddFlags(cpu.getHighReg(hd), cpu.getHighReg(hs)));
	}

	private void addHL(int hd, int rs) {
		setHighRegSafe(hd, cpu.cpsr.setAddFlags(cpu.getHighReg(hd), cpu.getLowReg(rs)));
	}

	private void addLH(int rd, int hs) {
		cpu.setLowReg(rd, cpu.cpsr.setAddFlags(cpu.getLowReg(rd), cpu.getHighReg(hs)));
	}

	private void cmpHH(int hd, int hs) {
		cpu.cpsr.setSubFlags(cpu.getHighReg(hd), cpu.getHighReg(hs));
	}

	private void cmpHL(int hd, int rs) {
		cpu.cpsr.setSubFlags(cpu.getHighReg(hd), cpu.getLowReg(rs));
	}

	private void cmpLH(int rd, int hs) {
		cpu.cpsr.setSubFlags(cpu.getLowReg(rd), cpu.getHighReg(hs));
	}

	private void movHH(int hd, int hs) {
		setHighRegSafe(hd, cpu.getHighReg(hs));
	}

	private void movHL(int hd, int rs) {
		setHighRegSafe(hd, cpu.getLowReg(rs));
	}

	private void movLH(int rd, int hs) {
		cpu.setLowReg(rd, cpu.getHighReg(hs));
	}

	private void branchXLow(int rs) {
		int address = cpu.getLowReg(rs);
		if ((address & 0x1) == 0) { //Swap modes
			cpu.cpsr.thumb = false;
			cpu.branch(address & 0xFFFFFFFC); //Word aligned
		}
		else
			cpu.branch(address & 0xFFFFFFFE); //Halfword aligned
	}

	private void branchXHigh(int hs) {
		int address = cpu.getHighReg(hs);
		if ((address & 0x1) == 0) { //Swap modes
			cpu.cpsr.thumb = false;
			cpu.branch(address & 0xFFFFFFFC); //Word aligned
		}
		else 
			cpu.branch(address & 0xFFFFFFFE); //Halfword aligned
	}

	private void pcRelativeLoad(int instr) {
		//This loads a word from an address specified as a 	10-bit immediate offset from the PC.
		//Bit 1 of the PC is forced to 0 to ensure it is word aligned.
		cpu.setLowReg(instr >>> 8, cpu.read32((cpu.getPC() & 0xFFFFFFFD) + ((instr & 0xFF) << 2)));
	}

	private void str(int instr) {
		//Pre-indexed word store, get address from reg (bit 5-3) and reg (bit 8-6)
		int address = cpu.getLowReg(instr >>> 3) + cpu.getLowReg(instr >>> 6);
		//Store the value in the reg (instr & 0x7) at [address]
		cpu.write32(address, cpu.getLowReg(instr));
	}

	private void strb(int instr) {
		//Pre-indexed byte store, get address from reg (bit 5-3) and reg (bit 8-6)
		int address = cpu.getLowReg(instr >>> 3) + cpu.getLowReg(instr >>> 6);
		//Store the low byte in the reg (instr & 0x7) at [address]
		cpu.write8(address, cpu.getLowReg(instr));
	}
	
	private void strh(int instr) {
		//Halfword store, get address from reg (bit 5-3) and reg (bit 8-6)
		int address = cpu.getLowReg(instr >>> 3) + cpu.getLowReg(instr >>> 6);
		//Store the halfword in the reg (instr & 0x7) at [address]
		cpu.write16(address, cpu.getLowReg(instr));
	}

	private void ldr(int instr) {
		//Pre-indexed word load, get address from reg (bit 5-3) and reg (bit 8-6)
		int address = cpu.getLowReg(instr >>> 3) + cpu.getLowReg(instr >>> 6);
		//Load the value at [address] into the reg (instr & 0x7)
		cpu.setLowReg(instr, cpu.read32(address));
	}

	private void ldrb(int instr) {
		//Pre-indexed byte load, get address from reg (bit 5-3) and reg (bit 8-6)
		int address = cpu.getLowReg(instr >>> 3) + cpu.getLowReg(instr >>> 6);
		//Load the byte at [address] into the reg (instr & 0x7)
		cpu.setLowReg(instr, cpu.read8(address));
	}

	private void ldsb(int instr) {
		//Sign extended byte load, get address from reg (bit 5-3) and reg (bit 8-6)
		int address = cpu.getLowReg(instr >>> 3) + cpu.getLowReg(instr >>> 6);
		//Load the byte at [address] into the reg (instr & 0x7)
		//We sign extend by shifting it left 24 then ASR-24 
		cpu.setLowReg(instr, (cpu.read8(address) << 24) >> 24);
	}

	private void ldrh(int instr) {
		//Halfword load, get address from reg (bit 5-3) and reg (bit 8-6)
		int address = cpu.getLowReg(instr >>> 3) + cpu.getLowReg(instr >>> 6);
		//Load the halfword at [address] into the reg (instr & 0x7)
		cpu.setLowReg(instr, cpu.read16(address));
	}	

	private void ldsh(int instr) {
		//Sign extended Halfword load, get address from reg (bit 5-3) and reg (bit 8-6)
		int address = cpu.getLowReg(instr >>> 3) + cpu.getLowReg(instr >>> 6);
		//Load the halfword at [address] into the reg (instr & 0x7)
		//We sign extend by shifting it left 16 then ASR-16
		cpu.setLowReg(instr, (cpu.read16(address) << 16) >> 16);
	}

	private void strImm(int instr) {
		//Address is the sum of the immediate5 (which is actually immediate7) and value in Rb
		//Offset7 = (Bit 10-6) << 2, Rb = bit 5-3
		int address = (((instr >>> 6) & 0x1F) << 2) + cpu.getLowReg(instr >>> 3);
		//Store the value in the reg (instr & 0x7) at [address]
		cpu.write32(address, cpu.getLowReg(instr));
	}

	private void strbImm(int instr) {
		//Address is the sum of the immediate5 and value in Rb
		//Offset5 = Bit 10-6, Rb = bit 5-3
		int address = ((instr >>> 6) & 0x1F) + cpu.getLowReg(instr >>> 3);
		//Store the low byte in the reg (instr & 0x7) at [address]
		cpu.write8(address, cpu.getLowReg(instr));
	}

	private void ldrImm(int instr) {
		//Address is the sum of the immediate5 (which is actually immediate7) and value in Rb
		//Offset7 = (Bit 10-6) << 2, Rb = bit 5-3
		int address = (((instr >>> 6) & 0x1F) << 2) + cpu.getLowReg(instr >>> 3);
		//Load the value at [address] into the reg (instr & 0x7)
		cpu.setLowReg(instr, cpu.read32(address));
	}

	private void ldrbImm(int instr) {
		//Address is the sum of the immediate5 and value in Rb
		//Offset5 = Bit 10-6, Rb = bit 5-3
		int address = ((instr >>> 6) & 0x1F) + cpu.getLowReg(instr >>> 3);
		//Load the byte at [address] into the reg (instr & 0x7)
		cpu.setLowReg(instr, cpu.read8(address));
	}

	private void strhImm(int instr) {
		//Address is the sum of the immediate5 (which is actually immediate6) and value in Rb
		//Offset6 = (Bit 10-6) << 1, Rb = bit 5-3
		int address = (((instr >>> 6) & 0x1F) << 1) + cpu.getLowReg(instr >>> 3);
		//Store the halfword in the reg (instr & 0x7) at [address]
		cpu.write16(address, cpu.getLowReg(instr));
	}

	private void ldrhImm(int instr) {
		//Address is the sum of the immediate5 (which is actually immediate6) and value in Rb
		//Offset6 = (Bit 10-6) << 1, Rb = bit 5-3
		int address = (((instr >>> 6) & 0x1F) << 1) + cpu.getLowReg(instr >>> 3);
		//Load the halfword at [address] into the reg (instr & 0x7)
		cpu.setLowReg(instr, cpu.read16(address));
	}

	private void spRelativeStore(int instr) {
		//Offset is actually an unsigned 10 bit value
		int address = cpu.getSP() + ((instr & 0xFF) << 2);
		cpu.write32(address, cpu.getLowReg(instr >>> 8));
	}

	private void spRelativeLoad(int instr) {
		//Offset is actually an unsigned 10 bit value
		int address = cpu.getSP() + ((instr & 0xFF) << 2);
		cpu.setLowReg(instr >>> 8, cpu.read32(address));
	}

	private void addPC(int instr) {
		//Bit 1 of the PC is forced to 0 to ensure it is word aligned, Offset is actually an unsigned 10 bit value
		cpu.setLowReg(instr >>> 8, (cpu.getPC() & 0xFFFFFFFD) + ((instr & 0xFF) << 2));
	}

	private void addSP(int instr) {
		//Offset is actually an unsigned 10 bit value
		cpu.setLowReg(instr >>> 8, cpu.getSP() + ((instr & 0xFF) << 2));
	}

	private void addOffsetToSP(int instr) { 
		//Bit 7 is sign bit, bit 6-0 is shifted by 2 (9 bit constant)
		if ((instr & 0x80) == 0) //Pos/Add
			cpu.setSP(cpu.getSP() + ((instr & 0x7F) << 2));
		else
			cpu.setSP(cpu.getSP() - ((instr & 0x7F) << 2));
	}

	private void pushRegisters(int instr) {
		//PRE DREC 
		int sp = cpu.getSP();
		if ((instr & 0x100) == 0x100) { //Bit 8 set - store LR
			sp -= 4;
			cpu.write32(sp, cpu.getLR());
		}
		for (byte reg = 7; reg >= 0; --reg) { //Check all 8 bits
			if ((instr & (1 << reg)) != 0)	{
				sp -= 4;
				cpu.write32(sp, cpu.getLowReg(reg));
			}
		}
		cpu.setSP(sp);
	}

	private void popRegisters(int instr) {
		//POST INCR
		int sp = cpu.getSP();
		for (byte reg = 0; reg <= 7; ++reg) {
			if ((instr & (1 << reg)) != 0) {
				cpu.setLowReg(reg, cpu.read32(sp));
				sp += 4;
			}
		}
		if ((instr & 0x100) == 0x100) { //bit 8 set - load PC
			cpu.branch(cpu.read32(sp) & 0xFFFFFFFE);
			sp += 4;
		}
		cpu.setSP(sp);
	}

	private void storeMult(int instr) {
		//POST INCR
		int address = cpu.getLowReg(instr >>> 8);
		for (byte reg = 0; reg <= 7; ++reg) {
			if ((instr & (1 << reg)) != 0) {
				cpu.write32(address, cpu.getLowReg(reg));
				address += 4;
			}
		}
		cpu.setLowReg(instr >>> 8, address);
	}

	private void loadMult(int instr) {
		//POST INCR
		int address = cpu.getLowReg(instr >>> 8);
		for (byte reg = 0; reg <= 7; ++reg) {
			if ((instr & (1 << reg)) != 0) {
				cpu.setLowReg(reg, cpu.read32(address));
				address += 4;
			}
		}
		cpu.setLowReg(instr >>> 8, address);
	}

	private void conditionalBranch(int instr) {
		byte cond = (byte) ((instr >>> 8) & 0xF);
		if (cond == 14)
			cpu.undefinedInstr("Branch conditional-14 is undefined");
		//8 bit offset is actually 9 bits
		else if (Condition.condition(cond, cpu.cpsr))
			cpu.branch(cpu.getPC() + ((instr & 0xFF) << 1));
	}

	private void softwareInterrupt(int instr) {
		cpu.softwareInterrupt((byte)instr);
	}

	private void unconditionalBranch(int instr) {
		//11 bits are actually 12, halfword aligned
		int offset = (instr & 0x7FF) << 1;
		cpu.branch(cpu.getPC() + offset);
	}

	private void longBranch(int instr) {
		//Bit 11 is clear - offset high - LR = PC + (Offset11 << 12)
		cpu.setLR(cpu.getPC() + ((instr & 0x7FF) << 12));
	}

	private void branchWithLink(int instr) {
		//Bit 11 is set - offset low - PC = LR + (Offset11 << 1)
		//Also store next instruction in LR
		int nextInstr = cpu.getPC() - 2;
		//We shouldn't need to halfword align this, assuming that this is called
		//after a long branch call, but we'll be safe and do it anyway
		cpu.branch((cpu.getLR() + ((instr & 0x7FF) << 1)) & 0xFFFFFFFE);
		//Update LR with the address of the next instruction, set bit 0 
		cpu.setLR(nextInstr | 0x1);
	}

}
