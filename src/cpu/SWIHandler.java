package cpu;

public class SWIHandler {

	private final CPU cpu;

	public SWIHandler(CPU cpu) {
		this.cpu = cpu;
	}

	public void execute(byte instr) {
		switch(instr) {
		case 0x0: softReset(); break;
		case 0x1: registerRamReset(); break;
		case 0x2: halt(); break;
		case 0x3: stop(); break;
		case 0x4: intrWait(); break;
		case 0x5: vBlankIntrWait(); break;
		case 0x6: div(); break;
		case 0x7: divArm(); break;
		case 0x8: sqrt(); break;
		case 0x9: arcTan(); break;
		case 0xA: arcTan2(); break;
		case 0xB: cpuSet(); break;
		case 0xC: cpuFastSet(); break;
		case 0xD: getBiosChecksum(); break;
		case 0xE: bgAffineset(); break;
		case 0xF: objAffineSet(); break;
		case 0x10: bitUnpack(); break;
		case 0x11: lz77UncompWram(); break;
		case 0x12: lz77UncompVram(); break;
		case 0x13: huffUncomp(); break;
		case 0x14: rlUncompWram(); break;
		case 0x15: rlUncompVram(); break;
		case 0x16: diff8bitUnfilterWram(); break;
		case 0x17: diff8bitUnfilterVram(); break;
		case 0x18: diff16bitUnfilter(); break;
		case 0x19: soundBias(); break;
		case 0x1A: soundDriverInit(); break;
		case 0x1B: soundDriverMode(); break;
		case 0x1C: soundDriverMain(); break;
		case 0x1D: soundDriverVSync(); break;
		case 0x1E: soundChannelClear(); break;
		case 0x1F: midiKey2Freq(); break;
		//Unknown sound driver functions
		case 0x20:
		case 0x21:
		case 0x22:
		case 0x23:
		case 0x24: 
			soundDriverUnknown(); break;
		case 0x25: multiBoot(); break;
		case 0x26: hardReset(); break;
		case 0x27: customHalt(); break;
		case 0x28: soundDriverVSyncOff(); break;
		case 0x29: soundDriverVSyncOn(); break;
		case 0x2A: soundGetJumpList(); break;
		//Undefined. According to GBATEK, 
		//"The BIOS SWI handler does not perform any range checks, so calling GBA SWI 2Bh-FFh will blindly jump to garbage addresses."
		default: break;
		}
	}

	private void softReset() {
		// TODO Auto-generated method stub

	}

	private void registerRamReset() {
		// TODO Auto-generated method stub

	}

	private void halt() {
		// TODO Auto-generated method stub

	}

	private void stop() {
		// TODO Auto-generated method stub

	}

	private void intrWait() {
		// TODO Auto-generated method stub

	}

	private void vBlankIntrWait() {
		// TODO Auto-generated method stub

	}

	private void div() {
		int numer = cpu.getLowReg(0);
		int denom = cpu.getLowReg(1);
		if (denom == 0) {
			//TODO Something more useful
			cpu.regDump();
			throw new RuntimeException("(EMULATED) BIOS/SWI ERROR: Division by zero");
		}
		cpu.setLowReg(0, numer / denom);
		cpu.setLowReg(1, numer % denom);
		cpu.setLowReg(3, Math.abs(numer / denom));
	}

	private void divArm() {
		int numer = cpu.getLowReg(1);
		int denom = cpu.getLowReg(0);
		if (denom == 0) {
			//TODO Something more useful
			cpu.regDump();
			throw new RuntimeException("(EMULATED) BIOS/SWI ERROR: Division by zero");
		}
		cpu.setLowReg(0, numer / denom);
		cpu.setLowReg(1, numer % denom);
		cpu.setLowReg(3, Math.abs(numer / denom));
	}

	private void sqrt() {
		//Sqrt of unsigned int
		cpu.setLowReg(0, (int) Math.sqrt(cpu.getLowReg(0) & 0xFFFFFFFFL));
	}

	private void arcTan() {
		// TODO Auto-generated method stub
		
	}

	private void arcTan2() {
		// TODO Auto-generated method stub

	}

	private void cpuSet() {
		// TODO Auto-generated method stub

	}

	private void cpuFastSet() {
		// TODO Auto-generated method stub

	}

	private void getBiosChecksum() {
		// TODO Auto-generated method stub

	}

	private void bgAffineset() {
		// TODO Auto-generated method stub

	}

	private void objAffineSet() {
		// TODO Auto-generated method stub

	}

	private void bitUnpack() {
		// TODO Auto-generated method stub

	}

	private void lz77UncompWram() {
		// TODO Auto-generated method stub

	}

	private void lz77UncompVram() {
		// TODO Auto-generated method stub

	}

	private void huffUncomp() {
		// TODO Auto-generated method stub

	}

	private void rlUncompWram() {
		// TODO Auto-generated method stub

	}

	private void rlUncompVram() {
		// TODO Auto-generated method stub

	}

	private void diff8bitUnfilterWram() {
		// TODO Auto-generated method stub

	}

	private void diff8bitUnfilterVram() {
		// TODO Auto-generated method stub

	}

	private void diff16bitUnfilter() {
		// TODO Auto-generated method stub

	}

	private void soundBias() {
		// TODO Auto-generated method stub

	}

	private void soundDriverInit() {
		// TODO Auto-generated method stub

	}

	private void soundDriverMode() {
		// TODO Auto-generated method stub

	}

	private void soundDriverMain() {
		// TODO Auto-generated method stub

	}

	private void soundDriverVSync() {
		// TODO Auto-generated method stub

	}

	private void soundChannelClear() {
		// TODO Auto-generated method stub

	}

	private void midiKey2Freq() {
		// TODO Auto-generated method stub

	}

	private void soundDriverUnknown() {
		// TODO Auto-generated method stub

	}

	private void multiBoot() {
		// TODO Auto-generated method stub

	}

	private void hardReset() {
		// TODO Auto-generated method stub

	}

	private void customHalt() {
		// TODO Auto-generated method stub

	}

	private void soundDriverVSyncOff() {
		// TODO Auto-generated method stub

	}

	private void soundDriverVSyncOn() {
		// TODO Auto-generated method stub

	}

	private void soundGetJumpList() {
		// TODO Auto-generated method stub

	}

}
