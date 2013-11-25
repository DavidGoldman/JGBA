package cpu;

public class ARMProcessor implements CPU.IProcessor {
	
	private final CPU cpu;

	public ARMProcessor(CPU cpu) {
		this.cpu = cpu;
	}

	/**
	 * Given the pc, accesses the cartridge ROM and retrieves the current operation bytes.
	 * If the evaluated condition is true, an operation will be decoded and executed.
	 * 
	 * TODO Optimize (switch)
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

			if (bit27_to_24 == 1 && midTop == (byte)0x2F && midBot == (byte)0xFF && (bot & 0xF0) == 0x10) /*0x12FFF1, Rn*/
				branchAndExchange((byte) (bot & 0xF));
			else if ((bit27_to_24 & 0xC) == 0 && ((bit27_to_24 & 0x2) == 0x2 || (bot & 0x10) == 0 || (bot & 0x80) == 0)) /*Bit 27,26 are CLEAR AND (Bit 25 SET OR Bit 4 CLEAR OR Bit 7 CLEAR) */ 
				dataProcPSR(top, midTop, midBot, bot);																	 /*OK since we check for Branch Exchange first */																								
			else if (bit27_to_24 == 0 || bit27_to_24 == 1) { /*Bit 24 = ?, Bit 7 and 4 WILL be SET because of statement above*/ 
				if ((bot & 0x60) == 0) { /*Bit 6,5 are CLEAR*/
					if (bit27_to_24 == 0 && (bit23_to_20 & 0xC) == 0) /*Bit 27-22 are all CLEAR*/
						multiply(midTop, midBot, bot);
					if (bit27_to_24 == 0 && (bit23_to_20 & 0xF) == 0x8) /*Bit 27-24 are all CLEAR, bit 23 is SET*/
						multiplyLong(midTop, midBot, bot);
					if (bit27_to_24 == 1 && (bit23_to_20 & 0xB) == 0 && (midBot & 0xF) == 0) /*Bit 27-25 CLEAR, Bit 24 SET, BIT 23,21,20 CLEAR, Bit 11-8 CLEAR*/
						singleDataSwap(midTop, midBot, bot);
				}
				else { /*Bit 6,5 are NOT both CLEAR, implies Halfword DT*/
					if ((bit23_to_20 & 0x4) == 0x4) /*Bit 22 is SET*/
						halfwordDTImmediate(bit27_to_24 == 1, midTop, midBot, bot);
					else if ((midBot & 0xF) == 0) /* Bit 22 is CLEAR AND Bit 11-8 CLEAR*/
						halfwordDTRegister(bit27_to_24 == 1, midTop, midBot, bot);
				}
			}
			else if ((bit27_to_24 & 0xC) == 0x4) {/*Bit 27 is CLEAR, Bit 26 is SET*/
				if ((bit27_to_24 & 0x2) == 0 || (bot & 0x10) == 0) /*Bit 25 is CLEAR OR Bit 4 is CLEAR*/
					singleDataTransfer(top, midTop, midBot, bot);
				else /*Bit 27 CLEAR, Bit 26,25 SET, Bit 4 SET*/
					undefinedTrap();
			}
			else if (bit27_to_24 == 8 || bit27_to_24 == 9) /*Bit 27 SET, Bit 26,25 CLEAR*/
				blockDataTransfer(bit27_to_24 == 9, midTop, midBot, bot);
			else if (bit27_to_24 == 10 || bit27_to_24 == 11) /*Bit 27 SET, Bit 26 CLEAR, Bit 24 SET*/
				branch(bit27_to_24 == 11, midTop, midBot, bot);
			else if (bit27_to_24 == 12 || bit27_to_24 == 13) /*Bit 27,26 SET, Bit 25 CLEAR*/
				coprocDataTransfer(bit27_to_24 == 13, midTop, midBot, bot);
			else if (bit27_to_24 == 14) { /*Bit 27-25 SET, Bit 24 CLEAR*/
				if ((bot & 0x10) == 0) /*Bit 4 CLEAR*/
					coprocDataOperation(midTop, midBot, bot);
				else /*Bit 4 SET*/
					coprocRegisterTransfer(midTop, midBot, bot);
			}
			else /*if (bit27_to_24 == 15)*/ /*Bit 27-24 are all SET*/							
				softwareInterrupt(pc, midTop, midBot, bot);
		}
	}

	private void branchAndExchange(byte Rn) {
		
	}

	private void dataProcPSR(byte top, byte midTop, byte midBot, byte bot) {

	}

	private void multiply(byte midTop, byte midBot, byte bot) {

	}

	private void multiplyLong(byte midTop, byte midBot, byte bot) {

	}

	private void singleDataSwap(byte midTop, byte midBot, byte bot) {

	}

	private void halfwordDTImmediate(boolean p, byte midTop, byte midBot, byte bot) {

	}

	private void halfwordDTRegister(boolean p, byte midTop, byte midBot, byte bot) {

	}

	private void singleDataTransfer(byte top, byte midTop, byte midBot, byte bot) {

	}

	private void undefinedTrap() {

	}
	private void blockDataTransfer(boolean p, byte midTop, byte midBot, byte bot) {

	}

	private void branch(boolean l, byte midTop, byte midBot, byte bot) {
	
	}

	private void coprocDataTransfer(boolean p, byte midTop, byte midBot, byte bot) {

	}

	private void coprocDataOperation(byte midTop, byte midBot, byte bot) {

	}

	private void coprocRegisterTransfer(byte midTop, byte midBot, byte bot) {

	}

	private void softwareInterrupt(int pc, byte midTop, byte midBot, byte bot) {

	}

}
