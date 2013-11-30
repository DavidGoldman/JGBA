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
					dataProcPSR(top, midTop, midBot, bot);
				else if ((bot & 0x60) == 0) { //Bit 6,5 are CLEAR
					if ((bit23_to_20 & 0xC) == 0)
						multiply(midTop, midBot, bot);
					else if ((bit23_to_20 & 0x8) == 0x8)
						multiplyLong(midTop, midBot, bot);
					else
						; //TODO Undefined
				}
				else { //Bit 6,5 are NOT both CLEAR, implies Halfword DT
					if ((bit23_to_20 & 0x4) == 0x4) //Bit 22 is SET
						halfwordDTImmediate(false, midTop, midBot, bot);
					else if ((midBot & 0xF) == 0) //Bit 22 is CLEAR AND Bit 11-8 CLEAR
						halfwordDTRegister(false, midTop, midBot, bot);
					else
						; //TODO Undefined
				}
				break;
			case 0x1:
				if (midTop == (byte)0x2F && midBot == (byte)0xFF && (bot & 0xF0) == 0x10) //0x12FFF1, Rn
					branchAndExchange((byte) (bot & 0xF));
				else if ((bot & 0x10) == 0 || (bot & 0x80) == 0) //Bit 4 or bit 7 clear
					dataProcPSR(top, midTop, midBot, bot); 
				else if ((bot & 0x60) == 0) { //Bit 6,5 are CLEAR
					if ((bit23_to_20 & 0xB) == 0 && (midBot & 0xF) == 0) //Bit 27-25 CLEAR, Bit 24 SET, BIT 23,21,20 CLEAR, Bit 11-8 CLEAR
						singleDataSwap(midTop, midBot, bot);
					else
						; //TODO Undefined
				}
				else { //Bit 6,5 are NOT both CLEAR, implies Halfword DT
					if ((bit23_to_20 & 0x4) == 0x4) //Bit 22 is SET
						halfwordDTImmediate(true, midTop, midBot, bot);
					else if ((midBot & 0xF) == 0) //Bit 22 is CLEAR AND Bit 11-8 CLEAR
						halfwordDTRegister(true, midTop, midBot, bot);
					else
						; //TODO Undefined
				}
				break;
			case 0x2: dataProcPSR(top, midTop, midBot, bot); break;
			case 0x3: dataProcPSR(top, midTop, midBot, bot); break;
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

	private void singleDataTransferImmPre(byte midTop, byte midBot, byte bot) {

	}
	
	private void singleDataTransferImmPost(byte midTop, byte midBot, byte bot) {

	}
	
	private void singleDataTransferRegPre(byte midTop, byte midBot, byte bot) {

	}
	
	private void singleDataTransferRegPost(byte midTop, byte midBot, byte bot) {

	}

	private void undefinedTrap() {

	}
	
	private void blockDataTransferPre(byte midTop, byte midBot, byte bot) {

	}
	
	private void blockDataTransferPost(byte midTop, byte midBot, byte bot) {

	}
	
	private void branchLink(byte midTop, byte midBot, byte bot) {
		
	}

	private void branch(byte midTop, byte midBot, byte bot) {

	}

	private void coprocDataTransferPre(byte midTop, byte midBot, byte bot) {

	}
	
	private void coprocDataTransferPost(byte midTop, byte midBot, byte bot) {

	}

	private void coprocDataOperation(byte midTop, byte midBot, byte bot) {

	}

	private void coprocRegisterTransfer(byte midTop, byte midBot, byte bot) {

	}

	private void softwareInterrupt(byte midTop, byte midBot, byte bot) {

	}

}
