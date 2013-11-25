package cpu;

public class THUMBProcessor implements CPU.IProcessor {

	private final CPU cpu;

	public THUMBProcessor(CPU cpu) {
		this.cpu = cpu;
	}

	@Override
	public void execute(int pc) {
		/*2 Bytes stored in Little-Endian format
		  15-8, 7-0 */
		byte top = cpu.accessROM(pc+1), bot = cpu.accessROM(pc);

		byte bit15_to_11 = (byte)((top >>> 3) & 0x1F);

		//From 0x0 to 0x1F (0-31)
		switch(bit15_to_11) {
		case 0x0: lsl(top, bot); break; 
		case 0x1: lsr(top, bot); break;
		case 0x2: asr(top, bot); break;
		case 0x3: /*Add or Sub*/
			if ((top & 0x2) == 0) /*Bit 9 CLEAR*/
				add(top, bot); 
			else
				sub(top, bot);
			break;
		case 0x4: movImm(top, bot); break;
		case 0x5: cmpImm(top, bot); break;
		case 0x6: addImm(top, bot); break;
		case 0x7: subImm(top, bot); break;
		case 0x8:
			if ((top & 0x4) == 0) /*Bit 10 CLEAR*/
				aluOp(top, bot); 
			else
				hiRegOpsBranchX(top, bot);
			break;
		case 0x9: pcRelativeLoad(top, bot); break;
		case 0xA: /*Bit 11 CLEAR*/
			if ((top & 0x2) == 0)/*Bit 9 CLEAR, Store register offset*/
				if ((top & 0x4) == 0) /*Bit 10 CLEAR - B*/
					str(top, bot);
				else
					strb(top, bot);
			else
				if ((top & 0x4) == 0) /*Bit 10 CLEAR - S*/
					storeHW(top, bot);  //strh
				else
					loadSignExtendedByte(top, bot); //ldsb
			break;
		case 0xB: /*Bit 11 SET*/
			if ((top & 0x2) == 0)/*Bit 9 CLEAR, Load register offset*/
				if ((top & 0x4) == 0) /*Bit 10 CLEAR*/
					ldr(top, bot);
				else 
					ldrb(top, bot);
			else
				if ((top & 0x4) == 0) /*Bit 10 CLEAR - S*/
					loadHW(top, bot);  //ldrh
				else
					loadSignExtendedHW(top, bot); //ldsh
			break;
		case 0xC: strImm(top, bot); break;
		case 0xD: ldrImm(top, bot); break;
		case 0xE: strbImm(top, bot); break;
		case 0xF: ldrbImm(top, bot); break;
		case 0x10: storeHWImm(top, bot); break;
		case 0x11: loadHWImm(top, bot); break;
		case 0x12: spRelativeStore(top, bot); break;
		case 0x13: spRelativeLoad(top, bot); break;
		case 0x14: loadAddress(false, top, bot); break;
		case 0x15: loadAddress(true, top, bot); break;
		case 0x16:
			if ((top & 0x7) == 0) /*Bit 10-8 CLEAR*/
				addOffsetToSP(bot); 
			if ((top & 0x6) == 0x4) /*Bit 10 SET, Bit 9 CLEAR*/
				pushRegisters(top, bot); 
			//TODO Add undefined instruction trap here?
			break;
		case 0x17:
			if ((top & 0x6) == 0x4) /*Bit 10 SET, Bit 9 CLEAR*/
				popRegisters(top, bot); 
			//TODO Add undefined instruction trap here?
			break;
		case 0x18: storeMult(top, bot); break;
		case 0x19: loadMult(top, bot); break;
		case 0x1A: conditionalBranch(top, bot); break;
		case 0x1B:
			if ((top & 0xF) == 0xF)
				softwareInterrupt(pc, bot);
			else
				conditionalBranch(top, bot);
			break;
		case 0x1C: unconditionalBranch(top, bot);
		case 0x1D: System.out.println("UNDEFINED THUMB INSTRUCTION"); break; //Undefined????
		case 0x1E: longBranch(false, top, bot); break;
		case 0x1F: longBranch(true, top, bot); break;
		}
	}

	private void lsl(byte top, byte bot) {

	}

	private void lsr(byte top, byte bot){

	}

	private void asr(byte top, byte bot) {

	}

	private void add(byte top, byte bot) {

	}

	private void sub(byte top, byte bot) {

	}

	private void movImm(byte top, byte bot) {

	}

	private void cmpImm(byte top, byte bot) {

	}

	private void addImm(byte top, byte bot) {

	}

	private void subImm(byte top, byte bot) {

	}

	private void aluOp(byte top, byte bot) {

	}

	private void hiRegOpsBranchX(byte top, byte bot) {

	}

	private void pcRelativeLoad(byte top, byte bot) {

	}

	private void str(byte top, byte bot) { 

	}

	private void strb(byte top, byte bot) {

	}

	private void ldr(byte top, byte bot) {

	}

	private void ldrb(byte top, byte bot) {

	}

	private void storeHW(byte top, byte bot) {

	}

	private void loadSignExtendedByte(byte top, byte bot) {

	}

	private void loadHW(byte top, byte bot) {

	}	

	private void loadSignExtendedHW(byte top, byte bot) {

	}

	private void strImm(byte top, byte bot) {

	}

	private void strbImm(byte top, byte bot) {

	}

	private void ldrImm(byte top, byte bot) {

	}

	private void ldrbImm(byte top, byte bot) {

	}

	private void storeHWImm(byte top, byte bot) {

	}

	private void loadHWImm(byte top, byte bot) {

	}

	private void spRelativeStore(byte top, byte bot) {

	}

	private void spRelativeLoad(byte top, byte bot) {

	}

	private void loadAddress(boolean sp, byte top, byte bot) {
		//If sp is false, we use pc instead
	}

	private void addOffsetToSP(byte bot) { 

	}

	private void pushRegisters(byte top, byte bot) {

	}

	private void popRegisters(byte top, byte bot) {

	}
	
	private void storeMult(byte top, byte bot) {
		
	}
	
	private void loadMult(byte top, byte bot) {
		
	}
	
	private void conditionalBranch(byte top, byte bot) {
		
	}
	
	private void softwareInterrupt(int pc, byte val) {
		
	}
	
	private void unconditionalBranch(byte top, byte bot) {
		
	}
	
	private void longBranch(boolean offsetLow, byte top, byte bot) {
		
	}

}
